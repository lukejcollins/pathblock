package arpa.homelab.pathblock;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;

public final class UpdateInstall extends BroadcastReceiver {
    private static final long MAX_APK_SIZE = 32L * 1024 * 1024;

    static void stage(Context context, Updates.Release release) throws Exception {
        File apk = File.createTempFile("pathblock-update-", ".apk", context.getCacheDir());
        try {
            download(release.url(), apk);
            PackageManager packages = context.getPackageManager();
            String packageName = context.getPackageName();
            int flags = PackageManager.GET_SIGNING_CERTIFICATES;
            PackageInfo installed = packages.getPackageInfo(packageName, flags);
            PackageInfo downloaded = packages.getPackageArchiveInfo(apk.getPath(), flags);
            if (downloaded == null || !packageName.equals(downloaded.packageName)
                    || !release.version().equals(downloaded.versionName)
                    || downloaded.getLongVersionCode() <= installed.getLongVersionCode()
                    || downloaded.signingInfo == null
                    || !Arrays.equals(downloaded.signingInfo.getApkContentsSigners(),
                            installed.signingInfo.getApkContentsSigners())) {
                throw new IOException("The APK did not match this Pathblock installation.");
            }

            PackageInstaller installer = packages.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(packageName);
            params.setSize(apk.length());
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            }
            int id = installer.createSession(params);
            boolean committed = false;
            try (var session = installer.openSession(id)) {
                try (var input = new java.io.FileInputStream(apk);
                     var output = session.openWrite("base.apk", 0, apk.length())) {
                    byte[] buffer = new byte[16384];
                    for (int count; (count = input.read(buffer)) != -1;) {
                        output.write(buffer, 0, count);
                    }
                    session.fsync(output);
                }
                Intent callback = new Intent(context, UpdateInstall.class);
                PendingIntent pending = PendingIntent.getBroadcast(context, id, callback,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                session.commit(pending.getIntentSender());
                committed = true;
            } finally {
                if (!committed) installer.abandonSession(id);
            }
        } finally {
            apk.delete();
        }
    }

    private static void download(String url, File apk) throws IOException {
        HttpURLConnection connection = Updates.connection(url);
        try {
            if (connection.getResponseCode() != 200
                    || !"https".equals(connection.getURL().getProtocol())) {
                throw new IOException("Could not download the release APK.");
            }
            try (var input = connection.getInputStream();
                 var output = new java.io.FileOutputStream(apk)) {
                byte[] buffer = new byte[16384];
                long size = 0;
                for (int count; (count = input.read(buffer)) != -1;) {
                    size += count;
                    if (size > MAX_APK_SIZE) throw new IOException("The release APK is too large.");
                    output.write(buffer, 0, count);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override public void onReceive(Context context, Intent result) {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = result.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(context, "Android could not install the Pathblock update.", Toast.LENGTH_LONG).show();
        }
    }
}
