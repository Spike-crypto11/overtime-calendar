const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {fixture}=require('./support/apps-script-fixture.cjs');
const snapshot=f=>JSON.stringify(Object.fromEntries(Object.entries(f.sheets).map(([k,v])=>[k,v.rows])));
test('legacy month list preserves its response and all sheets',()=>{
 const f=fixture(),before=snapshot(f);const r=f.get({action:'list',month:'2026-10'});
 assert.equal(r.ok,true);assert.equal(r.count,1);assert.equal(r.entries[0].records[0].v,3);assert.equal(snapshot(f),before);
});
test('legacy GET save replaces only its date with one script lock',()=>{
 const f=fixture();const untouched=JSON.stringify([f.sheets.Events.rows,f.sheets.Holidays.rows]);
 const r=f.get({action:'save',date:'2026-10-01',records:JSON.stringify([{c:'attendance',v:0},{c:'half',v:4},{c:'overtime',v:1.5}])});
 assert.equal(r.ok,true);assert.equal(r.count,3);assert.equal(f.state.locks,1);assert.equal(f.state.releases,1);
 assert.equal(f.get({action:'list',month:'2026-09'}).entries[0].records[0].v,8);
 assert.equal(JSON.stringify([f.sheets.Events.rows,f.sheets.Holidays.rows]),untouched);
});
test('legacy POST save works and explicit empty records removes only requested date',()=>{
 const f=fixture();assert.equal(f.post({date:'2026-10-01',records:[{c:'special',v:8}]}).ok,true);
 assert.equal(f.post({date:'2026-10-01',records:[]}).ok,true);
 assert.equal(f.get({action:'list',month:'2026-10'}).count,0);
 assert.equal(f.get({action:'list',month:'2026-09'}).count,1);
});
test('malformed JSON, wrong shape and missing records cannot erase work records',()=>{
 const f=fixture(),before=snapshot(f);
 for(const records of ['[broken','{}','null',''])assert.equal(f.get({action:'save',date:'2026-10-01',records}).ok,false);
 assert.equal(f.post({date:'2026-10-01'}).ok,false);
 assert.equal(f.context.doPost({postData:{contents:'{broken'}}).ok,false);
 assert.equal(snapshot(f),before);assert.equal(f.state.locks,0);
});
test('invalid date, category and infinite value cannot erase records',()=>{
 const f=fixture(),before=snapshot(f);
 for(const [date,records] of [['2026-02-30',[{c:'overtime',v:3}]],['2026-10-01',[{v:3}]],['2026-10-01',[{c:'overtime',v:'Infinity'}]]])assert.equal(f.post({date,records}).ok,false);
 assert.equal(snapshot(f),before);
});
test('failed replacement write keeps existing records and releases lock',()=>{
 const f=fixture(),before=snapshot(f);f.sheets.Data.failSet=true;
 assert.equal(f.post({date:'2026-10-01',records:[{c:'overtime',v:2}]}).ok,false);
 assert.equal(snapshot(f),before);assert.equal(f.state.held,false);
});
test('events list, update, yearly metadata, insert and delete retain compatibility',()=>{
 const f=fixture();const data=JSON.stringify(f.sheets.Data.rows);
 assert.equal(f.get({action:'events'}).events[0].title,'keep');
 assert.equal(f.get({action:'saveEvent',id:'old',title:'updated',start:'2026-10-02',end:'2026-10-02',color:'FFFFFF',yearly:'true'}).ok,true);
 assert.equal(f.get({action:'events'}).events[0].yearly,true);
 assert.equal(f.get({action:'saveEvent',id:'new',title:'new',start:'2026-10-03'}).ok,true);
 assert.equal(f.get({action:'deleteEvent',id:'new'}).ok,true);
 assert.equal(f.get({action:'events'}).count,1);assert.equal(JSON.stringify(f.sheets.Data.rows),data);
});
test('existing holiday year is read without API requests or sheet changes',()=>{
 const f=fixture(),before=snapshot(f);const r=f.get({action:'holidays',year:'2026'});
 assert.equal(r.ok,true);assert.equal(r.count,1);assert.equal(f.state.fetches,0);assert.equal(snapshot(f),before);
});
test('KASI HTTP, JSON, API and partial responses fail closed',()=>{
 const f=fixture(),before=snapshot(f);
 for(const response of [
  {status:503,text:'unavailable'}, {status:200,text:'<xml/>'},
  {status:200,text:JSON.stringify({response:{header:{resultCode:'99'},body:{totalCount:0}}})},
  {status:200,text:JSON.stringify({response:{header:{resultCode:'00'},body:{totalCount:1,items:''}}})}
 ]){
  f.context.UrlFetchApp.fetch=()=>({getResponseCode:()=>response.status,getContentText:()=>response.text});
  assert.throws(()=>f.context.syncOneYear_(2026),/KASI_/);assert.equal(snapshot(f),before);
 }
});
test('empty holiday year never wipes existing Holidays',()=>{
 const f=fixture(),before=snapshot(f);f.context.syncYear_=()=>[];
 assert.throws(()=>f.context.syncOneYear_(2026),/KASI_EMPTY_YEAR_PRESERVED/);assert.equal(snapshot(f),before);
});
test('manual holiday refresh writes before deleting and retains other years',()=>{
 const f=fixture();const other=JSON.stringify([f.sheets.Data.rows,f.sheets.Events.rows]);
 f.context.syncYear_=()=>[['2026-01-01','holiday','새해']];f.context.syncOneYear_(2026);
 assert.equal(f.get({action:'holidays',year:'2026'}).holidays[0].name,'새해');
 assert.equal(f.get({action:'holidays',year:'2027'}).count,1);
 assert.equal(JSON.stringify([f.sheets.Data.rows,f.sheets.Events.rows]),other);assert.equal(f.state.locks,1);
});
test('failed holiday append cannot erase the existing year',()=>{
 const f=fixture(),before=snapshot(f);f.context.syncYear_=()=>[['2026-01-01','holiday','new']];f.sheets.Holidays.failSet=true;
 assert.throws(()=>f.context.syncOneYear_(2026),/SIMULATED_WRITE_FAILURE/);assert.equal(snapshot(f),before);assert.equal(f.state.held,false);
});
test('automatic missing-year import rechecks under lock and avoids duplicate imports',()=>{
 const f=fixture();f.context.syncYear_=()=>[['2028-01-01','holiday','새해']];
 assert.equal(f.get({action:'holidays',year:'2028'}).count,1);assert.equal(f.get({action:'holidays',year:'2028'}).count,1);
 assert.equal(f.state.locks,1);assert.equal(f.get({action:'holidays',year:'2027'}).count,1);
});
test('calendar view and menu remain available and preserve original sheets',()=>{
 const f=fixture(),before=snapshot(f);f.context.onOpen();f.context.buildCalendarView(2026,10);
 for(const name of ['Data','Events','Holidays'])assert.equal(f.sheets[name].writes,0);
 assert.equal(f.sheets['달력보기'].rows[0][0],'2026년 10월');assert.ok(before.length);
});
test('capability route does not fall through to the legacy data action',()=>{
 const f=fixture(),before=snapshot(f);const r=f.get({action:'payrollCapabilities'});
 assert.equal(r.payrollProtocol,1);assert.equal(r.ok,true);assert.equal(snapshot(f),before);assert.equal(f.state.locks,0);
});
test('payroll POST integrates with live legacy shape without nested locking or raw-sheet writes',()=>{
 const f=fixture();const before=JSON.stringify([f.sheets.Data.rows,f.sheets.Events.rows,f.sheets.Holidays.rows]);
 const deductions=Object.fromEntries(Array.from(f.context.PAYROLL_DEDUCTIONS,k=>[k,null]));
 const body={action:'payrollSync',payrollProtocol:1,month:'2026-10',source:f.get({action:'list',month:'2026-10'}).entries,
  categories:[{id:'overtime',name:'잔업'}],rules:{roles:{overtime:'OVERTIME',special:'SPECIAL'},hoursPerDay:null,hoursPerHalfDay:null,
  companyDaysUseRecordedAttendance:false,weeklyMarkersConfirmed:false,allowancePerWeek:null,evidence:'',earnings:{},deductions:{}},
  summary:{month:'2026-10',recordedDays:0,halfDays:0,companyDays:null,basicHours:null,weeklyCount:null,weeklyAllowance:null,
  overtimeHours:'3',specialHours:'0',gross:null,net:null,deductions,warnings:[]}};
 const r=f.post(body);assert.equal(r.ok,true);assert.equal(r.payrollProtocol,1);assert.equal(f.state.locks,1);
 assert.equal(JSON.stringify([f.sheets.Data.rows,f.sheets.Events.rows,f.sheets.Holidays.rows]),before);
 assert.equal(f.sheets['급여월별집계'].formulas['2:5'],'=KEEP_OT');assert.equal(f.sheets['급여월별집계'].rows[1][1],'');
});
test('invalid payroll POST cannot fall through into legacy save',()=>{
 const f=fixture(),before=snapshot(f);
 assert.equal(f.post({action:'payrollSync',payrollProtocol:1,month:'bad',date:'2026-10-01',records:[]}).ok,false);
 assert.equal(snapshot(f),before);assert.equal(f.state.held,false);
});
test('published source has a property lookup and no hard-coded service key',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../server/Code.gs'),'utf8');
 assert.match(source,/getProperty\('KASI_KEY'\)/);assert.doesNotMatch(source,/var KASI_KEY = '[^']+'/);
});
