package com.manan.stockmanager;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.io.File;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class BackupScheduler {
    private BackupScheduler() {}
    public static void scheduleDailyLocalBackup(Context context) {
        Calendar now=Calendar.getInstance();
        Calendar next=Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY,22); next.set(Calendar.MINUTE,0); next.set(Calendar.SECOND,0); next.set(Calendar.MILLISECOND,0);
        if(!next.after(now)) next.add(Calendar.DAY_OF_YEAR,1);
        long delay=Math.max(60_000L,next.getTimeInMillis()-now.getTimeInMillis());
        Constraints constraints=new Constraints.Builder().build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(DailyLocalBackupWorker.class,24,TimeUnit.HOURS,1,TimeUnit.HOURS)
                .setInitialDelay(delay,TimeUnit.MILLISECONDS).setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("shyam_co_daily_local_backup", ExistingPeriodicWorkPolicy.UPDATE, request);
        Constraints cloudConstraints=new Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build();
        PeriodicWorkRequest cloudRequest=new PeriodicWorkRequest.Builder(DailyDriveBackupWorker.class,24,TimeUnit.HOURS,1,TimeUnit.HOURS)
                .setInitialDelay(delay,TimeUnit.MILLISECONDS).setConstraints(cloudConstraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("shyam_co_daily_drive_backup", ExistingPeriodicWorkPolicy.UPDATE, cloudRequest);
    }
    public static File latestLocalBackup(Context context) {
        File f=new File(context.getFilesDir(),"backups/ShyamCo_latest.db");
        return f.exists() && f.length()>0 ? f : null;
    }
}
