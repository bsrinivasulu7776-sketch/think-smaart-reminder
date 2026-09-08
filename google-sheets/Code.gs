/**
 * Think Smaart Reminder V14 -> Google Sheets + secure account backend
 *
 * IMPORTANT SECURITY:
 * - Plain passwords are NEVER stored in Google Sheets.
 * - The Users sheet stores only a salted HMAC password verifier.
 * - Forgot Password sends a short-lived 6-digit code to the registered email.
 *
 * Required Script Property:
 *   SYNC_KEY = same value used in GitHub Actions secret
 * Optional but recommended:
 *   AUTH_PEPPER = a different long random secret used only by Apps Script
 *
 * Deploy as Web app:
 *   Execute as: Me
 *   Who has access: Anyone
 */

const SPREADSHEET_ID = '1GJ9ezyQcY4GdmxOhw7VVUiN8uf3IfuhpwQhCB4PJQQg';
const PROFILE_FOLDER_NAME = 'Think Smaart Reminder Profile Photos';
const USER_HEADERS = [
  'User ID','Name','Email','Mobile','Profile Photo URL','Signup Date','Last Sync','Status',
  'Password Hash','Password Salt','Password Updated At','Role','Password Recovery'
];
const RESET_TTL_SECONDS = 600;

function doGet() {
  return json_({ok:true, app:'Think Smaart Reminder V14', sheetId:SPREADSHEET_ID, message:'Sync + secure auth endpoint ready'});
}

function doPost(e) {
  try {
    const payload = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    const keyError = validateSyncKey_(payload);
    if (keyError) return json_({ok:false, error:keyError});

    const action = String(payload.action || 'sync');
    if (action === 'authSignup') return json_(authSignup_(payload));
    if (action === 'authLogin') return json_(authLogin_(payload));
    if (action === 'authForgotRequest') return json_(authForgotRequest_(payload));
    if (action === 'authForgotReset') return json_(authForgotReset_(payload));
    if (action === 'authSnapshot') return json_(authSnapshot_(payload));
    return json_(syncPayload_(payload));
  } catch (err) {
    return json_({ok:false, error:String(err && err.message ? err.message : err)});
  }
}

function validateSyncKey_(payload) {
  const expected = PropertiesService.getScriptProperties().getProperty('SYNC_KEY') || '';
  if (!expected) return 'SYNC_KEY is not configured in Script Properties';
  if (String(payload.syncKey || '') !== expected) return 'Invalid sync key';
  return '';
}

function syncPayload_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const user = payload.user || {};
  const userId = clean_(user.userId || payload.deviceId || 'unknown');
  const userName = clean_(user.name || 'User');
  const email = clean_(user.email || '').toLowerCase();
  const phone = clean_(user.phone || '');
  const role = clean_(user.role || 'User');
  const now = new Date();

  let photoUrl = '';
  if (user.photoData) photoUrl = saveProfilePhoto_(userId, userName, user.photoData);

  updateUsersIndex_(ss, userId, userName, email, phone, role, photoUrl, now);
  syncCategorySheets_(ss, userId, userName, payload, now);
  updateUserSheet_(ss, userId, userName, email, phone, role, photoUrl, payload, now);
  appendEvent_(ss, payload, userId, userName, now);

  return {ok:true, message:'Synced for ' + userName, userId:userId, photoUrl:photoUrl || undefined};
}

// ---------------- SECURE ACCOUNT AUTH ----------------

function authSignup_(payload) {
  const name = clean_(payload.name);
  const email = clean_(payload.email).toLowerCase();
  const phone = clean_(payload.phone).replace(/\D/g, '');
  const password = String(payload.password || '');
  if (name.length < 2) return {ok:false,error:'Full name required'};
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return {ok:false,error:'Valid email required'};
  if (phone.length !== 10) return {ok:false,error:'10 digit mobile number required'};
  if (password.length < 6) return {ok:false,error:'Password must be at least 6 characters'};

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sh = ensureUsersSheet_(ss);
  if (findUser_(sh, email)) return {ok:false,error:'Ee email tho account already undhi. Login cheyyandi.'};
  if (findUser_(sh, phone)) return {ok:false,error:'Ee mobile number tho account already undhi.'};

  const now = new Date();
  const userId = 'user_' + Utilities.getUuid().replace(/-/g,'').substring(0,20);
  const salt = Utilities.getUuid();
  const hash = passwordHash_(password, salt);
  sh.appendRow([userId,name,email,phone,'',now,now,'Active',hash,salt,now,'User','Email OTP enabled']);

  appendAuthEvent_(ss,userId,name,'Signup',email,now);
  return {ok:true,message:'Account created',user:publicUser_(userId,name,email,phone,'User')};
}

function authLogin_(payload) {
  const identifier = clean_(payload.identifier).toLowerCase();
  const password = String(payload.password || '');
  if (!identifier || !password) return {ok:false,error:'Username / email and password required'};

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sh = ensureUsersSheet_(ss);
  let rec = findUser_(sh, identifier);

  // Owner bootstrap: V13 owner rows may exist without a password verifier.
  if (!rec && ['srinivasulu','thinksmaart.tpt@gmail.com'].indexOf(identifier) >= 0 && password === '1234') {
    const now = new Date(), salt = Utilities.getUuid(), hash = passwordHash_(password,salt);
    sh.appendRow(['owner','Srinivasulu','thinksmaart.tpt@gmail.com','','',now,now,'Active',hash,salt,now,'Owner','Email OTP enabled']);
    rec = findUser_(sh, 'owner');
  }
  if (!rec) return {ok:false,error:'Account dorakaledhu. Email / username check cheyyandi.'};
  if (String(rec.values[7] || '').toLowerCase() === 'disabled') return {ok:false,error:'Account disabled'};

  let storedHash = String(rec.values[8] || '');
  let salt = String(rec.values[9] || '');
  const userId = String(rec.values[0] || '');
  const name = String(rec.values[1] || 'User');
  const email = String(rec.values[2] || '');
  const phone = String(rec.values[3] || '');
  const role = String(rec.values[11] || (userId === 'owner' ? 'Owner' : 'User'));

  // Existing owner migration from pre-V14 sheet.
  if (!storedHash && userId === 'owner' && password === '1234') {
    salt = Utilities.getUuid(); storedHash = passwordHash_(password,salt);
    sh.getRange(rec.row,9,1,5).setValues([[storedHash,salt,new Date(),role,'Email OTP enabled']]);
  }
  if (!storedHash || !constantTimeEqual_(storedHash,passwordHash_(password,salt))) return {ok:false,error:'Password correct kaadu'};

  sh.getRange(rec.row,7).setValue(new Date());
  const snapshot = buildSnapshot_(ss,userId);
  appendAuthEvent_(ss,userId,name,'Login',email,new Date());
  return {ok:true,message:'Login successful',user:publicUser_(userId,name,email,phone,role),snapshot:snapshot};
}

function authForgotRequest_(payload) {
  const email = clean_(payload.email).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return {ok:false,error:'Valid registered email required'};
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sh = ensureUsersSheet_(ss);
  const rec = findUser_(sh,email);

  // Do not reveal account existence to an unknown caller.
  if (!rec) return {ok:true,message:'If the email is registered, a reset code was sent.'};
  const code = String(Math.floor(100000 + Math.random() * 900000));
  CacheService.getScriptCache().put('pwdreset:' + email, code, RESET_TTL_SECONDS);
  const name = String(rec.values[1] || 'User');
  MailApp.sendEmail({
    to: email,
    subject: 'Think Smaart Reminder - Password Reset Code',
    body: 'Hello ' + name + ',\n\nYour Think Smaart Reminder password reset code is: ' + code +
      '\n\nThis code is valid for 10 minutes. If you did not request this, ignore this email.\n\nThink Smaart'
  });
  appendAuthEvent_(ss,String(rec.values[0]||''),name,'Forgot Password Code',email,new Date());
  return {ok:true,message:'Reset code sent to registered email'};
}

function authForgotReset_(payload) {
  const email = clean_(payload.email).toLowerCase();
  const code = clean_(payload.code).replace(/\D/g,'');
  const newPassword = String(payload.newPassword || '');
  if (code.length !== 6) return {ok:false,error:'6-digit reset code required'};
  if (newPassword.length < 6) return {ok:false,error:'New password must be at least 6 characters'};
  const cache = CacheService.getScriptCache();
  const expectedCode = cache.get('pwdreset:' + email) || '';
  if (!expectedCode || !constantTimeEqual_(expectedCode,code)) return {ok:false,error:'Reset code invalid or expired'};

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sh = ensureUsersSheet_(ss);
  const rec = findUser_(sh,email);
  if (!rec) return {ok:false,error:'Account not found'};
  const salt = Utilities.getUuid(), hash = passwordHash_(newPassword,salt), now = new Date();
  const role = String(rec.values[11] || (String(rec.values[0]) === 'owner' ? 'Owner' : 'User'));
  sh.getRange(rec.row,9,1,5).setValues([[hash,salt,now,role,'Email OTP enabled']]);
  cache.remove('pwdreset:' + email);
  appendAuthEvent_(ss,String(rec.values[0]||''),String(rec.values[1]||'User'),'Password Reset',email,now);
  return {ok:true,message:'Password reset successful',user:publicUser_(String(rec.values[0]||''),String(rec.values[1]||'User'),email,String(rec.values[3]||''),role)};
}

function authSnapshot_(payload) {
  const userId = clean_(payload.userId);
  if (!userId) return {ok:false,error:'User ID required'};
  return {ok:true,snapshot:buildSnapshot_(SpreadsheetApp.openById(SPREADSHEET_ID),userId)};
}

function passwordHash_(password,salt) {
  const props = PropertiesService.getScriptProperties();
  const pepper = props.getProperty('AUTH_PEPPER') || props.getProperty('SYNC_KEY') || 'ThinkSmaart';
  const bytes = Utilities.computeHmacSha256Signature(String(password) + '|' + String(salt), pepper, Utilities.Charset.UTF_8);
  return Utilities.base64Encode(bytes);
}

function constantTimeEqual_(a,b) {
  a = String(a || ''); b = String(b || '');
  if (a.length !== b.length) return false;
  let x = 0; for (let i=0;i<a.length;i++) x |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return x === 0;
}

function ensureUsersSheet_(ss) {
  const sh = ss.getSheetByName('Users') || ss.insertSheet('Users');
  sh.getRange(1,1,1,USER_HEADERS.length).setValues([USER_HEADERS]);
  styleHeader_(sh.getRange(1,1,1,USER_HEADERS.length)); sh.setFrozenRows(1);
  return sh;
}

function findUser_(sh,identifier) {
  identifier = clean_(identifier).toLowerCase();
  if (!identifier || sh.getLastRow() < 2) return null;
  const vals = sh.getRange(2,1,sh.getLastRow()-1,USER_HEADERS.length).getValues();
  for (let i=0;i<vals.length;i++) {
    const id=String(vals[i][0]||'').toLowerCase(), name=String(vals[i][1]||'').toLowerCase(), email=String(vals[i][2]||'').toLowerCase(), phone=String(vals[i][3]||'').replace(/\D/g,'');
    if (identifier===id || identifier===name || identifier===email || identifier===phone) return {row:i+2,values:vals[i]};
  }
  return null;
}

function publicUser_(id,name,email,phone,role) {
  return {id:String(id||''),name:String(name||''),email:String(email||''),phone:String(phone||''),role:String(role||'User')};
}

function appendAuthEvent_(ss,userId,userName,action,email,now) {
  const sh=ss.getSheetByName('User Data'); if(!sh)return;
  sh.appendRow(['evt_'+Utilities.getUuid(),userId,userName,'Account','',action,email,'',Utilities.formatDate(now,'Asia/Kolkata','yyyy-MM-dd'),Utilities.formatDate(now,'Asia/Kolkata','HH:mm:ss'),now,'Think Smaart Reminder V14']);
}

// ---------------- SNAPSHOT / RESTORE ----------------

function buildSnapshot_(ss,userId) {
  return {
    tasks:[].concat(snapshotTasks_(ss,'Work',userId,'Work'),snapshotTasks_(ss,'Payments',userId,'Payment'),snapshotTasks_(ss,'Delivery',userId,'Delivery'),snapshotTasks_(ss,'Follow-up',userId,'Follow-up'),snapshotTasks_(ss,'Vendors',userId,'Vendor')),
    water:snapshotWater_(ss,userId),
    healthSchedule:snapshotHealth_(ss,userId)
  };
}

function rowsAsObjects_(ss,sheetName,userId) {
  const sh=ss.getSheetByName(sheetName); if(!sh||sh.getLastRow()<2)return [];
  const lastCol=sh.getLastColumn(), headers=sh.getRange(1,1,1,lastCol).getDisplayValues()[0];
  const vals=sh.getRange(2,1,sh.getLastRow()-1,lastCol).getValues();
  return vals.filter(r=>String(r[1]||'')===String(userId)).map(r=>{const o={};headers.forEach((h,i)=>o[h]=r[i]);return o});
}

function snapshotTasks_(ss,sheetName,userId,type) {
  return rowsAsObjects_(ss,sheetName,userId).map(o=>{
    const base={id:String(o['Record ID']||Date.now()),type:type,date:dateText_(o[type==='Delivery'?'Delivery Date':type==='Follow-up'?'Follow-up Date':'Due Date']),time:timeText_(o[type==='Follow-up'?'Time':'Due Time']),reminderDate:dateText_(o['Reminder Date'])||dateText_(o[type==='Delivery'?'Delivery Date':type==='Follow-up'?'Follow-up Date':'Due Date']),reminderTime:timeText_(o['Reminder Time'])||timeText_(o[type==='Follow-up'?'Time':'Due Time']),priority:String(o['Priority']||'High'),notes:String(o['Notes']||''),done:String(o['Status']||'').toLowerCase()==='completed',repeat:'Until Completed'};
    let d={};
    if(type==='Work')d={customerName:o['Customer']||'',phone:o['Phone']||'',workTitle:o['Work Title']||'',workType:o['Work Type']||'',sizeQty:o['Size / Qty']||'',amount:o['Amount']||''};
    if(type==='Payment')d={customerName:o['Customer']||'',phone:o['Phone']||'',paymentFor:o['Payment For']||'',totalAmount:o['Total']||'',paidAmount:o['Paid']||'',balanceAmount:o['Balance']||'',paymentMethod:o['Payment Method']||''};
    if(type==='Delivery')d={customerName:o['Customer']||'',phone:o['Phone']||'',deliveryItem:o['Work / Item']||'',deliveryType:o['Delivery Type']||'',deliveryAddress:o['Address']||'',assignedTo:o['Assigned To']||''};
    if(type==='Follow-up')d={personName:o['Customer / Person']||'',phone:o['Phone']||'',followUpFor:o['Follow-up For']||'',followUpType:o['Type']||'',lastContact:o['Last Discussion']||'',nextAction:o['Next Action']||''};
    if(type==='Vendor')d={vendorName:o['Vendor Name']||'',phone:o['Phone']||'',vendorType:o['Vendor Type']||'',vendorWork:o['Work / Material']||'',sizeQty:o['Size / Qty']||'',totalAmount:o['Total']||'',paidAmount:o['Paid']||'',balanceAmount:o['Balance']||''};
    base.details=d;base.customer=d.customerName||d.personName||d.vendorName||'';base.title=d.workTitle||d.paymentFor||d.deliveryItem||d.followUpFor||d.vendorWork||type+' Reminder';base.workType=d.workType||d.paymentMethod||d.deliveryType||d.followUpType||d.vendorType||type;return base;
  });
}

function snapshotWater_(ss,userId) {
  const rows=rowsAsObjects_(ss,'Water',userId);if(!rows.length)return null;
  const entries=rows.filter(o=>o['Amount (ml)']!==''&&o['Amount (ml)']!=null).map(o=>({date:dateText_(o['Date']),time:timeText_(o['Time']),ml:Number(o['Amount (ml)']||0)}));
  const last=rows[rows.length-1];return {date:dateText_(last['Date']),ml:Number(last['Total Today (ml)']||0),target:Number(last['Daily Target (ml)']||3000),interval:Number(last['Reminder Interval (min)']||60),entries:entries,lastReminder:0};
}

function snapshotHealth_(ss,userId) {
  const rows=rowsAsObjects_(ss,'Health Schedule',userId);if(!rows.length)return null;const o=rows[rows.length-1];
  return {enabled:String(o['Enabled']||'Yes').toLowerCase()!=='no',breakfast:timeText_(o['Breakfast Time'])||'08:00',lunch:timeText_(o['Lunch Time'])||'13:00',dinner:timeText_(o['Dinner Time'])||'20:00',breakMinutes:Number(o['Lunch Break (Minutes)']||90)};
}

function dateText_(v){if(!v)return '';if(Object.prototype.toString.call(v)==='[object Date]'&&!isNaN(v))return Utilities.formatDate(v,'Asia/Kolkata','yyyy-MM-dd');return String(v).substring(0,10)}
function timeText_(v){if(v==null||v==='')return '';if(Object.prototype.toString.call(v)==='[object Date]'&&!isNaN(v))return Utilities.formatDate(v,'Asia/Kolkata','HH:mm');const s=String(v);const m=s.match(/(\d{1,2}):(\d{2})/);return m?('0'+m[1]).slice(-2)+':'+m[2]:s}

// ---------------- BUSINESS / REMINDER SYNC ----------------

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
  const sh = ss.getSheetByName(sheetName); if (!sh) return;
  removeUserRows_(sh, 2, userId); if (!tasks.length) return;
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
  const sh=ss.getSheetByName('Water'); if(!sh)return;removeUserRows_(sh,2,userId);
  const entries=Array.isArray(water.entries)?water.entries:[],total=num_(water.ml),target=num_(water.target),interval=num_(water.interval);
  if(!entries.length){sh.appendRow(['water_'+userId,userId,userName,new Date(),'','',target,total,interval,now]);return}
  const rows=entries.map((w,i)=>['water_'+userId+'_'+i,userId,userName,w.date||new Date(),w.time||w.at||'',num_(w.ml||w.amount),target,total,interval,now]);
  sh.getRange(sh.getLastRow()+1,1,rows.length,rows[0].length).setValues(rows);
}

function syncHealth_(ss,userId,userName,h,now){
  const sh=ss.getSheetByName('Health Schedule'); if(!sh)return;removeUserRows_(sh,2,userId);
  const lunch=h.lunch||'13:00', mins=Number(h.breakMinutes||90);
  sh.appendRow(['health_'+userId,userId,userName,h.breakfast||'08:00',lunch,mins,addMinutes_(lunch,mins),h.dinner||'20:00',h.enabled===false?'No':'Yes',now]);
}

function updateUsersIndex_(ss,userId,name,email,phone,role,newPhotoUrl,now){
  const sh=ensureUsersSheet_(ss), rec=findUser_(sh,userId) || (email?findUser_(sh,email):null);
  if(!rec){sh.appendRow([userId,name,email,phone,newPhotoUrl||'',now,now,'Active','','','',role||'User','Email OTP enabled']);return}
  const old=rec.values, oldPhoto=String(old[4]||''), signup=old[5]||now;
  sh.getRange(rec.row,1,1,USER_HEADERS.length).setValues([[
    userId,name,email,phone,newPhotoUrl||oldPhoto,signup,now,old[7]||'Active',old[8]||'',old[9]||'',old[10]||'',role||old[11]||'User',old[12]||'Email OTP enabled'
  ]]);
}

function updateUserSheet_(ss,userId,name,email,phone,role,photoUrl,payload,now){
  const sheetName=safeSheetName_(name+' - '+userId.slice(-8)),sh=ss.getSheetByName(sheetName)||ss.insertSheet(sheetName);sh.clearContents();
  const rows=[];rows.push(['THINK SMAART REMINDER - USER DATA']);rows.push(['User ID',userId]);rows.push(['Name',name]);rows.push(['Email',email]);rows.push(['Phone',phone]);rows.push(['Role',role]);rows.push(['Password','Protected - use Forgot Password']);rows.push(['Profile Photo URL',photoUrl||'']);rows.push(['Device ID',payload.deviceId||'']);rows.push(['Last Sync',now]);rows.push(['Sync Reason',payload.reason||'']);rows.push([]);
  rows.push(['REMINDERS / BUSINESS DATA']);rows.push(['ID','Category','Title','Customer/Person','Phone','Due Date','Due Time','Reminder Date','Reminder Time','Priority','Repeat','Completed','Notes','All Fields JSON']);
  (Array.isArray(payload.tasks)?payload.tasks:[]).forEach(t=>{const f=d_(t);rows.push([t.id||'',t.type||'',t.title||'',f.customerName||f.personName||f.vendorName||t.customer||'',f.phone||'',t.date||'',t.time||'',t.reminderDate||t.date||'',t.reminderTime||t.time||'',t.priority||'',t.repeat||'',t.done?'Yes':'No',t.notes||'',JSON.stringify(f)])});
  rows.push([]);rows.push(['WATER TRACKER']);const w=payload.water||{};rows.push(['Daily Target (ml)',w.target||'']);rows.push(['Today Total (ml)',w.ml||'']);rows.push(['Reminder Interval (min)',w.interval||'']);rows.push(['Entry Time','Amount (ml)']);(Array.isArray(w.entries)?w.entries:[]).forEach(x=>rows.push([x.time||x.at||'',x.ml||x.amount||'']));
  rows.push([]);rows.push(['HEALTH / MEAL SCHEDULE']);const h=payload.healthSchedule||{};rows.push(['Enabled',h.enabled===false?'No':'Yes']);rows.push(['Breakfast',h.breakfast||'08:00']);rows.push(['Lunch',h.lunch||'13:00']);rows.push(['Lunch Break Minutes',h.breakMinutes||90]);rows.push(['Back To Work',addMinutes_(h.lunch||'13:00',Number(h.breakMinutes||90))]);rows.push(['Dinner',h.dinner||'20:00']);
  rows.push([]);rows.push(['APP SETTINGS']);const st=payload.settings||{};rows.push(['Sound Mode',st.soundMode||'']);rows.push(['Vibration',st.vibrate===false?'Off':'On']);rows.push(['Volume',st.volume==null?'':st.volume]);
  const maxCols=14,norm=rows.map(r=>{const a=(Array.isArray(r)?r:[r]).slice(0,maxCols);while(a.length<maxCols)a.push('');return a});if(norm.length)sh.getRange(1,1,norm.length,maxCols).setValues(norm);
  sh.getRange(1,1,1,maxCols).merge().setFontWeight('bold').setFontSize(14).setHorizontalAlignment('center');
  for(let r=1;r<=sh.getLastRow();r++){const v=String(sh.getRange(r,1).getDisplayValue()||'');if(['REMINDERS / BUSINESS DATA','WATER TRACKER','HEALTH / MEAL SCHEDULE','APP SETTINGS'].includes(v)){sh.getRange(r,1,1,maxCols).merge();styleHeader_(sh.getRange(r,1,1,maxCols))}}
  sh.setFrozenRows(1);sh.autoResizeColumns(1,maxCols);
}

function appendEvent_(ss,payload,userId,userName,now){const sh=ss.getSheetByName('User Data');if(!sh)return;const id='evt_'+Utilities.getUuid(),summary=(payload.reason||'sync')+' • '+((payload.tasks||[]).length)+' tasks';sh.appendRow([id,userId,userName,payload.reason||'Sync','',payload.reason||'Sync',summary,'',Utilities.formatDate(now,'Asia/Kolkata','yyyy-MM-dd'),Utilities.formatDate(now,'Asia/Kolkata','HH:mm:ss'),now,'Think Smaart Reminder '+(payload.appVersion||'')])}
function saveProfilePhoto_(userId,userName,dataUrl){const m=String(dataUrl).match(/^data:([^;]+);base64,(.+)$/);if(!m)return '';const mime=m[1]||'image/jpeg',bytes=Utilities.base64Decode(m[2]);let folders=DriveApp.getFoldersByName(PROFILE_FOLDER_NAME);const folder=folders.hasNext()?folders.next():DriveApp.createFolder(PROFILE_FOLDER_NAME),ext=mime.indexOf('png')>=0?'png':'jpg',name=safeFileName_(userName+'-'+userId+'.'+ext),old=folder.getFilesByName(name);while(old.hasNext())old.next().setTrashed(true);return folder.createFile(Utilities.newBlob(bytes,mime,name)).getUrl()}
function removeUserRows_(sh,userIdCol,userId){if(sh.getLastRow()<2)return;const vals=sh.getRange(2,userIdCol,sh.getLastRow()-1,1).getDisplayValues();for(let i=vals.length-1;i>=0;i--)if(String(vals[i][0])===String(userId))sh.deleteRow(i+2)}
function ensureHeaders_(sh,headers){if(sh.getLastRow()===0||String(sh.getRange(1,1).getValue())!==headers[0]){sh.getRange(1,1,1,headers.length).setValues([headers]);styleHeader_(sh.getRange(1,1,1,headers.length));sh.setFrozenRows(1)}}
function addMinutes_(hhmm,mins){const p=String(hhmm||'13:00').split(':').map(Number),d=new Date(2000,0,1,p[0]||0,p[1]||0);d.setMinutes(d.getMinutes()+Number(mins||0));return Utilities.formatDate(d,'Asia/Kolkata','HH:mm')}
function date_(v){try{return v?new Date(v):''}catch(e){return ''}}
function num_(v){const n=Number(String(v==null?'':v).replace(/[^0-9.-]/g,''));return isNaN(n)?'':n}
function clean_(v){return String(v==null?'':v).trim()}
function safeSheetName_(name){let s=clean_(name).replace(/[\\\/\?\*\[\]:]/g,' ').replace(/\s+/g,' ');if(!s)s='User';return s.substring(0,90)}
function safeFileName_(name){return clean_(name).replace(/[\\/:*?"<>|]/g,'_').substring(0,160)}
function styleHeader_(range){range.setFontWeight('bold').setBackground('#0b6fd3').setFontColor('#ffffff')}
function json_(obj){return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON)}
