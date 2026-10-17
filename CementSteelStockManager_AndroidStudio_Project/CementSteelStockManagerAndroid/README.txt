Cement & Steel Stock Manager — Android Studio project

This is native Android source code (Java), not an HTML preview.
Features:
- Cement stock for OPC, PPC, SUPER, WEATHER PLUS; opening stock, receipts, sales, closing balance, recent movements.
- Steel company bookings with fixed booking rates; dispatches reduce pending company booking and add physical inventory at the booking rate.
- Steel customer bookings only against available physical stock; customer deliveries reduce physical inventory and pending customer booking.
- Physical stock weighted-average cost, expected gross profit on customer booking, reports.
- Offline local SQLite storage; no payment tracking.

Build an APK:
1. Install Android Studio on a computer.
2. Open the folder CementSteelStockManager (or extract this ZIP and open that folder).
3. Allow Gradle sync. Use JDK 17 if prompted.
4. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Android Studio will show the APK output path.

Important:
- This project has not been compiled or tested on a physical phone in this environment.
- The app stores data locally on the device. Clearing app storage or uninstalling removes local data.
- Quantities are entered in decimal tons; 0.001 ton = 1 kg. Stock summaries show both tons and kilograms.
