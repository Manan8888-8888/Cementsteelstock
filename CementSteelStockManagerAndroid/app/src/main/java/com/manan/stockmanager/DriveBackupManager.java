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
        FileList found = drive.files().list().setQ("name='" + BACKUP_NAME + "' and trashed=false")
                .setSpaces("drive").setOrderBy("modifiedTime desc")
                .setFields("files(id,name,modifiedTime,size)").setPageSize(100).execute();
        if (found.getFiles() == null || found.getFiles().isEmpty())
            throw new FileNotFoundException("No ShyamCo_Backup.db was found in this Google Drive account.");
        com.google.api.services.drive.model.File remote = found.getFiles().get(0);
        java.io.File temp = new java.io.File(context.getCacheDir(), "drive-restore-check.db");
        try (OutputStream out = new FileOutputStream(temp)) {
            drive.files().get(remote.getId()).executeMediaAndDownloadTo(out);
        }
        SQLiteDatabase check = SQLiteDatabase.openDatabase(temp.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try (android.database.Cursor c = check.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('cement_moves','company_bookings','customer_bookings','steel_moves')", null)) {
            int count=0; while(c.moveToNext()) count++;
            if(count < 4) throw new IOException("The Drive file is not a valid Shyam Co. database backup.");
        } finally { check.close(); }
        java.io.File destination = context.getDatabasePath("stock_manager.db");
        java.io.File safety = new java.io.File(context.getFilesDir(), "pre_drive_restore_backup.db");
        if(destination.exists()) copy(destination, safety);
        // Remove SQLite sidecar files so an old WAL cannot be replayed onto the restored database.
        java.io.File wal = new java.io.File(destination.getAbsolutePath() + "-wal");
        java.io.File shm = new java.io.File(destination.getAbsolutePath() + "-shm");
        if (wal.exists() && !wal.delete()) throw new IOException("Could not clear old database WAL before restore.");
        if (shm.exists() && !shm.delete()) throw new IOException("Could not clear old database SHM before restore.");
        copy(temp, destination);
        temp.delete();
    }

    private static void copy(java.io.File from, java.io.File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[8192]; int n; while((n=in.read(b))>0) out.write(b,0,n); out.flush();
        }
    }
}
