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
            if(target.exists() && !target.delete()) throw new IOException("Cannot replace previous local backup");
            if(!temp.renameTo(target)) throw new IOException("Cannot finalize backup");
            return Result.success();
        } catch(Exception e) { if(temp.exists()) temp.delete(); return Result.retry(); }
    }
}
