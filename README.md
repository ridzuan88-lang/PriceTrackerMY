# PriceTracker MY

Android grocery price tracker + expense/budget manager (Kotlin, Jetpack Compose, Room, ML Kit OCR, WorkManager) with a Google Sheets + Drive backend and a GitHub Actions APK build.

## 1. Backend (5 minutes)
1. Create a Google Sheet. Extensions > Apps Script. Paste `apps-script/Code.gs`.
2. Project Settings > Script properties > add `TOKEN` = a long random secret.
3. Run `setup` once (authorise Sheets + Drive). It creates all worksheets.
4. Deploy > New deployment > Web app > Execute as **Me**, access **Anyone** > copy the `/exec` URL.
5. In the app: Settings > paste URL + token > Test.

## 2. Build the APK (GitHub Actions)
1. Create a GitHub repo and push this folder to `main`.
2. Actions tab > "Build APK" > when finished, download artifact `PriceTrackerMY-debug-apk`, unzip, install `app-debug.apk` on your phone.

## What gets saved on "Confirm"
One DB transaction writes: receipt, every item (Receipt_Items), exactly one expense (`EXP-<receipt_id>`), price history rows. All share `receipt_id`. A queued sync job then uploads the image to Drive and upserts rows in Sheets. Offline = job stays queued; WorkManager retries with backoff when online. Re-editing a receipt replaces its rows by id, so nothing duplicates.

## Notes
- OCR: ML Kit Latin text recognition, bundled model, fully on-device. The auto-crop scanner (Google Play services document scanner) downloads its small module once; "Take photo"/"Gallery" use built-in crop + enhance and work with no Google module.
- Receipt parsing is heuristic (names on left, price on right, qty x price lines, discounts, GST/SST, rounding). Low-confidence fields are highlighted orange; you always review before saving.
- Package/app id: `com.chewanninja.pricetrackermy` (change in `app/build.gradle.kts`).
