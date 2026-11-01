const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const crypto = require('node:crypto');
class Sheet {
  constructor(name, rows, formulas={}) { this.name=name; this.rows=rows; this.formulas=formulas; this.writes=0; this.cols=rows[0].length; this.maxRows=100; }
  getLastColumn() { return Math.max(...this.rows.map(r=>r.length)); }
  getLastRow() { return this.rows.length; }
  getMaxColumns() { return this.cols; }
  getMaxRows() { return this.maxRows; }
  insertColumnsAfter(_,n) { this.cols+=n; this.writes++; }
  insertRowsAfter(_,n) { this.maxRows+=n; this.writes++; }
  getRange(row,col,nr=1,nc=1) {
    return {
      getValues:()=>Array.from({length:nr},(_,i)=>Array.from({length:nc},(_,j)=>this.rows[row+i-1]?.[col+j-1] ?? '')),
      getFormulas:()=>Array.from({length:nr},(_,i)=>Array.from({length:nc},(_,j)=>this.formulas[`${row+i}:${col+j}`] || '')),
      setValue:value=> { this.writes++; while(this.rows.length<row)this.rows.push([]); this.rows[row-1][col-1]=value; }
    };
  }
}
function fixture() {
  const context = vm.createContext({console, Utilities:{
    DigestAlgorithm:{SHA_256:'sha256'},
    computeDigest:(_,text)=>Array.from(crypto.createHash('sha256').update(text).digest()),
    formatDate:d=>d.toISOString().slice(0,10)
  }});
  vm.runInContext(fs.readFileSync(require('node:path').join(__dirname,'../server/Payroll.gs'),'utf8'),context);
  const source=[{date:'2026-10-01',records:[{c:'work',v:0},{c:'overtime',v:3}]},
    {date:'2026-10-02',records:[{c:'work',v:0},{c:'half',v:0},{c:'overtime',v:1.5}]},
    {date:'2026-10-04',records:[{c:'week',v:0}]}, {date:'2026-10-10',records:[{c:'special',v:8}]}];
  const data=new Sheet('Data',[['날짜','항목ID','값'],...source.flatMap(e=>e.records.map(r=>[e.date,r.c,r.v]))]);
  const summary=new Sheet('급여월별집계',[Array.from(context.PAYROLL_LEGACY_HEADERS),
    ['2026-09',99,9,999,24,8,'old'],['2026-10','','','',4.5,8,'old']],{'3:5':'=KEEP_OT','3:6':'=KEEP_SPECIAL'});
  const events=new Sheet('Events',[['ID','제목'],['event','keep']]);
  const holidays=new Sheet('Holidays',[['날짜','종류','이름'],['2026-10-03','holiday','keep']]);
  const sheets={Data:data,'급여월별집계':summary,Events:events,Holidays:holidays};
  const book={getSheetByName:n=>sheets[n],getSpreadsheetTimeZone:()=> 'Asia/Seoul'};
  const rules={roles:{work:'ATTENDANCE',half:'HALF_DAY',overtime:'OVERTIME',special:'SPECIAL',week:'WEEKLY_PAID'},
    hoursPerDay:'8',hoursPerHalfDay:'4',companyDaysUseRecordedAttendance:true,weeklyMarkersConfirmed:true,
    allowancePerWeek:'100',evidence:'test fixture only',earnings:{base:'10',overtime:'10',special:'10',position:'10',other:'10'},
    deductions:Object.fromEntries(Array.from(context.PAYROLL_DEDUCTIONS,k=>[k,'1']))};
  const s={month:'2026-10',recordedDays:2,halfDays:1,companyDays:2,basicHours:'12',weeklyCount:1,weeklyAllowance:'100',
    overtimeHours:'4.5',specialHours:'8',gross:'150',deductions:{...rules.deductions},net:'142',warnings:[]};
  const body={action:'payrollSync',payrollProtocol:1,month:'2026-10',source,rules,categories:[],summary:s};
  return {context,source,data,summary,events,holidays,book,body};
}
function unchanged(f) { for(const sheet of [f.data,f.events,f.holidays])assert.equal(sheet.writes,0); }

test('upsert keeps raw sheets, adjacent month, and E/F formulas unchanged',()=> {
  const f=fixture(), before=JSON.stringify(f.summary.rows[1]);
  assert.equal(f.context.payrollApply_(f.book,f.body).ok,true);
  assert.equal(JSON.stringify(f.summary.rows[1]),before);
  assert.equal(f.summary.formulas['3:5'],'=KEEP_OT'); assert.equal(f.summary.rows[2][4],4.5);
  assert.equal(f.summary.rows[2][1],2); assert.equal(f.summary.rows[2][7],12);
  const cols=f.summary.cols; f.context.payrollApply_(f.book,f.body);
  assert.equal(f.summary.getLastRow(),3);assert.equal(f.summary.cols,cols);unchanged(f);
});
test('unknown is blank; zero remains numeric zero',()=> {
  const f=fixture(); f.body.rules.evidence='';
  for(const k of ['companyDays','basicHours','weeklyCount','weeklyAllowance','gross','net'])f.body.summary[k]=null;
  f.context.payrollApply_(f.book,f.body);
  assert.equal(f.summary.rows[2][1],'');assert.equal(f.summary.rows[2][7],'');
  assert.equal(f.summary.rows[2][8],2);unchanged(f);
});
test('source changed between read and post prevents ALL writes',()=> {
  const f=fixture(); f.data.rows.push(['2026-10-03','overtime',3]);
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/SOURCE_CHANGED/);assert.equal(f.summary.writes,0);unchanged(f);
});
test('tampered summary prevents ALL writes',()=> {
  const f=fixture(); f.body.summary.basicHours='16';
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/SUMMARY_MISMATCH/);assert.equal(f.summary.writes,0);
});
test('duplicate month prevents writes',()=> {
  const f=fixture();f.summary.rows.push(['2026-10']);
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/DUPLICATE_MONTH/);assert.equal(f.summary.writes,0);
});
test('unexpected headers prevent writes',()=> {
  const f=fixture();f.summary.rows[0][1]='custom';
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/SUMMARY_HEADERS_CHANGED/);assert.equal(f.summary.writes,0);
});
test('unknown custom columns are preserved',()=> {
  const f=fixture();f.summary.rows[0].push('user_notes');f.summary.rows[2].push('KEEP');f.summary.cols++;
  f.context.payrollApply_(f.book,f.body); assert.equal(f.summary.rows[2][7],'KEEP');
  assert.equal(f.summary.rows[2][8],12);unchanged(f);
});
test('a protected formula outside E/F prevents all changes',()=> {
  const f=fixture();f.summary.formulas['3:2']='=KEEP';
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/PROTECTED_FORMULA/);assert.equal(f.summary.writes,0);
});
test('missing extra deduction blocks net, never treats unknown as zero',()=> {
  const f=fixture(); f.body.rules.deductions.unionFee=null;f.body.summary.deductions.unionFee=null;f.body.summary.net=null;
  f.context.payrollApply_(f.book,f.body);
  assert.equal(f.summary.rows[2][f.summary.rows[0].indexOf('예상실수령액')],'');unchanged(f);
});
test('invalid month or NaN cannot produce a sheet write',()=> {
  const f=fixture(); f.body.month='2026-13';
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/INVALID_PROTOCOL_OR_MONTH/);
  f.body.month='2026-10';f.body.source[0].records[0].v='NaN';
  assert.throws(()=>f.context.payrollApply_(f.book,f.body),/INVALID_NUMBER/);assert.equal(f.summary.writes,0);
});
test('new month is appended without deleting old months',()=> {
  const f=fixture();f.summary.rows.splice(2);f.summary.formulas={};
  f.context.payrollApply_(f.book,f.body);assert.equal(f.summary.rows[2][0],'2026-10');
  assert.equal(f.summary.rows[1][0],'2026-09');unchanged(f);
});
test('unrelated GET actions are left to the original server',()=> {
  const f=fixture();assert.equal(f.context.payrollGet({parameter:{action:'list'}}),null);
  f.context.ContentService={MimeType:{JSON:'json'},createTextOutput:text=>({setMimeType:()=>JSON.parse(text)})};
  assert.equal(f.context.payrollGet({parameter:{action:'payrollCapabilities'}}).payrollProtocol,1);
  unchanged(f);assert.equal(f.summary.writes,0);
});
test('unrelated or malformed POST actions are left to the original server',()=> {
  const f=fixture();assert.equal(f.context.payrollPost({postData:{contents:'not JSON'}}),null);
  assert.equal(f.context.payrollPost({postData:{contents:JSON.stringify({action:'legacy'})}}),null);
  assert.equal(f.summary.writes,0);unchanged(f);
});
test('POST returns explicit protocol acknowledgement and releases script lock',()=> {
  const f=fixture();let locked=0,released=0,flushed=0;
  f.context.LockService={getScriptLock:()=>({waitLock:()=>locked++,releaseLock:()=>released++})};
  f.context.SpreadsheetApp={getActiveSpreadsheet:()=>f.book,flush:()=>flushed++};
  f.context.ContentService={MimeType:{JSON:'json'},createTextOutput:text=>({setMimeType:()=>JSON.parse(text)})};
  const result=f.context.payrollPost({postData:{contents:JSON.stringify(f.body)}});
  assert.equal(result.ok,true);assert.equal(result.payrollProtocol,1);assert.equal(result.month,'2026-10');
  assert.equal(locked,1);assert.equal(released,1);assert.equal(flushed,1);unchanged(f);
});
test('POST failure releases lock and cannot falsely acknowledge a stale source',()=> {
  const f=fixture();let released=0;
  f.context.LockService={getScriptLock:()=>({waitLock:()=>{},releaseLock:()=>released++})};
  f.context.SpreadsheetApp={getActiveSpreadsheet:()=>f.book,flush:()=>assert.fail('must not flush')};
  f.context.ContentService={MimeType:{JSON:'json'},createTextOutput:text=>({setMimeType:()=>JSON.parse(text)})};
  f.body.source=[];
  const result=f.context.payrollPost({postData:{contents:JSON.stringify(f.body)}});
  assert.equal(result.ok,false);assert.match(result.error,/SOURCE_CHANGED/);assert.equal(released,1);
  assert.equal(f.summary.writes,0);unchanged(f);
});
