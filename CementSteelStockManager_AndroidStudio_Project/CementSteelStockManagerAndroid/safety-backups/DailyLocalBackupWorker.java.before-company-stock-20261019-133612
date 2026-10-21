package com.manan.stockmanager;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DailyLocalBackupWorker extends Worker {
    public DailyLocalBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
    @NonNull @Override public Result doWork() {
        Context c=getApplicationContext();
        File dbFile=c.getDatabasePath("stock_manager.db");
        if(!dbFile.exists()) return Result.success();
        File dir=new File(c.getFilesDir(),"backups");
        if(!dir.exists() && !dir.mkdirs()) return Result.retry();
        File temp=new File(dir,"ShyamCo_latest.tmp");
        File target=new File(dir,"ShyamCo_latest.db");
        try {
            SQLiteDatabase db=SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE);
            db.rawQuery("PRAGMA wal_checkpoint(FULL)",null).close(); db.close();
            try(FileInputStream in=new FileInputStream(dbFile); FileOutputStream out=new FileOutputStream(temp)) {
                byte[] b=new byte[8192]; int n; while((n=in.read(b))>0) out.write(b,0,n); out.getFD().sync();
            }
            if(temp.length()==0) throw new IOException("Empty backup");

            // Validate the temporary copy before replacing the last known-good backup.
            SQLiteDatabase check = SQLiteDatabase.openDatabase(
                    temp.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                try(android.database.Cursor integrity=check.rawQuery("PRAGMA integrity_check",null)) {
                    if(!integrity.moveToFirst() || !"ok".equalsIgnoreCase(integrity.getString(0)))
                        throw new IOException("Backup integrity check failed");
                }
                try(android.database.Cursor tables=check.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                        "('cement_moves','company_bookings','customer_bookings','steel_moves')",null)) {
                    int count=0; while(tables.moveToNext()) count++;
                    if(count<4) throw new IOException("Backup is missing required tables");
                }
            } finally { check.close(); }

            File previous=new File(dir,"ShyamCo_previous.tmp");
            if(previous.exists() && !previous.delete())
                throw new IOException("Cannot clear temporary previous-backup file");

            boolean movedOld=false;
            if(target.exists()) {
                if(!target.renameTo(previous))
                    throw new IOException("Cannot safely move previous backup aside");
                movedOld=true;
            }
            if(!temp.renameTo(target)) {
                if(movedOld) previous.renameTo(target);
                throw new IOException("Cannot finalize backup; previous backup was preserved where possible");
            }
            if(previous.exists()) previous.delete();
            return Result.success();
        } catch(Exception e) {
            if(temp.exists()) temp.delete();
            return Result.retry();
        }
    }
}
