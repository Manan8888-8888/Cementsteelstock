Shyam Co. Cement & Steel Stock Manager

Android Studio project. Package: com.manan.stockmanager

Current iteration includes:
- Offline SQLite stock and booking records
- Rounded Shyam Co. interface styling
- Cement movement entry and stock balances
- Steel company bookings, physical stock receipts, customer bookings and deliveries
- Weighted-average physical steel cost and delivery gross-profit estimate
- Report date range filters for transaction history
- A basic steel movement chart
- Manual database export/import with required saved backup email and new-device restore guidance
- Scheduled local safety backup (not cloud upload)

Important limitations:
- This source has not yet been successfully compiled or tested on a phone in this environment.
- Automatic Google Drive upload is not configured; the email field is a reminder and Drive export/import requires the user to choose the same Google account in Android's file picker.
- Report period filters affect transaction lists; the current stock snapshot and chart are lifetime/current values.
- Full transaction edit/delete with historical recalculation and all advanced reporting requirements still need implementation and testing.
- Keep a separate backup of important live data before testing restore.

Build using Android Studio with Java 17 and Android Gradle Plugin 8.6.1.


Google Drive backup configuration: read BACKUP_FEATURE_NOTES.txt before building/testing. Google Drive OAuth must be configured with the package name and signing certificate SHA-1.
