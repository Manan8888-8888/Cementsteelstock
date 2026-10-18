package com.manan.stockmanager;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

/** Daily cloud upload after the user has authorized the saved Google account in-app. */
public class DailyDriveBackupWorker extends Worker {
    public DailyDriveBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
    @NonNull @Override public Result doWork() {
        Context c=getApplicationContext();
        String email=c.getSharedPreferences("backup_settings",Context.MODE_PRIVATE).getString("backup_email","");
        if(email.isEmpty()) return Result.success(); // user has not configured cloud backup yet
        GoogleSignInAccount account=GoogleSignIn.getLastSignedInAccount(c);
        if(account==null || account.getEmail()==null || !email.equalsIgnoreCase(account.getEmail())
                || !GoogleSignIn.hasPermissions(account,new Scope(DriveScopes.DRIVE_FILE))) return Result.success();
        try { DriveBackupManager.upload(c,email); return Result.success(); }
        catch(Exception e) { return Result.retry(); }
    }
}
