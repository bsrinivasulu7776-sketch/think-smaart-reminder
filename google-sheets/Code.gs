/**
 * Think Smaart Reminder -> Google Sheets sync endpoint
 * Target spreadsheet:
 * https://docs.google.com/spreadsheets/d/1GJ9ezyQcY4GdmxOhw7VVUiN8uf3IfuhpwQhCB4PJQQg/edit
 *
 * Apps Script Project Settings -> Script Properties -> add SYNC_KEY.
 * Deploy -> New deployment -> Web app
 * Execute as: Me
 * Who has access: Anyone
 */

const SPREADSHEET_ID = '1GJ9ezyQcY4GdmxOhw7VVUiN8uf3IfuhpwQhCB4PJQQg';
const PROFILE_FOLDER_NAME = 'Think Smaart Reminder Profile Photos';

function doGet() {
  return json_({ok:true, app:'Think Smaart Reminder', sheetId:SPREADSHEET_ID, message:'Sync endpoint ready'});
}

function doPost(e) {
  try {
    const payload = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    const expected = PropertiesService.getScriptProperties().getProperty('SYNC_KEY') || '';
    if (!expected) return json_({ok:false, error:'SYNC_KEY is not configured in Script Properties'});
    if (String(payload.syncKey || '') !== expected) return json_({ok:false, error:'Invalid sync key'});

    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const user = payload.user || {};
    const userId = clean_(user.userId || payload.deviceId || 'unknown');
    const userName = clean_(user.name || 'User');
    const email = clean_(user.email || '');
    const phone = clean_(user.phone || '');
    const role = clean_(user.role || 'User');
    const now = new Date();

    let photoUrl = '';
    if (user.photoData) photoUrl = saveProfilePhoto_(userId, userName, user.photoData);

    updateUsersIndex_(ss, userId, userName, email, phone, role, photoUrl, now);
    syncCategorySheets_(ss, userId, userName, payload, now);
    updateUserSheet_(ss, userId, userName, email, phone, role, photoUrl, payload, now);
    appendEvent_(ss, payload, userId, userName, now);

    return json_({ok:true, message:'Synced for ' + userName, userId:userId, photoUrl:photoUrl || undefined});
  } catch (err) {
    return json_({ok:false, error:String(err && err.message ? err.message : err)});
  }
}

function syncCategorySheets_(ss, userId, userName, payload, now) {
  const tasks = Array.isArray(payload.tasks) ? payload.tasks : [];
  syncTaskCategory_(ss, 'Work', userId, userName, tasks.filter(t => t.type === 'Work'), workRow_, now);
  syncTaskCategory_(ss, 'Payments', userId, userName, tasks.filter(t => t.type === 'Payment'), paymentRow_, now);
  syncTaskCategory_(ss, 'Delivery', userId, userName, tasks.filter(t => t.type === 'Delivery'), deliveryRow_, now);
  syncTaskCategory_(ss, 'Follow-up', userId, userName, tasks.filter(t => t.type === 'Follow-up'), followRow_, now);
  syncTaskCategory_(ss, 'Vendors', userId, userName, tasks.filter(t => t.type === 'Vendor'), vendorRow_, now);
  syncWater_(ss, userId, userName, payload.water || {}, now);
  syncHealth_(ss, userId, userName, payload.healthSchedule || {}, now);
}

function syncTaskCategory_(ss, sheetName, userId, userName, tasks, rowFn, now) {
  const sh = ss.getSheetByName(sheetName);
  if (!sh) return;
  removeUserRows_(sh, 2, userId);
  if (!tasks.length) return;
  const rows = tasks.map(t => rowFn(t, userId, userName, now));
  sh.getRange(sh.getLastRow() + 1, 1, rows.length, rows[0].length).setValues(rows);
}

function d_(t){return t.details||t.fields||{}}
function workRow_(t,u,n,now){const f=d_(t);return [t.id||'',u,n,f.customerName||t.customer||'',f.phone||'',f.workTitle||t.title||'',f.workType||t.workType||'',f.sizeQty||'',num_(f.amount),t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.notes||'',t.done?'Completed':'Pending',date_(t.createdAt)||now,now]}
function paymentRow_(t,u,n,now){const f=d_(t);return [t.id||'',u,n,f.customerName||t.customer||'',f.phone||'',f.paymentFor||t.title||'',num_(f.totalAmount),num_(f.paidAmount),num_(f.balanceAmount),f.paymentMethod||'',t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.notes||'',t.done?'Completed':'Pending',now]}
function deliveryRow_(t,u,n,now){const f=d_(t);return [t.id||'',u,n,f.customerName||t.customer||'',f.phone||'',f.deliveryItem||t.title||'',f.deliveryType||'',f.deliveryAddress||'',f.assignedTo||'',t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.notes||'',t.done?'Completed':'Pending',now]}
function followRow_(t,u,n,now){const f=d_(t);return [t.id||'',u,n,f.personName||t.customer||'',f.phone||'',f.followUpFor||t.title||'',f.followUpType||t.workType||'',f.lastContact||'',f.nextAction||'',t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.notes||'',t.done?'Completed':'Pending',now]}
function vendorRow_(t,u,n,now){const f=d_(t);return [t.id||'',u,n,f.vendorName||t.customer||'',f.phone||'',f.vendorType||'',f.vendorWork||t.title||'',f.sizeQty||'',num_(f.totalAmount),num_(f.paidAmount),num_(f.balanceAmount),t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.notes||'',t.done?'Completed':'Pending',now]}

function syncWater_(ss,userId,userName,water,now){
  const sh=ss.getSheetByName('Water'); if(!sh)return;
  removeUserRows_(sh,2,userId);
  const entries=Array.isArray(water.entries)?water.entries:[];
  const total=num_(water.ml), target=num_(water.target), interval=num_(water.interval);
  if(!entries.length){sh.appendRow(['water_'+userId,userId,userName,new Date(),'', '',target,total,interval,now]);return}
  const rows=entries.map((w,i)=>['water_'+userId+'_'+i,userId,userName,w.date||new Date(),w.time||w.at||'',num_(w.ml||w.amount),target,total,interval,now]);
  sh.getRange(sh.getLastRow()+1,1,rows.length,rows[0].length).setValues(rows);
}

function syncHealth_(ss,userId,userName,h,now){
  const sh=ss.getSheetByName('Health Schedule'); if(!sh)return;
  removeUserRows_(sh,2,userId);
  const lunch=h.lunch||'13:00', mins=Number(h.breakMinutes||90);
  sh.appendRow(['health_'+userId,userId,userName,h.breakfast||'08:00',lunch,mins,addMinutes_(lunch,mins),h.dinner||'20:00',h.enabled===false?'No':'Yes',now]);
}

function updateUsersIndex_(ss,userId,name,email,phone,role,newPhotoUrl,now){
  const sh=ss.getSheetByName('Users')||ss.insertSheet('Users');
  const headers=['User ID','Name','Email','Mobile','Profile Photo URL','Signup Date','Last Sync','Status'];
  ensureHeaders_(sh,headers);
  const data=sh.getLastRow()>1?sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues():[];
  let row=-1;
  for(let i=0;i<data.length;i++) if(String(data[i][0])===userId){row=i+2;break}
  if(row<0){sh.appendRow([userId,name,email,phone,newPhotoUrl||'',now,now,'Active']);}
  else {
    const oldPhoto=String(sh.getRange(row,5).getValue()||'');
    const signup=sh.getRange(row,6).getValue()||now;
    sh.getRange(row,1,1,headers.length).setValues([[userId,name,email,phone,newPhotoUrl||oldPhoto,signup,now,'Active']]);
  }
}

function updateUserSheet_(ss,userId,name,email,phone,role,photoUrl,payload,now){
  const sheetName=safeSheetName_(name+' - '+userId.slice(-8));
  const sh=ss.getSheetByName(sheetName)||ss.insertSheet(sheetName);
  sh.clearContents();
  const rows=[];
  rows.push(['THINK SMAART REMINDER - USER DATA']);
  rows.push(['User ID',userId]); rows.push(['Name',name]); rows.push(['Email',email]); rows.push(['Phone',phone]); rows.push(['Role',role]);
  rows.push(['Profile Photo URL',photoUrl||'']); rows.push(['Device ID',payload.deviceId||'']); rows.push(['Last Sync',now]); rows.push(['Sync Reason',payload.reason||'']); rows.push([]);
  rows.push(['REMINDERS / BUSINESS DATA']);
  rows.push(['ID','Category','Title','Customer/Person','Phone','Due Date','Due Time','Reminder Date','Reminder Time','Priority','Repeat','Completed','Notes','All Fields JSON']);
  (Array.isArray(payload.tasks)?payload.tasks:[]).forEach(t=>{const f=d_(t);rows.push([t.id||'',t.type||'',t.title||'',f.customerName||f.personName||f.vendorName||t.customer||'',f.phone||'',t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.repeat||'',t.done?'Yes':'No',t.notes||'',JSON.stringify(f)])});
  rows.push([]); rows.push(['WATER TRACKER']); const w=payload.water||{}; rows.push(['Daily Target (ml)',w.target||'']);rows.push(['Today Total (ml)',w.ml||'']);rows.push(['Reminder Interval (min)',w.interval||'']);rows.push(['Entry Time','Amount (ml)']);(Array.isArray(w.entries)?w.entries:[]).forEach(x=>rows.push([x.time||x.at||'',x.ml||x.amount||'']));
  rows.push([]); rows.push(['HEALTH / MEAL SCHEDULE']); const h=payload.healthSchedule||{}; rows.push(['Enabled',h.enabled===false?'No':'Yes']);rows.push(['Breakfast',h.breakfast||'08:00']);rows.push(['Lunch',h.lunch||'13:00']);rows.push(['Lunch Break Minutes',h.breakMinutes||90]);rows.push(['Back To Work',addMinutes_(h.lunch||'13:00',Number(h.breakMinutes||90))]);rows.push(['Dinner',h.dinner||'20:00']);
  rows.push([]); rows.push(['APP SETTINGS']); const st=payload.settings||{};rows.push(['Sound Mode',st.soundMode||'']);rows.push(['Vibration',st.vibrate===false?'Off':'On']);rows.push(['Volume',st.volume==null?'':st.volume]);
  const maxCols=14, norm=rows.map(r=>{const a=(Array.isArray(r)?r:[r]).slice(0,maxCols);while(a.length<maxCols)a.push('');return a});
  if(norm.length)sh.getRange(1,1,norm.length,maxCols).setValues(norm);
  sh.getRange(1,1,1,maxCols).merge().setFontWeight('bold').setFontSize(14).setHorizontalAlignment('center');
  for(let r=1;r<=sh.getLastRow();r++){const v=String(sh.getRange(r,1).getDisplayValue()||'');if(['REMINDERS / BUSINESS DATA','WATER TRACKER','HEALTH / MEAL SCHEDULE','APP SETTINGS'].includes(v)){sh.getRange(r,1,1,maxCols).merge();styleHeader_(sh.getRange(r,1,1,maxCols))}}
  sh.setFrozenRows(1); sh.autoResizeColumns(1,maxCols);
}

function appendEvent_(ss,payload,userId,userName,now){
  const sh=ss.getSheetByName('User Data'); if(!sh)return;
  const id='evt_'+Utilities.getUuid(); const summary=(payload.reason||'sync')+' • '+((payload.tasks||[]).length)+' tasks';
  sh.appendRow([id,userId,userName,payload.reason||'Sync','',payload.reason||'Sync',summary,'',Utilities.formatDate(now,'Asia/Kolkata','yyyy-MM-dd'),Utilities.formatDate(now,'Asia/Kolkata','HH:mm:ss'),now,'Think Smaart Reminder '+(payload.appVersion||'')]);
}

function saveProfilePhoto_(userId,userName,dataUrl){
  const m=String(dataUrl).match(/^data:([^;]+);base64,(.+)$/); if(!m)return '';
  const mime=m[1]||'image/jpeg', bytes=Utilities.base64Decode(m[2]);
  let folders=DriveApp.getFoldersByName(PROFILE_FOLDER_NAME); const folder=folders.hasNext()?folders.next():DriveApp.createFolder(PROFILE_FOLDER_NAME);
  const ext=mime.indexOf('png')>=0?'png':'jpg'; const name=safeFileName_(userName+'-'+userId+'.'+ext);
  const old=folder.getFilesByName(name); while(old.hasNext()) old.next().setTrashed(true);
  const file=folder.createFile(Utilities.newBlob(bytes,mime,name));
  return file.getUrl();
}

function removeUserRows_(sh,userIdCol,userId){
  if(sh.getLastRow()<2)return; const vals=sh.getRange(2,userIdCol,sh.getLastRow()-1,1).getDisplayValues();
  for(let i=vals.length-1;i>=0;i--) if(String(vals[i][0])===String(userId)) sh.deleteRow(i+2);
}
function ensureHeaders_(sh,headers){if(sh.getLastRow()===0||String(sh.getRange(1,1).getValue())!==headers[0]){sh.getRange(1,1,1,headers.length).setValues([headers]);styleHeader_(sh.getRange(1,1,1,headers.length));sh.setFrozenRows(1)}}
function addMinutes_(hhmm,mins){const p=String(hhmm||'13:00').split(':').map(Number),d=new Date(2000,0,1,p[0]||0,p[1]||0);d.setMinutes(d.getMinutes()+Number(mins||0));return Utilities.formatDate(d,'Asia/Kolkata','HH:mm')}
function date_(v){try{return v?new Date(v):''}catch(e){return ''}}
function num_(v){const n=Number(String(v==null?'':v).replace(/[^0-9.-]/g,''));return isNaN(n)?'':n}
function clean_(v){return String(v==null?'':v).trim()}
function safeSheetName_(name){let s=clean_(name).replace(/[\\\/\?\*\[\]:]/g,' ').replace(/\s+/g,' ');if(!s)s='User';return s.substring(0,90)}
function safeFileName_(name){return clean_(name).replace(/[\\/:*?"<>|]/g,'_').substring(0,160)}
function styleHeader_(range){range.setFontWeight('bold').setBackground('#0b6fd3').setFontColor('#ffffff')}
function json_(obj){return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON)}
