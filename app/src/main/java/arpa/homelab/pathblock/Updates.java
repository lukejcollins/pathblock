package arpa.homelab.pathblock;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public final class Updates extends JobService {
    static final String AUTO = "automatic";
    private static final int JOB = 851;
    private static final String REPO = "lukejcollins/pathblock";
    private static final int NOTIFICATION = 851;
    private static final String CHANNEL = "updates";
    record Release(String version, String notes, String pageUrl) {}
    private Thread worker;

    static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("updates", MODE_PRIVATE);
    }

    static boolean canNotify(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static boolean backgroundUpdatesEnabled(Context context) {
        return settings(context).getBoolean(AUTO, true) && canNotify(context);
    }

    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (!backgroundUpdatesEnabled(context)) {
            jobs.cancel(JOB);
        } else if (jobs.getPendingJob(JOB) == null) {
            jobs.schedule(new JobInfo.Builder(JOB, new ComponentName(context, Updates.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setPeriodic(TimeUnit.DAYS.toMillis(1))
                    .build());
        }
    }

    static HttpURLConnection connection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        return connection;
    }

    static Release latest(Context context) throws Exception {
        HttpURLConnection connection = connection("https://api.github.com/repos/" + REPO + "/releases/latest");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status == 404) throw new IOException("No public Pathblock release is available yet.");
            if (status != 200) throw new IOException("GitHub could not check for updates.");
            JSONObject json;
            try (var input = connection.getInputStream()) {
                var response = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (response.size() + count > 512 * 1024) {
                        throw new IOException("Release information is too large.");
                    }
                    response.write(buffer, 0, count);
                }
                json = new JSONObject(new String(response.toByteArray(), StandardCharsets.UTF_8));
            }
            String tag = json.getString("tag_name");
            if (!tag.matches("v?[0-9]{1,6}(\\.[0-9]{1,6}){1,2}")) throw new IOException("Unrecognized release version.");
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            String installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            if (!newer(version, installed)) return null;
            var assets = json.getJSONArray("assets");
            String filename = "Pathblock-" + version + ".apk";
            String expected = "https://github.com/" + REPO + "/releases/download/" + tag + "/" + filename;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (filename.equals(asset.optString("name")) && expected.equals(asset.optString("browser_download_url"))) {
                    return new Release(version, json.optString("body"),
                            "https://github.com/" + REPO + "/releases/tag/" + tag);
                }
            }
            throw new IOException("The latest release has no Pathblock APK.");
        } finally {
            connection.disconnect();
        }
    }

    private static boolean newer(String candidate, String installed) {
        String[] a = candidate.split("\\.");
        String[] b = installed.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int comparison = Integer.compare(i < a.length ? Integer.parseInt(a[i]) : 0,
                    i < b.length ? Integer.parseInt(b[i]) : 0);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    @Override public boolean onStartJob(JobParameters parameters) {
        if (!backgroundUpdatesEnabled(this)) return false;
        worker = new Thread(() -> {
            try {
                Release release = latest(this);
                if (release != null && !Thread.currentThread().isInterrupted()
                        && backgroundUpdatesEnabled(this)) notifyUpdate(release);
            } catch (Exception ignored) {
                // The next scheduled check can retry without interrupting the user.
            } finally {
                if (!Thread.currentThread().isInterrupted()) jobFinished(parameters, false);
            }
        }, "Pathblock update check");
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        if (worker != null) worker.interrupt();
        return false;
    }

    private void notifyUpdate(Release release) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (!notifications.areNotificationsEnabled()
                || release.version().equals(settings(this).getString("notified", ""))) return;
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "App updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(this, UpdateSettings.class);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        notifications.notify(NOTIFICATION, new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.pathblock_shield)
                .setContentTitle("Pathblock update available")
                .setContentText("Version " + release.version())
                .setAutoCancel(true).setContentIntent(tap).build());
        settings(this).edit().putString("notified", release.version()).apply();
    }
}
