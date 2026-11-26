package arpa.homelab.pathblock;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.IOException;

public final class UpdateSettings extends Activity {
    private TextView status;
    private TextView notes;
    private Button check;
    private Button releasePage;
    private Button notificationSettings;
    private boolean requestedNotifications;
    private Updates.Release available;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        getWindow().setDecorFitsSystemWindows(false);
        Design design = new Design(this);
        LinearLayout root = design.column();
        root.setBackgroundColor(design.background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left + design.dp(20), safe.top + design.dp(8),
                    safe.right + design.dp(20), safe.bottom + design.dp(12));
            return insets;
        });
        setContentView(root);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar);
        var back = design.icon(R.drawable.ic_back, design.foreground, "Back");
        back.setOnClickListener(view -> finish());
        bar.addView(back);
        TextView title = design.text("Settings", 28, design.foreground, true);
        title.setAccessibilityHeading(true);
        bar.addView(title);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = design.column();
        scroll.addView(body);
        TextView heading = design.text("Updates", 14, design.accent, true);
        heading.setPadding(design.dp(16), design.dp(24), 0, design.dp(10));
        heading.setAccessibilityHeading(true);
        body.addView(heading);

        LinearLayout card = design.column();
        card.setPadding(design.dp(16), design.dp(8), design.dp(16), design.dp(16));
        card.setBackground(design.shape(design.surface, 24));
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        Switch automatic = new Switch(this);
        automatic.setText("Automatically check for updates");
        automatic.setTextSize(16);
        automatic.setTextColor(design.foreground);
        automatic.setMinHeight(design.dp(56));
        automatic.setSwitchPadding(design.dp(12));
        automatic.setThumbTintList(new ColorStateList(new int[][]{
                {android.R.attr.state_checked}, {}}, new int[]{design.accent, design.muted}));
        automatic.setChecked(Updates.settings(this).getBoolean(Updates.AUTO, true));
        card.addView(automatic, new LinearLayout.LayoutParams(-1, -2));
        TextView summary = design.text("Contacts GitHub about once a day for release information. Download updates from GitHub Releases.", 14, design.muted, false);
        card.addView(summary);
        notificationSettings = design.button("", design.raised, design.foreground);
        LinearLayout.LayoutParams notificationLayout = new LinearLayout.LayoutParams(-1, -2);
        notificationLayout.topMargin = design.dp(12);
        card.addView(notificationSettings, notificationLayout);
        requestedNotifications = Updates.settings(this).getBoolean("requested_notifications", false);
        notificationSettings.setOnClickListener(view -> openNotificationSettings());
        automatic.setOnCheckedChangeListener((view, enabled) -> {
            Updates.settings(this).edit().putBoolean(Updates.AUTO, enabled).apply();
            Updates.schedule(this);
        });

        check = design.button("Check for updates", design.raised, design.accent);
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-1, -2);
        button.topMargin = design.dp(16);
        card.addView(check, button);
        status = design.text("", 14, design.muted, false);
        status.setPadding(0, design.dp(12), 0, 0);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        card.addView(status);
        notes = design.text("", 14, design.foreground, false);
        notes.setVisibility(View.GONE);
        card.addView(notes);
        releasePage = design.button("Open GitHub release", design.raised, design.accent);
        card.addView(releasePage);
        releasePage.setVisibility(Button.GONE);
        check.setOnClickListener(view -> check());
        releasePage.setOnClickListener(view -> openReleasePage());
        check();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshNotificationStatus();
    }

    private void refreshNotificationStatus() {
        boolean enabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        notificationSettings.setText(enabled ? "Update notifications · On" : "Update notifications · Off");
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !requestedNotifications
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestedNotifications = true;
            Updates.settings(this).edit().putBoolean("requested_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1) refreshNotificationStatus();
    }

    private void check() {
        check.setEnabled(false);
        releasePage.setVisibility(Button.GONE);
        notes.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText("Checking for updates…");
        new Thread(() -> {
            try {
                Updates.Release release = Updates.latest(this);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    check.setEnabled(true);
                    available = release;
                    status.setText(release == null ? "Pathblock is up to date." : "Version " + release.version() + " is available.");
                    if (release != null) {
                        notes.setText(release.notes());
                        notes.setVisibility(release.notes().trim().isEmpty() ? View.GONE : View.VISIBLE);
                        releasePage.setVisibility(Button.VISIBLE);
                    }
                });
            } catch (Exception error) {
                showError(error, "Could not check for updates.");
            }
        }, "Pathblock manual update check").start();
    }

    private void openReleasePage() {
        if (available == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(available.pageUrl())));
        } catch (ActivityNotFoundException error) {
            status.setText("No app could open the GitHub release.");
        }
    }

    private void showError(Exception error, String fallback) {
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            check.setEnabled(true);
            status.setText(error instanceof IOException ? error.getMessage() : fallback);
        });
    }
}
