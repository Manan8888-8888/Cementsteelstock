package com.manan.stockmanager;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import java.io.*;
import java.util.Collections;

/** Google Drive app-file backup. Requires Drive API enabled and OAuth configured for this app. */
public final class DriveBackupManager {
    private static final String BACKUP_NAME = "ShyamCo_Backup.db";
    private DriveBackupManager() {}

    private static Drive service(Context context, String email) throws Exception {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccountName(email);
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Shyam Co. Stock Manager").build();
    }

    public static void upload(Context context, String email) throws Exception {
        File source = context.getDatabasePath("stock_manager.db");
        if (!source.exists()) throw new IOException("The app database does not exist yet.");
        SQLiteDatabase sql = SQLiteDatabase.openDatabase(source.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        try { sql.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close(); } finally { sql.close(); }
        Drive drive = service(context, email);
        FileList found = drive.files().list().setQ("name='" + BACKUP_NAME + "' and trashed=false")
                .setSpaces("drive").setFields("files(id,name,modifiedTime)").setPageSize(100).execute();
        com.google.api.services.drive.model.File existing = (found.getFiles() == null || found.getFiles().isEmpty()) ? null : found.getFiles().get(0);
        com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File(); metadata.setName(BACKUP_NAME);
        FileContent media = new FileContent("application/x-sqlite3", source);
        if (existing == null) drive.files().create(metadata, media).setFields("id,name,modifiedTime").execute();
        else drive.files().update(existing.getId(), null, media).setFields("id,name,modifiedTime").execute();
    }

    public static void download(Context context, String email) throws Exception {
        Drive drive = service(context, email);
        FileList found = drive.files().list()
                .setQ("name='" + BACKUP_NAME + "' and trashed=false")
                .setSpaces("drive").setOrderBy("modifiedTime desc")
                .setFields("files(id,name,modifiedTime,size)").setPageSize(100).execute();

        if (found.getFiles() == null || found.getFiles().isEmpty())
            throw new FileNotFoundException("No ShyamCo_Backup.db was found in this Google Drive account.");

        com.google.api.services.drive.model.File remote = found.getFiles().get(0);
        java.io.File downloaded = new java.io.File(context.getCacheDir(), "drive-restore-download.tmp");
        try {
            try (OutputStream out = new FileOutputStream(downloaded)) {
                drive.files().get(remote.getId()).executeMediaAndDownloadTo(out);
                out.flush();
            }
            if (!downloaded.isFile() || downloaded.length() == 0)
                throw new IOException("The downloaded backup is empty.");

            validateDatabase(downloaded);

            java.io.File destination = context.getDatabasePath("stock_manager.db");
            java.io.File parent = destination.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs()))
                throw new IOException("Cannot access the database directory.");

            // Prepare and validate a replacement in the same directory as the database.
            java.io.File replacement = new java.io.File(parent, "stock_manager.restore.tmp");
            java.io.File previous = new java.io.File(parent, "stock_manager.previous.tmp");
            if (replacement.exists() && !replacement.delete())
                throw new IOException("Cannot clear the temporary restore file.");
            copy(downloaded, replacement);
            validateDatabase(replacement);

            // Keep a separate safety copy before touching the current database.
            java.io.File safety = new java.io.File(context.getFilesDir(), "pre_drive_restore_backup.db");
            if (destination.exists()) {
                copy(destination, safety);
                validateDatabase(safety);
            }

            java.io.File wal = new java.io.File(destination.getAbsolutePath() + "-wal");
            java.io.File shm = new java.io.File(destination.getAbsolutePath() + "-shm");
            if (wal.exists() && !wal.delete())
                throw new IOException("Could not clear the old database WAL before restore.");
            if (shm.exists() && !shm.delete())
                throw new IOException("Could not clear the old database SHM before restore.");

            if (previous.exists() && !previous.delete())
                throw new IOException("Cannot clear the previous temporary database.");
            boolean movedOld = false;
            if (destination.exists()) {
                if (!destination.renameTo(previous))
                    throw new IOException("Could not safely move the current database aside.");
                movedOld = true;
            }

            if (!replacement.renameTo(destination)) {
                if (movedOld) previous.renameTo(destination);
                throw new IOException("Could not install the restored database; the previous database was preserved where possible.");
            }
            validateDatabase(destination);
            if (previous.exists()) previous.delete();
        } finally {
            if (downloaded.exists()) downloaded.delete();
        }
    }

    private static void validateDatabase(java.io.File file) throws IOException {
        SQLiteDatabase check = null;
        try {
            check = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            try (android.database.Cursor integrity = check.rawQuery("PRAGMA integrity_check", null)) {
                if (!integrity.moveToFirst() || !"ok".equalsIgnoreCase(integrity.getString(0)))
                    throw new IOException("SQLite integrity check failed.");
            }
            try (android.database.Cursor tables = check.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('cement_moves','company_bookings','customer_bookings','steel_moves')", null)) {
                int count = 0;
                while (tables.moveToNext()) count++;
                if (count < 4)
                    throw new IOException("The file is not a complete Shyam Co. database backup.");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("The backup database could not be validated.", e);
        } finally {
            if (check != null) check.close();
        }
    }

    private static void copy(java.io.File from, java.io.File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[8192]; int n; while((n=in.read(b))>0) out.write(b,0,n); out.flush();
        }
    }
}
