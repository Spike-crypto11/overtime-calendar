/** Add-on only. Keep the existing doGet/doPost and every legacy action intact. */
var PAYROLL_SHEET = '급여월별집계';
var PAYROLL_LEGACY_HEADERS = ['월','근무일수(앱 동기화 필요)','주휴 횟수(앱 동기화 필요)',
  '주차수당(앱 동기화 필요)','잔업시간(기록 기준)','특근시간(기록 기준)','동기화 상태'];
var PAYROLL_EXTRA_HEADERS = ['기본근로시간(확인규칙)','출근반차기록일수','반차기록일수',
  '예상세전급여','소득세','지방소득세','국민연금','건강보험','장기요양보험','고용보험',
  '보험정산','기타공제','예상실수령액','집계상세JSON','적용규칙JSON','항목정의JSON',
  '집계시각','원본지문','집계버전'];
var PAYROLL_DEDUCTIONS = ['incomeTax','localIncomeTax','nationalPension','healthInsurance',
  'longTermCare','employmentInsurance','insuranceAdjustment','other'];

function payrollJson_(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
// Call at the beginning of the EXISTING doGet: var p = payrollGet(e); if (p) return p;
function payrollGet(e) {
  if (e && e.parameter && e.parameter.action === 'payrollCapabilities')
    return payrollJson_({ok:true, payrollProtocol:1});
  return null;
}
// Call at the beginning of existing doPost, or add doPost(e) { return payrollPost(e); } if absent.
function payrollPost(e) {
  if (!e || !e.postData || !e.postData.contents) return null;
  var body;
  try { body = JSON.parse(e.postData.contents); } catch (_) { return null; }
  if (body.action !== 'payrollSync') return null;
  var lock = LockService.getScriptLock();
  var locked = false;
  try {
    if (e.postData.contents.length > 300000) throw Error('PAYLOAD_TOO_LARGE');
    lock.waitLock(20000); locked = true;
    var book = SpreadsheetApp.getActiveSpreadsheet();
    if (!book) throw Error('BOUND_SPREADSHEET_REQUIRED');
    // No live deployment is supplied here: this runs only after merging into the current bound script.
    var result = payrollApply_(book, body);
    SpreadsheetApp.flush();
    return payrollJson_(result);
  } catch (error) {
    return payrollJson_({ok:false, payrollProtocol:1, error:String(error.message || error)});
  } finally { if (locked) lock.releaseLock(); }
}
function payrollNumber_(value, nullable, negative) {
  if (value === null && nullable) return null;
  if ((typeof value !== 'string' && typeof value !== 'number') || String(value).trim() === '')
    throw Error('INVALID_NUMBER');
  var n = Number(value);
  if (!isFinite(n) || Math.abs(n) > 1e12 || (!negative && n < 0)) throw Error('INVALID_NUMBER');
  return n;
}
function payrollCanonical_(entries, month) {
  if (!Array.isArray(entries)) throw Error('INVALID_SOURCE');
  var out = [];
  entries.forEach(function(e) {
    if (typeof e.date !== 'string' || !new RegExp('^'+month+'-\\d{2}$').test(e.date) || !Array.isArray(e.records))
      throw Error('INVALID_SOURCE');
    var day = Number(e.date.slice(8)), last = new Date(Number(month.slice(0,4)), Number(month.slice(5)), 0).getDate();
    if (day < 1 || day > last) throw Error('INVALID_DATE');
    e.records.forEach(function(r) {
      if (typeof r.c !== 'string' || !r.c || r.c.length > 120) throw Error('INVALID_CATEGORY');
      out.push([e.date, r.c, payrollNumber_(r.v, false, false)]);
    });
  });
  return JSON.stringify(out.sort(function(a,b) {
    return a[0].localeCompare(b[0]) || a[1].localeCompare(b[1]) || a[2]-b[2];
  }));
}
function payrollSource_(book, month) {
  var data = book.getSheetByName('Data');
  if (!data) throw Error('DATA_SHEET_MISSING');
  var header = data.getRange(1,1,1,3).getValues()[0];
  if (JSON.stringify(header) !== JSON.stringify(['날짜','항목ID','값'])) throw Error('DATA_HEADERS_CHANGED');
  if (data.getLastRow() < 2) return [];
  var grouped = {};
  data.getRange(2,1,data.getLastRow()-1,3).getValues().forEach(function(row) {
    var date = row[0] instanceof Date ? Utilities.formatDate(row[0], book.getSpreadsheetTimeZone(), 'yyyy-MM-dd') : String(row[0]);
    if (date.indexOf(month+'-') !== 0) return;
    if (!grouped[date]) grouped[date] = [];
    grouped[date].push({c:String(row[1]), v:payrollNumber_(row[2],false,false)});
  });
  return Object.keys(grouped).map(function(date) { return {date:date, records:grouped[date]}; });
}
function payrollValidate_(body) {
  var s = body.summary, r = body.rules;
  if (!s || !r || !r.roles || !Array.isArray(body.categories) || s.month !== body.month) throw Error('INVALID_SUMMARY');
  var allowed = ['IGNORE','ATTENDANCE','HALF_DAY','OVERTIME','SPECIAL','WEEKLY_PAID'];
  Object.keys(r.roles).forEach(function(id) { if (allowed.indexOf(r.roles[id]) < 0) throw Error('INVALID_ROLE'); });
  if (typeof r.companyDaysUseRecordedAttendance !== 'boolean' || typeof r.weeklyMarkersConfirmed !== 'boolean' ||
      typeof r.evidence !== 'string') throw Error('INVALID_RULES');
  var canonical = JSON.parse(payrollCanonical_(body.source, body.month)), days = {}, ot = 0, special = 0;
  canonical.forEach(function(row) {
    var role = Object.prototype.hasOwnProperty.call(r.roles,row[1]) ? r.roles[row[1]] : 'IGNORE';
    if (!days[row[0]]) days[row[0]] = {};
    days[row[0]][role] = true;
    if (role === 'OVERTIME') ot += row[2];
    if (role === 'SPECIAL') special += row[2];
  });
  var full = 0, half = 0, weeks = 0;
  Object.keys(days).forEach(function(d) {
    if (days[d].HALF_DAY) half++; else if (days[d].ATTENDANCE) full++;
    if (days[d].WEEKLY_PAID) weeks++;
  });
  var confirmed = r.evidence.trim().length > 0;
  var daily = payrollNumber_(r.hoursPerDay,true,false), halfHours = payrollNumber_(r.hoursPerHalfDay,true,false);
  var perWeek = payrollNumber_(r.allowancePerWeek,true,false);
  var count = confirmed && r.weeklyMarkersConfirmed ? weeks : null;
  var weeklyPay = count !== null && perWeek !== null ? count * perWeek : null;
  var basic = confirmed && daily !== null && (!half || halfHours !== null) ? full*daily+half*(halfHours || 0) : null;
  function equal(key, expected) {
    var actual = payrollNumber_(s[key],true,key==='net');
    if ((actual === null) !== (expected === null) || (actual !== null && Math.abs(actual-expected) > 0.000001))
      throw Error('SUMMARY_MISMATCH_'+key);
  }
  equal('recordedDays',full+half); equal('halfDays',half);
  equal('companyDays',confirmed && r.companyDaysUseRecordedAttendance ? full+half : null);
  equal('basicHours',basic); equal('weeklyCount',count); equal('weeklyAllowance',weeklyPay);
  equal('overtimeHours',ot); equal('specialHours',special);
  var earnings = r.earnings || {}, deductions = r.deductions || {};
  var required = ['base','overtime','special','position','other'];
  var complete = required.every(function(k) { return earnings[k] !== null && earnings[k] !== undefined; });
  var earned = 0;
  Object.keys(earnings).forEach(function(k) {
    var n = payrollNumber_(earnings[k],true,false); if (n === null) complete = false; else earned += n;
  });
  var gross = confirmed && complete && weeklyPay !== null ? earned+weeklyPay : null;
  equal('gross',gross);
  var deductionKeys = Array.from(new Set(PAYROLL_DEDUCTIONS.concat(Object.keys(deductions))));
  var deductionComplete = true, deducted = 0;
  if (!s.deductions) throw Error('DEDUCTIONS_MISSING');
  deductionKeys.forEach(function(k) {
    var expected = deductions[k] === undefined ? null : payrollNumber_(deductions[k],true,false);
    var actual = payrollNumber_(s.deductions[k],true,false);
    if (expected !== actual) throw Error('DEDUCTION_MISMATCH');
    if (expected === null) deductionComplete = false; else deducted += expected;
  });
  equal('net',gross !== null && deductionComplete ? gross-deducted : null);
  return s;
}
function payrollApply_(book, body) {
  if (body.payrollProtocol !== 1 || typeof body.month !== 'string' || !/^\d{4}-(0[1-9]|1[0-2])$/.test(body.month))
    throw Error('INVALID_PROTOCOL_OR_MONTH');
  var canonical = payrollCanonical_(body.source,body.month);
  if (canonical !== payrollCanonical_(payrollSource_(book,body.month),body.month)) throw Error('SOURCE_CHANGED_RETRY');
  var s = payrollValidate_(body);
  var sheet = book.getSheetByName(PAYROLL_SHEET);
  if (!sheet) throw Error('SUMMARY_SHEET_MISSING');
  var width = Math.max(sheet.getLastColumn(),7);
  var headers = sheet.getRange(1,1,1,width).getValues()[0];
  if (JSON.stringify(headers.slice(0,7)) !== JSON.stringify(PAYROLL_LEGACY_HEADERS)) throw Error('SUMMARY_HEADERS_CHANGED');
  var rows = sheet.getLastRow() > 1 ? sheet.getRange(2,1,sheet.getLastRow()-1,1).getValues() : [];
  var matches = [];
  rows.forEach(function(row,i) { if (String(row[0]) === body.month) matches.push(i+2); });
  if (matches.length > 1) throw Error('DUPLICATE_MONTH');
  var row = matches.length ? matches[0] : Math.max(2,sheet.getLastRow()+1);
  var extra = [s.basicHours,s.recordedDays,s.halfDays,s.gross].concat(
    PAYROLL_DEDUCTIONS.map(function(k) { return s.deductions[k]; }),
    [s.net,JSON.stringify(s),JSON.stringify(body.rules),JSON.stringify(body.categories),
      new Date().toISOString(),Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,canonical).map(function(b) {
        return ('0'+((b+256)%256).toString(16)).slice(-2);
      }).join(''),'payroll-v1']);
  var updates = [[1,body.month],[2,s.companyDays],[3,s.weeklyCount],[4,s.weeklyAllowance],
    [5,s.overtimeHours],[6,s.specialHours],[7,'기록 집계 동기화됨 · 미확정은 빈값']];
  var newHeaders = [];
  PAYROLL_EXTRA_HEADERS.forEach(function(name,i) {
    if (headers.filter(function(h) { return h === name; }).length > 1) throw Error('DUPLICATE_HEADER');
    var col = headers.indexOf(name)+1;
    if (!col) { headers.push(name); col = headers.length; newHeaders.push([col,name]); }
    updates.push([col,extra[i]]);
  });
  // Validate EVERYTHING before the first write. Keep existing overtime/special formulas.
  var existingFormulas = row <= sheet.getMaxRows() ? sheet.getRange(row,1,1,Math.min(width,sheet.getMaxColumns())).getFormulas()[0] : [];
  updates = updates.filter(function(u) {
    if (existingFormulas[u[0]-1]) {
      if (u[0] === 5 || u[0] === 6) return false;
      throw Error('PROTECTED_FORMULA');
    }
    return true;
  });
  updates.forEach(function(u) {
    if (u[1] === null) u[1] = '';
    else if ((u[0] >= 2 && u[0] <= 6) || (PAYROLL_EXTRA_HEADERS.indexOf(headers[u[0]-1]) >= 0 &&
      PAYROLL_EXTRA_HEADERS.indexOf(headers[u[0]-1]) <= 12)) u[1] = payrollNumber_(u[1],false,true);
    if (typeof u[1] === 'string' && u[1].length > 45000) throw Error('CELL_TOO_LARGE');
  });
  if (headers.length > sheet.getMaxColumns()) sheet.insertColumnsAfter(sheet.getMaxColumns(),headers.length-sheet.getMaxColumns());
  if (row > sheet.getMaxRows()) sheet.insertRowsAfter(sheet.getMaxRows(),row-sheet.getMaxRows());
  newHeaders.forEach(function(h) { sheet.getRange(1,h[0]).setValue(h[1]); });
  updates.filter(function(u) { return u[0] !== 7; }).forEach(function(u) { sheet.getRange(row,u[0]).setValue(u[1]); });
  // Publish the status only after the other cells have been written.
  sheet.getRange(row,7).setValue(updates.filter(function(u) { return u[0] === 7; })[0][1]);
  return {ok:true,payrollProtocol:1,month:body.month};
}
