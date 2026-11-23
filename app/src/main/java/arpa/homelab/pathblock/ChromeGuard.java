package arpa.homelab.pathblock;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Browser;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public final class ChromeGuard extends AccessibilityService {
    private static final String CHROME = "com.android.chrome";
    private static final String URL_BAR = CHROME + ":id/url_bar";
    private static final String TITLE = "Page blocked";
    private static final String EXPLANATION = "This address matches a Pathblock rule.";
    private static final String RETURN = "Return to Chrome";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::inspectChrome;
    private final SharedPreferences.OnSharedPreferenceChangeListener onRulesChanged =
            (preferences, key) -> { rules = RuleStore.read(this); queueInspection(); };
    private List<Rule> rules = List.of();
    private WindowManager windowManager;
    private LinearLayout overlay;
    private Rule.Address lastAddress;
    private boolean neutralPage;
    private boolean returnRequested;
    private Button returnButton;
    private long previewRequestedAt;
    private long retryAt;
    private long fallbackAt;
    private final Runnable expiry = this::inspectChrome;

    @Override protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
        rules = RuleStore.read(this);
        RuleStore.preferences(this).registerOnSharedPreferenceChangeListener(onRulesChanged);
        queueInspection();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        queueInspection();
    }

    @Override public void onInterrupt() {
        handler.removeCallbacks(refresh);
        handler.removeCallbacks(expiry);
        dismissOverlay();
    }

    @Override public void onDestroy() {
        RuleStore.preferences(this).unregisterOnSharedPreferenceChangeListener(onRulesChanged);
        onInterrupt();
        super.onDestroy();
    }

    private void queueInspection() {
        if (!handler.hasCallbacks(refresh)) handler.postDelayed(refresh, 45);
    }

    private void inspectChrome() {
        handler.removeCallbacks(refresh);
        long now = System.currentTimeMillis();
        if (returnButton != null && retryAt != 0 && SystemClock.uptimeMillis() >= retryAt) {
            retryAt = 0;
            returnButton.setText("Try again");
        }
        handler.removeCallbacks(expiry);
        long next = rules.stream().mapToLong(rule -> rule.pausedUntil)
                .filter(deadline -> deadline > now).min().orElse(0);
        if (next > 0) handler.postDelayed(expiry, next - now);
        if (overlay != null && lastAddress != null && !neutralPage && rules.stream().noneMatch(rule -> rule.matches(lastAddress, now))) {
            dismissOverlay();
        }
        if (rules.isEmpty()) return;
        AccessibilityNodeInfo root = null;
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
                root = window.getRoot();
                break;
            }
        }
        if (root == null) root = getRootInActiveWindow();
        if (root == null) {
            if (overlay != null) handler.postDelayed(refresh, 250);
            return;
        }
        if (!CHROME.contentEquals(root.getPackageName())) {
            lastAddress = null;
            dismissOverlay();
            return;
        }
        boolean sawAddress = false;
        boolean sawBlockedAddress = false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(URL_BAR)) {
            if (!node.isVisibleToUser()) continue;
            String url = addressBarText(node);
            boolean showingPreview = BlockPageProvider.URI.toString().equals(url);
            if (showingPreview || url.equals("about:blank")) {
                markPreviewReady();
                sawAddress = true;
                break;
            }
            if (node.isFocused()) continue;
            if (providerWasOpened() && url.equals("pathblock/blocked.html")) {
                markPreviewReady();
                sawAddress = true;
                break;
            }
            Rule.Address address = Rule.parse(url);
            if (address == null) continue;
            sawAddress = true;
            neutralPage = false;
            lastAddress = address;
            if (rules.stream().anyMatch(rule -> rule.matches(address, now))) {
                sawBlockedAddress = true;
                if (overlay == null) {
                    showOverlay();
                    showChromeBlockPage();
                } else if (!providerWasOpened() && fallbackAt != 0
                        && SystemClock.uptimeMillis() >= fallbackAt) {
                    showBlankChromePage();
                }
            } else {
                dismissOverlay();
            }
            break;
        }
        if (!sawAddress && overlay != null && providerWasOpened()
                && hasVisibleText(root, "Pathblock") && hasVisibleText(root, TITLE)) {
            markPreviewReady();
        }
        if (returnRequested && !sawBlockedAddress && providerWasOpened()
                && SystemClock.uptimeMillis() - BlockPageProvider.chromeReadAt() >= 650) {
            markPreviewReady();
        }
        // A site can update the URL with history.pushState without a Chrome accessibility event.
        handler.postDelayed(refresh, overlay != null ? 250 : 500);
    }

    private static String addressBarText(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        if (text == null || text.length() == 0) text = node.getContentDescription();
        if (text == null) return "";
        return text.toString().replace("‎", "").replace("‏", "").strip();
    }

    private static boolean hasVisibleText(AccessibilityNodeInfo root, String text) {
        return root.findAccessibilityNodeInfosByText(text).stream().anyMatch(node ->
                node.isVisibleToUser() && node.getText() != null && text.contentEquals(node.getText()));
    }

    private boolean providerWasOpened() {
        return previewRequestedAt != 0 && BlockPageProvider.chromeReadAt() >= previewRequestedAt;
    }

    private void markPreviewReady() {
        neutralPage = true;
        fallbackAt = 0;
        if (returnRequested) dismissOverlay();
    }

    private void showChromeBlockPage() {
        Intent block = new Intent(Intent.ACTION_VIEW);
        block.setClassName(CHROME, "com.google.android.apps.chrome.Main");
        block.setDataAndType(BlockPageProvider.URI, "text/html");
        block.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        block.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
        try {
            previewRequestedAt = SystemClock.uptimeMillis();
            startActivity(block);
            fallbackAt = SystemClock.uptimeMillis() + 1800;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            showBlankChromePage();
        }
    }

    private void showBlankChromePage() {
        fallbackAt = 0;
        Intent blank = new Intent(Intent.ACTION_VIEW, Uri.parse("about:blank"));
        blank.setPackage(CHROME);
        blank.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        blank.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
        try {
            startActivity(blank);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // If Chrome cannot open a blank page, the shield continues to cover the live page.
        }
    }

    private void showOverlay() {
        if (overlay != null || windowManager == null) return;
        Design design = new Design(this);
        LinearLayout screen = design.column();
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(design.dp(24), design.dp(24), design.dp(24), design.dp(24));
        screen.setBackgroundColor(design.background);
        screen.setClickable(true);
        LinearLayout card = design.column();
        card.setPadding(design.dp(22), design.dp(26), design.dp(22), design.dp(20));
        card.setBackground(design.shape(design.surface, 30));
        screen.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = design.text(TITLE, 30, design.accent, true);
        heading.setAccessibilityHeading(true);
        card.addView(heading);
        TextView explanation = design.text(EXPLANATION, 15, design.muted, false);
        explanation.setPadding(0, design.dp(8), 0, design.dp(16));
        card.addView(explanation);
        Button back = design.button(RETURN, design.raised, design.accent);
        returnButton = back;
        back.setOnClickListener(view -> {
            boolean retry = returnRequested && retryAt == 0;
            returnRequested = true;
            if (neutralPage) {
                dismissOverlay();
                return;
            }
            back.setText("Opening Pathblock…");
            if (retry || (fallbackAt == 0 && !providerWasOpened())) showChromeBlockPage();
            retryAt = SystemClock.uptimeMillis() + 2300;
            queueInspection();
        });
        card.addView(back);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.OPAQUE);
        params.setTitle("Pathblock shield");
        windowManager.addView(screen, params);
        overlay = screen;
    }

    private void dismissOverlay() {
        if (overlay == null) return;
        windowManager.removeView(overlay);
        overlay = null;
        fallbackAt = 0;
        retryAt = 0;
        previewRequestedAt = 0;
        returnRequested = false;
        returnButton = null;
    }
}
