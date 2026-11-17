/**
 * 잔업·특근 캘린더 백엔드 v5 (기존 기능 보존 + 월별 급여 집계)
 * -------------------------------------------------------------
 * 시트 탭:
 *   "Data"     : A 날짜  B 항목ID  C 값   (사용자 기록)
 *   "Holidays" : A 날짜  B 종류    C 이름 (KASI에서 받아온 특일)
 *                종류: holiday(공휴일) / term(24절기) / anniv(기념일)
 *
 * 공개 소스에서는 KASI_KEY를 스크립트 속성에 저장하고,
 * 메뉴 "특일 동기화 실행"을 한 번 눌러 5년치를 받아옴.
 *
 * 통신:
 *   GET ?action=list&month=2026-08   → 사용자 기록
 *   GET ?action=holidays&year=2026   → 그 해 특일 전체
 *   GET ?action=save&date=..&records=<JSON>  → 기록 저장
 */

var SHEET_NAME = 'Data';
var HOLIDAY_SHEET = 'Holidays';
var EVENT_SHEET = 'Events';
var TZ = 'Asia/Seoul';

// ▼▼▼ 공공데이터포털 인증키 (Encoding 키를 그대로 사용) ▼▼▼
var KASI_KEY = PropertiesService.getScriptProperties().getProperty('KASI_KEY') || '';
// ▲▲▲ 위는 이미 URL 인코딩된 Encoding 키이므로 코드에서 추가 인코딩하지 않음 ▲▲▲

var KASI_BASE = 'https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService';

// ===== 공통 =====
function getDataSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(SHEET_NAME);
  if (!sh) {
    sh = ss.insertSheet(SHEET_NAME);
    sh.getRange(1, 1, 1, 3).setValues([['날짜', '항목ID', '값']]);
    sh.setFrozenRows(1);
  }
  return sh;
}

function getHolidaySheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(HOLIDAY_SHEET);
  if (!sh) {
    sh = ss.insertSheet(HOLIDAY_SHEET);
    sh.getRange(1, 1, 1, 3).setValues([['날짜', '종류', '이름']]);
    sh.setFrozenRows(1);
  }
  return sh;
}

function getEventSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(EVENT_SHEET);
  if (!sh) {
    sh = ss.insertSheet(EVENT_SHEET);
    sh.getRange(1, 1, 1, 5).setValues([['ID', '제목', '시작일', '종료일', '색/반복']]);
    sh.setFrozenRows(1);
  }
  return sh;
}

function isValidDate_(s) {
  if (typeof s !== 'string') return false;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  var p = s.split('-');
  var y = Number(p[0]), m = Number(p[1]), d = Number(p[2]);
  if (m < 1 || m > 12 || d < 1 || d > 31) return false;
  var dt = new Date(y, m - 1, d);
  return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d;
}

function toNum_(v) {
  if (v === '' || v === null || v === undefined) return 0;
  var n = Number(v);
  if (!isFinite(n) || n < 0) return 0;
  return n;
}

function normDate_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, TZ, 'yyyy-MM-dd');
  return String(v).trim();
}

function jsonOut_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// ===== 사용자 기록 =====
function listEntries_(month) {
  var sh = getDataSheet_();
  var last = sh.getLastRow();
  var byDate = {};
  if (last >= 2) {
    var values = sh.getRange(2, 1, last - 1, 3).getValues();
    for (var i = 0; i < values.length; i++) {
      var dstr = normDate_(values[i][0]);
      if (!dstr) continue;
      if (month && dstr.indexOf(month) !== 0) continue;
      var cat = String(values[i][1]);
      if (!cat) continue;
      if (!byDate[dstr]) byDate[dstr] = [];
      byDate[dstr].push({ c: cat, v: toNum_(values[i][2]) });
    }
  }
  var out = [];
  for (var d in byDate) out.push({ date: d, records: byDate[d] });
  return out;
}

function saveEntry_(date, records) {
  if (!isValidDate_(date)) return { ok: false, error: 'invalid date: ' + date };
  if (!Array.isArray(records)) return { ok: false, error: 'INVALID_RECORDS_ARRAY' };
  var rows = [];
  // Validate before obtaining a sheet or changing any existing row. [] is an explicit deletion.
  for (var j = 0; j < records.length; j++) {
    var record = records[j];
    if (!record || typeof record.c !== 'string' || !record.c.trim() ||
        (record.v !== undefined && record.v !== null && !isFinite(Number(record.v))))
      return { ok: false, error: 'INVALID_RECORD' };
    rows.push([date, record.c, toNum_(record.v)]);
  }
  var lock = LockService.getScriptLock(), locked = false;
  try {
    lock.waitLock(10000); locked = true;
    var sh = getDataSheet_(), last = sh.getLastRow();
    var oldRows = [];
    if (last >= 2) {
      var dates = sh.getRange(2, 1, last - 1, 1).getValues();
      for (var i = 0; i < dates.length; i++) {
        if (normDate_(dates[i][0]) === date) oldRows.push(i + 2);
      }
    }
    // Store replacement first: a failed append cannot erase the old records.
    if (rows.length) {
      sh.getRange(last + 1, 1, rows.length, 3).setValues(rows);
      SpreadsheetApp.flush();
    }
    for (var k = oldRows.length - 1; k >= 0; k--) sh.deleteRow(oldRows[k]);
    SpreadsheetApp.flush();
    return { ok: true, date: date, count: rows.length };
  } finally { if (locked) lock.releaseLock(); }
}

function parseRecords_(s) {
  if (typeof s !== 'string' || !s.trim()) throw Error('RECORDS_REQUIRED');
  var records;
  try { records = JSON.parse(s); } catch (_) { throw Error('INVALID_RECORDS_JSON'); }
  if (!Array.isArray(records)) throw Error('INVALID_RECORDS_ARRAY');
  return records;
}

// ===== 특일(공휴일/절기/기념일) 조회 =====
function listHolidays_(year) {
  var sh = getHolidaySheet_();
  var last = sh.getLastRow();
  var out = [];
  if (last >= 2) {
    var values = sh.getRange(2, 1, last - 1, 3).getValues();
    for (var i = 0; i < values.length; i++) {
      var dstr = normDate_(values[i][0]);
      if (!dstr) continue;
      if (year && dstr.indexOf(String(year)) !== 0) continue;
      out.push({ date: dstr, kind: String(values[i][1]), name: String(values[i][2]) });
    }
  }
  return out;
}

// ===== 일정(Events) =====
function listEvents_() {
  var sh = getEventSheet_();
  var last = sh.getLastRow();
  var out = [];
  if (last >= 2) {
    var values = sh.getRange(2, 1, last - 1, 5).getValues();
    for (var i = 0; i < values.length; i++) {
      var id = String(values[i][0]);
      if (!id) continue;
      var meta = String(values[i][4] || '').split('|');
      out.push({
        id: id,
        title: String(values[i][1] || ''),
        start: normDate_(values[i][2]),
        end: normDate_(values[i][3]),
        color: meta[0] || '2F5496',
        yearly: meta[1] === 'yearly'
      });
    }
  }
  return out;
}

function saveEvent_(ev) {
  if (!ev || !ev.id) return { ok: false, error: 'no id' };
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var sh = getEventSheet_();
    var last = sh.getLastRow();
    var targetRow = -1;
    if (last >= 2) {
      var idcol = sh.getRange(2, 1, last - 1, 1).getValues();
      for (var i = 0; i < idcol.length; i++) {
        if (String(idcol[i][0]) === String(ev.id)) { targetRow = i + 2; break; }
      }
    }
    var meta = (ev.color || '2F5496') + '|' + (ev.yearly ? 'yearly' : 'once');
    var row = [String(ev.id), String(ev.title || ''), String(ev.start || ''), String(ev.end || ev.start || ''), meta];
    if (targetRow === -1) {
      sh.appendRow(row);
    } else {
      sh.getRange(targetRow, 1, 1, 5).setValues([row]);
    }
    return { ok: true, id: ev.id };
  } finally {
    lock.releaseLock();
  }
}

function deleteEvent_(id) {
  if (!id) return { ok: false, error: 'no id' };
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var sh = getEventSheet_();
    var last = sh.getLastRow();
    if (last >= 2) {
      var idcol = sh.getRange(2, 1, last - 1, 1).getValues();
      for (var i = idcol.length - 1; i >= 0; i--) {
        if (String(idcol[i][0]) === String(id)) sh.deleteRow(i + 2);
      }
    }
    return { ok: true, id: id };
  } finally {
    lock.releaseLock();
  }
}

// ===== KASI API 호출 =====
function kasiFetch_(endpoint, year, month) {
  var url = KASI_BASE + '/' + endpoint +
    '?serviceKey=' + KASI_KEY + '&solYear=' + year +
    '&solMonth=' + (month < 10 ? '0' + month : '' + month) + '&numOfRows=100&_type=json';
  var response;
  try { response = UrlFetchApp.fetch(url, { muteHttpExceptions: true }); }
  catch (_) { throw Error('KASI_FETCH_FAILED'); }
  if (response.getResponseCode() !== 200) throw Error('KASI_HTTP_FAILED');
  var obj;
  try { obj = JSON.parse(response.getContentText()); }
  catch (_) { throw Error('KASI_INVALID_JSON'); }
  var envelope = obj && obj.response, body = envelope && envelope.body;
  if (!body || typeof body !== 'object') throw Error('KASI_INVALID_RESPONSE');
  if (envelope.header && ['00','0'].indexOf(String(envelope.header.resultCode)) < 0)
    throw Error('KASI_API_FAILED');
  var raw = body.items && body.items.item;
  var rows = raw ? (Array.isArray(raw) ? raw : [raw]) : [];
  var count = Number(body.totalCount);
  if (!isFinite(count) || count < 0 || count !== rows.length) throw Error('KASI_INCOMPLETE_RESPONSE');
  return rows.map(function(item) {
    var locdate = String(item.locdate), date = locdate.slice(0,4)+'-'+locdate.slice(4,6)+'-'+locdate.slice(6,8);
    if (!isValidDate_(date) || date.slice(0,7) !== String(year)+'-'+pad2_(month))
      throw Error('KASI_INVALID_DATE');
    return {date:date, name:String(item.dateName || '')};
  });
}

/** 한 해 특일 전체를 모아 시트에 저장 (endpoint별 kind 지정) */
function syncYear_(year) {
  var collected = [];
  var jobs = [
    { ep: 'getRestDeInfo', kind: 'holiday' },   // 공휴일(대체공휴일 포함)
    { ep: 'get24DivisionsInfo', kind: 'term' }, // 24절기
    { ep: 'getAnniversaryInfo', kind: 'anniv' } // 기념일
  ];
  for (var j = 0; j < jobs.length; j++) {
    for (var m = 1; m <= 12; m++) {
      var items = kasiFetch_(jobs[j].ep, year, m);
      for (var i = 0; i < items.length; i++) {
        collected.push([items[i].date, jobs[j].kind, items[i].name]);
      }
    }
  }
  return collected;
}

/** 특정 한 해만 동기화 (기존 그 해 데이터는 지우고 새로 씀) */
function syncOneYear_(year) {
  if (!KASI_KEY) {
    SpreadsheetApp.getUi().alert('KASI_KEY가 비어 있습니다. 기존 인증키 설정을 확인하세요.');
    return;
  }
  // Complete all API calls first. Any failure preserves the existing Holidays sheet.
  var rows = syncYear_(year);
  if (!rows.length) throw Error('KASI_EMPTY_YEAR_PRESERVED');
  var lock = LockService.getScriptLock(), locked = false;
  try {
    lock.waitLock(10000); locked = true;
    var sh = getHolidaySheet_(), last = sh.getLastRow(), oldRows = [];
    if (last >= 2) {
      var dates = sh.getRange(2,1,last-1,1).getValues();
      for (var i=0;i<dates.length;i++) {
        if (normDate_(dates[i][0]).indexOf(String(year)+'-') === 0) oldRows.push(i+2);
      }
    }
    sh.getRange(last+1,1,rows.length,3).setValues(rows);
    SpreadsheetApp.flush();
    for (var k=oldRows.length-1;k>=0;k--) sh.deleteRow(oldRows[k]);
    SpreadsheetApp.flush();
  } finally { if (locked) lock.releaseLock(); }
  SpreadsheetApp.getUi().alert(year+'년 동기화 완료: '+rows.length+'건');
}

function syncYear2026() { syncOneYear_(2026); }
function syncYear2027() { syncOneYear_(2027); }
function syncYear2028() { syncOneYear_(2028); }
function syncYear2029() { syncOneYear_(2029); }
function syncYear2030() { syncOneYear_(2030); }

/** 진단: 2026년 1월 공휴일을 실제로 호출해서 응답을 그대로 보여줌 */
function diagnose() {
  if (!KASI_KEY) {
    SpreadsheetApp.getUi().alert('KASI_KEY가 비어 있습니다.');
    return;
  }
  var url = KASI_BASE + '/getRestDeInfo' +
    '?serviceKey=' + KASI_KEY +
    '&solYear=2026&solMonth=01&numOfRows=100&_type=json';
  var res = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
  var code = res.getResponseCode();
  var text = res.getContentText();
  // 앞부분 500자만 보여줌
  var preview = text.substring(0, 500);
  SpreadsheetApp.getUi().alert('HTTP ' + code + '\n\n응답:\n' + preview);
}

/** (구버전) 5년 한 번에 - 시간 초과 위험, 참고용 */
function syncHolidays() {
  syncOneYear_(2026);
}

/** 시트 열 때 메뉴 추가 */
/** 달력보기 탭 생성/갱신: 지정 연·월을 달력 모양으로 그림 */
function buildCalendarView(year, month) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var name = '달력보기';
  var sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  sh.clear();

  // 데이터 모으기
  var records = {};  // date -> "잔업3 특근8"
  var dsh = getDataSheet_();
  var dlast = dsh.getLastRow();
  if (dlast >= 2) {
    var dv = dsh.getRange(2, 1, dlast - 1, 3).getValues();
    var byDate = {};
    for (var i = 0; i < dv.length; i++) {
      var d = normDate_(dv[i][0]);
      if (!d) continue;
      if (!byDate[d]) byDate[d] = [];
      var label = (dv[i][1] === 'overtime' ? '잔업' : dv[i][1] === 'special' ? '특근' : dv[i][1]);
      byDate[d].push(label + (dv[i][2] ? dv[i][2] : ''));
    }
    for (var k in byDate) records[k] = byDate[k].join(' ');
  }

  var holidays = {}; // date -> name
  var hsh = getHolidaySheet_();
  var hlast = hsh.getLastRow();
  if (hlast >= 2) {
    var hv = hsh.getRange(2, 1, hlast - 1, 3).getValues();
    for (var j = 0; j < hv.length; j++) {
      var hd = normDate_(hv[j][0]);
      if (hd && hv[j][1] === 'holiday' && !holidays[hd]) holidays[hd] = String(hv[j][2]);
    }
  }

  var events = {}; // date -> [titles]
  var esh = getEventSheet_();
  var elast = esh.getLastRow();
  if (elast >= 2) {
    var ev = esh.getRange(2, 1, elast - 1, 5).getValues();
    for (var e = 0; e < ev.length; e++) {
      var title = String(ev[e][1] || '');
      var start = normDate_(ev[e][2]);
      var end = normDate_(ev[e][3]) || start;
      var meta = String(ev[e][4] || '').split('|');
      var yearly = meta[1] === 'yearly';
      if (yearly) {
        if (start.length === 10) {
          var md = start.substring(5);
          var dd = year + '-' + md;
          if (!events[dd]) events[dd] = [];
          events[dd].push(title);
        }
      } else {
        // 기간 순회
        var cur = start;
        var guard = 0;
        while (cur && cur <= end && guard < 60) {
          if (!events[cur]) events[cur] = [];
          events[cur].push(title);
          cur = addOneDay_(cur);
          guard++;
        }
      }
    }
  }

  // 제목
  sh.getRange(1, 1).setValue(year + '년 ' + month + '월');
  sh.getRange(1, 1, 1, 7).merge().setHorizontalAlignment('center');
  sh.getRange(1, 1).setFontSize(16).setFontWeight('bold')
    .setBackground('#2F5496').setFontColor('#FFFFFF');

  // 요일 헤더
  var wd = ['일', '월', '화', '수', '목', '금', '토'];
  for (var c = 0; c < 7; c++) {
    var cell = sh.getRange(2, c + 1);
    cell.setValue(wd[c]).setHorizontalAlignment('center').setFontWeight('bold');
    if (c === 0) cell.setFontColor('#CC0000').setBackground('#F2DEDE');
    else if (c === 6) cell.setFontColor('#0055CC').setBackground('#DEE8F2');
    else cell.setBackground('#E8E8E8');
  }

  // 달력 본체
  var firstDay = new Date(year, month - 1, 1).getDay(); // 0=일
  var numDays = new Date(year, month, 0).getDate();
  var row = 3;
  var col = firstDay;
  for (var day = 1; day <= numDays; day++) {
    var dateStr = year + '-' + pad2_(month) + '-' + pad2_(day);
    var lines = [String(day)];
    if (holidays[dateStr]) lines.push('🔴' + holidays[dateStr]);
    if (events[dateStr]) lines.push('📌' + events[dateStr].join(','));
    if (records[dateStr]) lines.push(records[dateStr]);
    var cellR = sh.getRange(row, col + 1);
    cellR.setValue(lines.join('\n')).setVerticalAlignment('top').setWrap(true);
    // 색: 일요일/공휴일 빨강, 토요일 파랑
    if (col === 0 || holidays[dateStr]) cellR.setFontColor('#CC0000');
    else if (col === 6) cellR.setFontColor('#0055CC');
    col++;
    if (col > 6) { col = 0; row++; }
  }

  // 열 너비/행 높이
  for (var w = 1; w <= 7; w++) sh.setColumnWidth(w, 110);
  for (var r = 3; r <= row + 1; r++) sh.setRowHeight(r, 70);
  sh.setRowHeight(1, 30);
}

function pad2_(n) { return n < 10 ? '0' + n : '' + n; }
function addOneDay_(dstr) {
  var p = dstr.split('-');
  var d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
  d.setDate(d.getDate() + 1);
  return d.getFullYear() + '-' + pad2_(d.getMonth() + 1) + '-' + pad2_(d.getDate());
}

/** 메뉴: 이번 달 달력보기 생성 */
function buildCalendarThisMonth() {
  var now = new Date();
  buildCalendarView(now.getFullYear(), now.getMonth() + 1);
  SpreadsheetApp.getUi().alert('달력보기 탭을 갱신했습니다.');
}

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('잔업특근 캘린더')
    .addItem('2026년 특일 동기화', 'syncYear2026')
    .addItem('2027년 특일 동기화', 'syncYear2027')
    .addItem('2028년 특일 동기화', 'syncYear2028')
    .addItem('2029년 특일 동기화', 'syncYear2029')
    .addItem('2030년 특일 동기화', 'syncYear2030')
    .addSeparator()
    .addItem('📅 이번 달 달력보기 생성', 'buildCalendarThisMonth')
    .addSeparator()
    .addItem('🔍 진단 (문제 확인)', 'diagnose')
    .addToUi();
}

// ===== 웹 요청 =====
function doGet(e) {
  try {
    var payroll = payrollGet(e);
    if (payroll) return payroll;
    var params = (e && e.parameter) ? e.parameter : {};
    var action = params.action || 'list';
    if (action === 'save') return jsonOut_(saveEntry_(params.date, parseRecords_(params.records)));
    if (action === 'holidays') {
      var year = params.year || '';
      if (year && KASI_KEY && Number(year) >= 2020 && Number(year) <= 2100)
        ensureHolidaysYear_(Number(year));
      var holidays = listHolidays_(year);
      return jsonOut_({ok:true,count:holidays.length,holidays:holidays});
    }
    if (action === 'events') {
      var events = listEvents_();
      return jsonOut_({ok:true,count:events.length,events:events});
    }
    if (action === 'saveEvent') return jsonOut_(saveEvent_({
      id:params.id,title:params.title,start:params.start,end:params.end,color:params.color,
      yearly:params.yearly === 'true' || params.yearly === 'yearly'
    }));
    if (action === 'deleteEvent') return jsonOut_(deleteEvent_(params.id));
    var entries = listEntries_(params.month || '');
    return jsonOut_({ok:true,count:entries.length,entries:entries});
  } catch (err) { return jsonOut_({ok:false,error:String(err)}); }
}

function doPost(e) {
  try {
    var payroll = payrollPost(e);
    if (payroll) return payroll;
    var body = {};
    if (e && e.postData && e.postData.contents) body = JSON.parse(e.postData.contents);
    if (!body || !Object.prototype.hasOwnProperty.call(body,'records') ||
        !Array.isArray(body.records)) return jsonOut_({ok:false,error:'RECORDS_REQUIRED'});
    return jsonOut_(saveEntry_(body.date,body.records));
  } catch (err) { return jsonOut_({ok:false,error:String(err)}); }
}

/** Missing-year auto import: recheck under the same lock after the slow network calls. */
function ensureHolidaysYear_(year) {
  if (listHolidays_(year).length) return;
  var rows = syncYear_(year);
  if (!rows.length) throw Error('KASI_EMPTY_YEAR_PRESERVED');
  var lock = LockService.getScriptLock(), locked = false;
  try {
    lock.waitLock(10000); locked = true;
    if (listHolidays_(year).length) return;
    var sh = getHolidaySheet_();
    sh.getRange(sh.getLastRow()+1,1,rows.length,3).setValues(rows);
    SpreadsheetApp.flush();
  } finally { if (locked) lock.releaseLock(); }
}
