/**
 * PriceTracker MY - Google Sheets + Drive backend.
 * Setup: Extensions > Apps Script > paste this file > Project Settings > Script properties > add TOKEN = (any long secret)
 * Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone > copy the /exec URL into the app (Settings).
 * Bind the script to the spreadsheet you want to use (open it from that sheet's Extensions menu).
 */
const SHEETS = {
  Receipts: ['receipt_id','store_name','receipt_number','purchase_date','subtotal','tax','discount','rounding','total_paid','payment_method','notes','image_drive_url','created_at','updated_at'],
  Receipt_Items: ['item_id','receipt_id','product_name','product_key','quantity','unit','unit_price','discount','line_total','category_id'],
  Price_History: ['price_id','receipt_id','product_key','product_name','store_name','purchase_date','unit_price'],
  Expenses: ['expense_id','transaction_date','store_name','receipt_id','expense_type','category_id','amount','payment_method','notes','created_at'],
  Budgets: ['budget_id','period_type','period_start','period_end','category_id','budget_amount','created_at','updated_at'],
  Expense_Categories: ['category_id','category_name','monthly_budget','color','is_active'],
  Shopping_Lists: ['list_id','list_name','created_at','status'],
  Shopping_List_Items: ['item_id','list_id','product_name','quantity','estimated_price','actual_price','is_purchased','linked_receipt_id'],
  App_Settings: ['setting_name','setting_value']
};
const FOLDER_NAME = 'PriceTracker MY Receipts';

function setup() { Object.keys(SHEETS).forEach(sheet_); }

function doGet(e) {
  return json_(auth_(e && e.parameter && e.parameter.token) ? { ok: true, pong: true } : { ok: false, error: 'bad token' });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const req = JSON.parse(e.postData.contents);
    if (!auth_(req.token)) return json_({ ok: false, error: 'bad token' });
    if (req.action === 'uploadImage') return json_(uploadImage_(req));
    if (req.action === 'batch') { applyOps_(req.ops || []); return json_({ ok: true }); }
    return json_({ ok: false, error: 'unknown action' });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function auth_(t) {
  const real = PropertiesService.getScriptProperties().getProperty('TOKEN');
  return !!real && t === real;
}
function json_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

function sheet_(name) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName(name);
  if (!sh) {
    sh = ss.insertSheet(name);
    sh.getRange(1, 1, 1, SHEETS[name].length).setValues([SHEETS[name]]).setFontWeight('bold');
    sh.setFrozenRows(1);
  }
  return sh;
}

function rowArr_(name, row) { return SHEETS[name].map(function (h) { const v = row[h]; return v === undefined || v === null ? '' : v; }); }

function applyOps_(ops) {
  ops.forEach(function (op) {
    if (!SHEETS[op.sheet]) throw new Error('unknown sheet ' + op.sheet);
    const sh = sheet_(op.sheet), cols = SHEETS[op.sheet];
    if (op.op === 'upsert') {
      const keyIdx = cols.indexOf(op.key), arr = rowArr_(op.sheet, op.row), key = String(op.row[op.key]);
      const last = sh.getLastRow();
      let target = -1;
      if (last > 1) {
        const vals = sh.getRange(2, keyIdx + 1, last - 1, 1).getValues();
        for (let i = 0; i < vals.length; i++) if (String(vals[i][0]) === key) { target = i + 2; break; }
      }
      if (target < 0) target = last + 1;
      sh.getRange(target, 1, 1, cols.length).setValues([arr]);
    } else if (op.op === 'deleteWhere' || op.op === 'replaceWhere') {
      deleteWhere_(sh, cols.indexOf(op.col), String(op.value));
      if (op.op === 'replaceWhere' && op.rows && op.rows.length) {
        const data = op.rows.map(function (r) { return rowArr_(op.sheet, r); });
        sh.getRange(sh.getLastRow() + 1, 1, data.length, cols.length).setValues(data);
      }
    }
  });
}

function deleteWhere_(sh, colIdx, value) {
  const last = sh.getLastRow();
  if (last < 2) return;
  const vals = sh.getRange(2, colIdx + 1, last - 1, 1).getValues();
  for (let i = vals.length - 1; i >= 0; i--) if (String(vals[i][0]) === value) sh.deleteRow(i + 2);
}

function folder_() {
  const props = PropertiesService.getScriptProperties();
  const id = props.getProperty('FOLDER_ID');
  if (id) { try { return DriveApp.getFolderById(id); } catch (e) {} }
  const it = DriveApp.getFoldersByName(FOLDER_NAME);
  const f = it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
  props.setProperty('FOLDER_ID', f.getId());
  return f;
}

function uploadImage_(req) {
  const folder = folder_();
  const existing = folder.getFilesByName(req.fileName);
  if (existing.hasNext()) { const f = existing.next(); return { ok: true, fileId: f.getId(), url: f.getUrl() }; } // idempotent on retry
  const file = folder.createFile(Utilities.newBlob(Utilities.base64Decode(req.data), req.mime || 'image/jpeg', req.fileName));
  return { ok: true, fileId: file.getId(), url: file.getUrl() };
}
