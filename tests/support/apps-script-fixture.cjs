const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const crypto = require('node:crypto');
class Sheet {
  constructor(name,rows=[],formulas={}) { this.name=name;this.rows=rows;this.formulas=formulas;this.writes=0;this.cols=Math.max(26,...rows.map(r=>r.length));this.maxRows=1000;this.failSet=false; }
  getLastRow(){return this.rows.length;}
  getLastColumn(){return Math.max(0,...this.rows.map(r=>r.length));}
  getMaxRows(){return this.maxRows;}
  getMaxColumns(){return this.cols;}
  insertColumnsAfter(_,n){this.cols+=n;this.writes++;}
  insertRowsAfter(_,n){this.maxRows+=n;this.writes++;}
  setFrozenRows(){return this;}
  setColumnWidth(){return this;}
  setRowHeight(){return this;}
  appendRow(row){this.getRange(this.rows.length+1,1,1,row.length).setValues([row]);}
  deleteRow(row){this.writes++;this.rows.splice(row-1,1);}
  clear(){this.writes++;this.rows=[];return this;}
  getRange(row,col,nr=1,nc=1){
    const range={
      getValues:()=>Array.from({length:nr},(_,i)=>Array.from({length:nc},(_,j)=>this.rows[row+i-1]?.[col+j-1]??'')),
      getFormulas:()=>Array.from({length:nr},(_,i)=>Array.from({length:nc},(_,j)=>this.formulas[`${row+i}:${col+j}`]||'')),
      setValues:values=>{
        if(this.failSet)throw Error('SIMULATED_WRITE_FAILURE');
        if(values.length!==nr||values.some(r=>r.length!==nc))throw Error('INVALID_RANGE_SHAPE');
        this.writes++;while(this.rows.length<row+nr-1)this.rows.push([]);
        values.forEach((r,i)=>r.forEach((v,j)=>{this.rows[row+i-1][col+j-1]=v;}));return range;
      },
      setValue:value=>{if(nr!==1||nc!==1)throw Error('Expected one cell');return range.setValues([[value]]);}
    };
    for(const name of ['merge','setHorizontalAlignment','setVerticalAlignment','setFontSize','setFontWeight','setBackground','setFontColor','setWrap'])range[name]=()=>range;
    return range;
  }
}
function fixture(){
  const sheets={
    Data:new Sheet('Data',[['날짜','항목ID','값'],['2026-10-01','overtime',3],['2026-09-30','special',8]]),
    Events:new Sheet('Events',[['ID','제목','시작일','종료일','색/반복'],['old','keep','2026-10-01','2026-10-01','2F5496|once']]),
    Holidays:new Sheet('Holidays',[['날짜','종류','이름'],['2026-10-03','holiday','기존공휴일'],['2027-01-01','holiday','다른해']]),
    '급여월별집계':new Sheet('급여월별집계',[['월','근무일수(앱 동기화 필요)','주휴 횟수(앱 동기화 필요)','주차수당(앱 동기화 필요)','잔업시간(기록 기준)','특근시간(기록 기준)','동기화 상태'],['2026-10','','','',3,0,'old']],{'2:5':'=KEEP_OT','2:6':'=KEEP_SPECIAL'})
  };
  const state={held:false,locks:0,releases:0,alerts:[],fetches:0,flushes:0};
  const lock={waitLock:()=>{if(state.held)throw Error('NESTED_LOCK');state.held=true;state.locks++;},releaseLock:()=>{if(!state.held)throw Error('LOCK_NOT_HELD');state.held=false;state.releases++;}};
  const menu={addItem:()=>menu,addSeparator:()=>menu,addToUi:()=>menu};
  const ui={alert:m=>state.alerts.push(m),createMenu:()=>menu};
  const book={getSheetByName:n=>sheets[n]||null,insertSheet:n=>sheets[n]=new Sheet(n),getSpreadsheetTimeZone:()=> 'Asia/Seoul'};
  const context=vm.createContext({Date,console,
    SpreadsheetApp:{getActiveSpreadsheet:()=>book,getUi:()=>ui,flush:()=>state.flushes++},
    LockService:{getScriptLock:()=>lock},
    PropertiesService:{getScriptProperties:()=>({getProperty:()=> 'unit-test-key'})},
    Utilities:{formatDate:d=>d.toISOString().slice(0,10),DigestAlgorithm:{SHA_256:'sha256'},computeDigest:(_,text)=>Array.from(crypto.createHash('sha256').update(text).digest())},
    ContentService:{MimeType:{JSON:'json'},createTextOutput:text=>({setMimeType:()=>JSON.parse(text)})},
    UrlFetchApp:{fetch:()=>{state.fetches++;return {getResponseCode:()=>200,getContentText:()=>JSON.stringify({response:{header:{resultCode:'00'},body:{totalCount:0,items:''}}})};}}
  });
  for(const file of ['Code.gs','Payroll.gs'])vm.runInContext(fs.readFileSync(path.join(__dirname,'../../server',file),'utf8'),context,{filename:file});
  return {sheets,context,state,book,get:parameter=>context.doGet({parameter}),post:body=>context.doPost({postData:{contents:JSON.stringify(body)}})};
}
module.exports={fixture};
