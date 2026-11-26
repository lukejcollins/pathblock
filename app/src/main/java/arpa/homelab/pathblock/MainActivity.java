package arpa.homelab.pathblock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MainActivity extends Activity {
    private static final String GUARD_SETUP_STEP = "guard_setup_step";
    private static final String GUARD_SETUP_DESTINATION = "guard_setup_destination";
    private static final int TRY_ACCESSIBILITY = 1;
    private static final int OPEN_APP_INFO = 2;
    private static final int CONFIRM_APP_INFO = 3;
    private static final int ENABLE_ACCESSIBILITY = 4;
    private int guardSetupDestination;

    private static final long PAUSE_DELAY_MS = 10_000;
    private static final PauseQuote[] PAUSE_QUOTES = {
        new PauseQuote("My experience is what I agree to attend to.", "William James · The Principles of Psychology, ch. 11", "Is this what you want to give your attention to?"),
        new PauseQuote("Lost time is never found again.", "Benjamin Franklin · The Way to Wealth", "Would you spend these five minutes here on purpose?"),
        new PauseQuote("Dost thou love life? Then do not squander time.", "Benjamin Franklin · The Way to Wealth", "What would you rather do with the next ten minutes?"),
        new PauseQuote("Our life is frittered away by detail.", "Henry David Thoreau · Walden, “Where I Lived, and What I Lived For”", "How many little scrolls made up the last hour?"),
        new PauseQuote("Simplify, simplify.", "Henry David Thoreau · Walden, “Where I Lived, and What I Lived For”", "You can close the feed and be done."),
        new PauseQuote("I wanted to live deep and suck out all the marrow of life.", "Henry David Thoreau · Walden, “Where I Lived, and What I Lived For”", "Does this feed usually leave you glad you opened it?"),
        new PauseQuote("It is not that we have a short space of time, but that we waste much of it.", "Seneca · On the Shortness of Life, §1, trans. Basore", "How long did “just a minute” take last time?"),
        new PauseQuote("While we are postponing, life speeds by.", "Seneca · Moral Letters, Letter 1, trans. Gummere", "What are you putting off by opening this?"),
        new PauseQuote("Hold every hour in your grasp.", "Seneca · Moral Letters, Letter 1, trans. Gummere", "Decide what you want from the site before opening it."),
        new PauseQuote("I wasted time, and now doth time waste me.", "William Shakespeare · Richard II", "Even the kings had trouble with screen time."),
        new PauseQuote("Can one desire too much of a good thing?", "William Shakespeare · As You Like It", "How many more posts would count as enough?"),
        new PauseQuote("I can resist everything except temptation.", "Oscar Wilde · Lady Windermere’s Fan", "The feed is very good at being temptation."),
        new PauseQuote("Nothing will come of nothing.", "William Shakespeare · King Lear", "What do you expect the next scroll to give you?"),
        new PauseQuote("Wisely and slow. They stumble that run fast.", "William Shakespeare · Romeo and Juliet", "Take a breath before you tap through."),
        new PauseQuote("Give me excess of it, that, surfeiting, the appetite may sicken and so die.", "William Shakespeare · Twelfth Night", "How has trying to scroll yourself satisfied worked so far?"),
        new PauseQuote("All that glisters is not gold.", "William Shakespeare · The Merchant of Venice", "A bright headline can still be a waste of your time."),
        new PauseQuote("These violent delights have violent ends.", "William Shakespeare · Romeo and Juliet", "Will another round of outrage improve your evening?"),
        new PauseQuote("What’s done cannot be undone.", "William Shakespeare · Macbeth", "The minutes already spent are gone. You still have the next one."),
        new PauseQuote("Do not waste the remainder of thy life in thoughts about others…", "Marcus Aurelius · Meditations 3.4, trans. Long", "Is the feed really how you want to spend this moment?"),
        new PauseQuote("Give thyself time to learn something new and good, and cease to be whirled around.", "Marcus Aurelius · Meditations 2.7, trans. Long", "Another headline may give you more noise and the same worries."),
        new PauseQuote("Do not act as if thou wert going to live ten thousand years.", "Marcus Aurelius · Meditations 4.17, trans. Long", "The feed can go on forever. Your evening can’t."),
        new PauseQuote("Nothing, Lucilius, is ours, except time.", "Seneca · Moral Letters, Letter 1, trans. Gummere", "Closing the feed takes one tap."),
        new PauseQuote("Men are disturbed not by things, but by the views which they take of things.", "Epictetus · Enchiridion §5, trans. Carter", "The comment section can wait. How do you want to feel after this?"),
        new PauseQuote("First say to yourself what you would be; and then do what you have to do.", "Epictetus · Discourses 3.23, trans. Long", "What did you plan to do before you reached for your phone?"),
        new PauseQuote("What is the use of a book, thought Alice, without pictures or conversation?", "Lewis Carroll · Alice’s Adventures in Wonderland, ch. 1", "Would you rather have a real conversation than another thread?"),
        new PauseQuote("One half of the world cannot understand the pleasures of the other.", "Jane Austen · Emma, ch. 9", "You don’t have to make the internet agree with you."),
        new PauseQuote("For where your treasure is, there will your heart be also.", "Matthew 6:21 · King James Version", "Your attention is worth more than another autoplay."),
        new PauseQuote("Sufficient unto the day is the evil thereof.", "Matthew 6:34 · King James Version", "You don’t need to catch up on every bad thing tonight."),
        new PauseQuote("All things are lawful for me, but I will not be brought under the power of any.", "1 Corinthians 6:12 · King James Version", "Are you choosing to open it, or just following the urge?"),
        new PauseQuote("Better is an handful with quietness, than both the hands full with travail and vexation of spirit.", "Ecclesiastes 4:6 · King James Version", "A quiet minute may serve you better than a full feed."),
    };

    private record PauseQuote(String text, String attribution, String prompt) {}

    private int nextQuote = ThreadLocalRandom.current().nextInt(PAUSE_QUOTES.length);
    private final Set<Rule.Address> selected = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refreshRules;
    private Design design;
    private LinearLayout ruleList;
    private Button guard;
    private TextView ruleCount;
    private CheckBox selectAll;
    private Button pauseSelected;
    private Button resumeSelected;
    private Button removeSelected;
    private boolean updatingSelection;
    private boolean selectionMode;
    private LinearLayout selectionActions;
    private List<Rule> rules = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            guardSetupDestination = state.getInt(GUARD_SETUP_DESTINATION);
        }
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        design = new Design(this);
        LinearLayout body = design.column();
        body.setBackgroundColor(design.background);
        body.setPadding(design.dp(20), design.dp(18), design.dp(20), 0);
        body.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left + design.dp(20), safe.top + design.dp(18),
                    safe.right + design.dp(20), safe.bottom);
            return insets;
        });
        body.setFocusableInTouchMode(true);
        setContentView(body);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(header);
        TextView title = design.text("Pathblock", 34, design.foreground, true);
        title.setAccessibilityHeading(true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        guard = design.button("", design.raised, design.accent);
        guard.setOnClickListener(view -> showGuardControls());
        header.addView(guard);
        ImageButton settings = design.icon(R.drawable.ic_settings, design.foreground, "Settings");
        settings.setOnClickListener(view -> startActivity(new Intent(this, UpdateSettings.class)));
        header.addView(settings);
        Updates.schedule(this);

        LinearLayout entry = design.column();
        entry.setPadding(design.dp(16), design.dp(14), design.dp(12), design.dp(14));
        entry.setBackground(design.shape(design.surface, 26));
        spaced(body, entry, 20);
        entry.addView(design.text("Block a URL", 18, design.foreground, true));
        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackground(design.shape(design.raised, 18));
        spaced(entry, input, 9);
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setTextColor(design.foreground);
        url.setHintTextColor(design.muted);
        url.setTextSize(16);
        url.setHint("example.com/path");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_DONE);
        url.setBackground(null);
        url.setMinHeight(design.dp(52));
        url.setPadding(design.dp(13), 0, design.dp(4), 0);
        input.addView(url, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton add = design.icon(R.drawable.ic_add, design.accent, "Add rule");
        input.addView(add);
        TextView urlError = design.text("", 13, design.danger, true);
        urlError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        urlError.setVisibility(View.GONE);
        spaced(entry, urlError, 8);
        url.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                urlError.setVisibility(View.GONE);
            }
        });
        add.setOnClickListener(view -> addRule(url, urlError));
        url.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            addRule(url, urlError);
            return true;
        });

        LinearLayout rulesHeader = new LinearLayout(this);
        rulesHeader.setGravity(Gravity.CENTER_VERTICAL);
        spaced(body, rulesHeader, 24);
        TextView rulesTitle = design.text("Rules", 24, design.foreground, true);
        rulesTitle.setAccessibilityHeading(true);
        rulesHeader.addView(rulesTitle, new LinearLayout.LayoutParams(0, -2, 1));
        ruleCount = design.text("", 14, design.muted, false);
        rulesHeader.addView(ruleCount);

        selectAll = checkbox("Select all rules");
        body.addView(selectAll);
        selectAll.setOnCheckedChangeListener((view, checked) -> {
            if (updatingSelection) return;
            selected.clear();
            if (checked) rules.forEach(rule -> selected.add(rule.address));
            refreshSelection();
        });
        selectionActions = new LinearLayout(this);
        body.addView(selectionActions);
        pauseSelected = design.button("Pause selected", design.raised, design.accent);
        resumeSelected = design.button("Resume selected", design.raised, design.accent);
        removeSelected = design.button("Remove selected", design.raised, design.danger);
        for (Button action : new Button[]{pauseSelected, resumeSelected, removeSelected}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            if (selectionActions.getChildCount() > 0) params.setMarginStart(design.dp(6));
            selectionActions.addView(action, params);
        }
        pauseSelected.setOnClickListener(view -> chooseDuration(Set.copyOf(selected), true));
        resumeSelected.setOnClickListener(view -> resumeRules(Set.copyOf(selected), true));
        removeSelected.setOnClickListener(view -> confirmRemoval(Set.copyOf(selected)));

        ScrollView rulesScroll = new ScrollView(this);
        rulesScroll.setClipToPadding(false);
        rulesScroll.setVerticalScrollBarEnabled(false);
        rulesScroll.setPadding(0, 0, 0, design.dp(16));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = design.dp(6);
        body.addView(rulesScroll, scrollParams);
        ruleList = design.column();
        rulesScroll.addView(ruleList, new ScrollView.LayoutParams(-1, -2));
        body.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateGuardButton();
        if (systemGuardEnabled()) {
            setGuardSetupStep(TRY_ACCESSIBILITY);
        }
        if (guardSetupDestination != 0) {
            int destination = guardSetupDestination;
            guardSetupDestination = 0;
            if (RuleStore.hasGuardConsent(this) && !systemGuardEnabled()) {
                if (destination == TRY_ACCESSIBILITY) {
                    setGuardSetupStep(OPEN_APP_INFO);
                } else if (destination == OPEN_APP_INFO && guardSetupStep() == OPEN_APP_INFO) {
                    setGuardSetupStep(CONFIRM_APP_INFO);
                }
                showGuardSetup();
            }
        }
        rules = RuleStore.read(this);
        refreshRules();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(GUARD_SETUP_DESTINATION, guardSetupDestination);
    }

    private int guardSetupStep() {
        return RuleStore.preferences(this).getInt(GUARD_SETUP_STEP, TRY_ACCESSIBILITY);
    }

    private void setGuardSetupStep(int step) {
        if (guardSetupStep() != step) {
            RuleStore.preferences(this).edit().putInt(GUARD_SETUP_STEP, step).apply();
        }
    }

    private boolean systemGuardEnabled() {
        ComponentName component = new ComponentName(this, ChromeGuard.class);
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        return manager.getEnabledAccessibilityServiceList(-1).stream()
                .map(info -> info.getResolveInfo().serviceInfo)
                .anyMatch(info -> component.equals(new ComponentName(info.packageName, info.name)));
    }

    private void updateGuardButton() {
        boolean enabled = RuleStore.hasGuardConsent(this) && systemGuardEnabled();
        guard.setText(enabled ? "Guard on" : "Enable guard");
        guard.setContentDescription(enabled ? "Chrome guard on. Manage Chrome guard" : "Enable Chrome guard");
        guard.setTextColor(enabled ? design.accent : design.danger);
    }

    private void showGuardControls() {
        boolean consent = RuleStore.hasGuardConsent(this);
        boolean systemEnabled = systemGuardEnabled();
        if (consent && !systemEnabled) {
            showGuardSetup();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout content = sheet(consent && systemEnabled ? "Chrome guard" : "Enable Chrome guard");
        spaced(content, design.text(
                "Pathblock uses Android Accessibility to read the address shown in Chrome and compare it with your rules. "
                        + "When a rule matches, Pathblock covers the page and may open its local block page in Chrome.",
                16, design.foreground, false), 12);
        spaced(content, design.text(
                "Browsing URLs and rules stay on your phone. You can turn off monitoring here or disable Pathblock in Android Accessibility settings.",
                15, design.muted, false), 10);

        if (!consent) {
            Button agree = design.button("Agree and continue", design.raised, design.accent);
            spaced(content, agree, 16);
            agree.setOnClickListener(view -> {
                RuleStore.setGuardConsent(this, true);
                dialog.dismiss();
                updateGuardButton();
                if (!systemEnabled) showGuardSetup();
            });
        } else {
            Button settings = design.button("Open Accessibility settings", design.raised, design.accent);
            spaced(content, settings, 16);
            settings.setOnClickListener(view -> {
                dialog.dismiss();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            });
            Button off = design.button("Turn off monitoring", design.raised, design.foreground);
            spaced(content, off, 8);
            off.setOnClickListener(view -> {
                RuleStore.setGuardConsent(this, false);
                setGuardSetupStep(TRY_ACCESSIBILITY);
                dialog.dismiss();
                updateGuardButton();
            });
        }
        Button cancel = design.button("Cancel", design.surface, design.foreground);
        cancel.setStateListAnimator(null);
        cancel.setElevation(0);
        spaced(content, cancel, 8);
        cancel.setOnClickListener(view -> dialog.cancel());
        showSheet(dialog, content);
    }

    private void showGuardSetup() {
        int step = guardSetupStep();
        Dialog dialog = new Dialog(this);
        LinearLayout content = sheet("Set up Chrome guard");
        Intent accessibility = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));

        addGuardSetupStep(content, "1. Try enabling Pathblock",
                "In Accessibility, choose Pathblock for Chrome and try turning it on. If Android shows a restricted setting warning, go back to Pathblock.",
                "Open Accessibility", step == TRY_ACCESSIBILITY,
                () -> openGuardSetupSettings(dialog, accessibility, TRY_ACCESSIBILITY));
        addGuardSetupStep(content, "2. Allow restricted settings",
                "In Pathblock’s App info, open the three-dot menu and tap Allow restricted settings if it appears. Then go back to Pathblock.",
                "Open Pathblock App info", step >= OPEN_APP_INFO,
                () -> openGuardSetupSettings(dialog, appInfo, OPEN_APP_INFO));
        if (step == CONFIRM_APP_INFO) {
            Button confirm = design.button("Continue to step 3", design.raised, design.accent);
            spaced(content, confirm, 8);
            confirm.setOnClickListener(view -> {
                setGuardSetupStep(ENABLE_ACCESSIBILITY);
                dialog.dismiss();
                showGuardSetup();
            });
        }
        addGuardSetupStep(content, "3. Turn on Chrome guard",
                "Return to Accessibility and turn on Pathblock for Chrome. The guard status updates automatically when you come back.",
                "Return to Accessibility", step == ENABLE_ACCESSIBILITY,
                () -> openGuardSetupSettings(dialog, accessibility, ENABLE_ACCESSIBILITY));

        Button off = design.button("Turn off monitoring", design.surface, design.foreground);
        off.setStateListAnimator(null);
        spaced(content, off, 12);
        off.setOnClickListener(view -> {
            RuleStore.setGuardConsent(this, false);
            setGuardSetupStep(TRY_ACCESSIBILITY);
            dialog.dismiss();
            updateGuardButton();
        });
        showSheet(dialog, content);
    }

    private void addGuardSetupStep(LinearLayout content, String title, String description,
            String action, boolean enabled, Runnable openSettings) {
        TextView heading = design.text(title, 16, design.foreground, true);
        heading.setAccessibilityHeading(true);
        spaced(content, heading, 14);
        spaced(content, design.text(description, 14, design.muted, false), 4);
        Button button = design.button(action, design.raised, enabled ? design.accent : design.muted);
        button.setAlpha(enabled ? 1f : .55f);
        button.setEnabled(enabled);
        spaced(content, button, 8);
        button.setOnClickListener(view -> openSettings.run());
    }

    private void openGuardSetupSettings(Dialog dialog, Intent intent, int destination) {
        try {
            startActivity(intent);
            guardSetupDestination = destination;
            dialog.dismiss();
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Open this page from Android Settings.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (selectionMode) exitSelection();
        else super.onBackPressed();
    }

    private void exitSelection() {
        selectionMode = false;
        selected.clear();
        refreshRules();
    }

    private void selectRule(Rule.Address address) {
        if (selected.contains(address)) selected.remove(address);
        else selected.add(address);
        refreshSelection();
    }

    private void refreshSelection() {
        selectionMode = !selected.isEmpty();
        refreshRules();
    }

    private void addRule(EditText url, TextView error) {
        String input = url.getText().toString();
        Rule.Address address = Rule.parse(input);
        if (address == null) {
            showUrlError(error, input.isBlank()
                    ? "Enter a website or URL to block."
                    : "Use a valid web address, like example.com/path.");
            return;
        }
        if (rules.stream().anyMatch(rule -> rule.address.equals(address))) {
            showUrlError(error, "This URL is already blocked.");
            return;
        }
        rules.add(new Rule(address, 0));
        RuleStore.write(this, rules);
        url.setText("");
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(url.getWindowToken(), 0);
        url.clearFocus();
        refreshRules();
    }

    private void showUrlError(TextView error, String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void chooseDuration(Set<Rule.Address> addresses, boolean clearSelectionOnApply) {
        if (addresses.isEmpty()) return;
        String title = addresses.size() == 1 ? "Pause rule" : "Pause " + addresses.size() + " rules";
        Dialog dialog = new Dialog(this);
        LinearLayout content = sheet(title);
        PauseQuote quote = PAUSE_QUOTES[nextQuote];
        nextQuote = (nextQuote + 1) % PAUSE_QUOTES.length;
        TextView message = design.text("“" + quote.text() + "”", 18, design.foreground, true);
        spaced(content, message, 16);
        spaced(content, design.text(quote.attribution(), 13, design.muted, false), 6);
        spaced(content, design.text(quote.prompt(), 15, design.foreground, false), 12);
        TextView countdown = design.text("10 seconds", 16, design.accent, true);
        countdown.setGravity(Gravity.CENTER);
        spaced(content, countdown, 18);
        LinearLayout choices = new LinearLayout(this);
        choices.setVisibility(View.GONE);
        spaced(content, choices, 16);
        for (int minutes : new int[]{5, 10, 20}) {
            Button option = design.button(minutes + " min", design.raised, design.accent);
            option.setContentDescription(minutes + " minutes");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, design.dp(60), 1);
            if (choices.getChildCount() > 0) params.setMarginStart(design.dp(8));
            choices.addView(option, params);
            option.setOnClickListener(view -> {
                long until = System.currentTimeMillis() + minutes * 60_000L;
                rules.replaceAll(rule -> addresses.contains(rule.address) ? new Rule(rule.address, until) : rule);
                RuleStore.write(this, rules);
                if (clearSelectionOnApply) {
                    exitSelection();
                } else {
                    refreshRules();
                }
                dialog.dismiss();
            });
        }
        Button cancel = design.button("Cancel", design.surface, design.foreground);
        cancel.setStateListAnimator(null);
        cancel.setElevation(0);
        spaced(content, cancel, 8);
        cancel.setOnClickListener(view -> dialog.cancel());
        CountDownTimer timer = new CountDownTimer(PAUSE_DELAY_MS, 1_000) {
            @Override public void onTick(long remaining) {
                long seconds = (remaining + 999) / 1_000;
                countdown.setText(seconds == 1 ? "1 second" : seconds + " seconds");
            }

            @Override public void onFinish() {
                countdown.setVisibility(View.GONE);
                choices.setVisibility(View.VISIBLE);
                choices.announceForAccessibility("Pause durations available");
            }
        };
        dialog.setOnDismissListener(ignored -> timer.cancel());
        showSheet(dialog, content);
        timer.start();
    }

    private void confirmRemoval(Set<Rule.Address> addresses) {
        if (addresses.isEmpty()) return;
        String title = addresses.size() == 1 ? "Remove this rule?" : "Remove " + addresses.size() + " rules?";
        Dialog dialog = new Dialog(this);
        LinearLayout content = sheet(title);
        LinearLayout actions = new LinearLayout(this);
        spaced(content, actions, 16);
        Button cancel = design.button("Cancel", design.raised, design.foreground);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        cancel.setOnClickListener(view -> dialog.cancel());
        Button remove = design.button("Remove", design.danger, design.background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginStart(design.dp(8));
        actions.addView(remove, params);
        remove.setOnClickListener(view -> {
            rules.removeIf(rule -> addresses.contains(rule.address));
            selected.removeAll(addresses);
            RuleStore.write(this, rules);
            refreshSelection();
            dialog.dismiss();
        });
        showSheet(dialog, content);
    }

    private LinearLayout sheet(String title) {
        LinearLayout content = design.column();
        content.setPadding(design.dp(20), design.dp(12), design.dp(20), design.dp(14));
        content.setBackground(design.shape(design.surface, 28));
        android.view.View handle = new android.view.View(this);
        handle.setBackground(design.shape(design.muted, 2));
        handle.setAlpha(.55f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(design.dp(32), design.dp(4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(handle, params);
        TextView heading = design.text(title, 24, design.foreground, true);
        heading.setAccessibilityHeading(true);
        spaced(content, heading, 16);
        return content;
    }

    private void showSheet(Dialog dialog, LinearLayout content) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView outer = new ScrollView(this);
        outer.setPadding(design.dp(10), 0, design.dp(10), design.dp(10));
        outer.addView(content);
        dialog.setContentView(outer);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.56f);
        window.setGravity(Gravity.BOTTOM);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        window.setLayout(-1, -2);
    }

    private void resumeRules(Set<Rule.Address> addresses, boolean clearSelection) {
        rules.replaceAll(rule -> addresses.contains(rule.address) && rule.pausedUntil != 0
                ? new Rule(rule.address, 0) : rule);
        RuleStore.write(this, rules);
        if (clearSelection) {
            exitSelection();
        } else {
            refreshRules();
        }
    }

    private void refreshRules() {
        handler.removeCallbacks(refresh);
        ruleList.removeAllViews();
        updateSelectionActions();
        if (rules.isEmpty()) {
            TextView empty = design.text("No rules yet", 16, design.muted, false);
            empty.setPadding(design.dp(16), design.dp(18), design.dp(16), design.dp(18));
            empty.setBackground(design.shape(design.surface, 20));
            ruleList.addView(empty);
        }
        long now = System.currentTimeMillis();
        long nextRefresh = Long.MAX_VALUE;
        List<Rule> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparing(rule -> rule.address.label()));
        for (Rule rule : sortedRules) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(design.dp(4), design.dp(7), design.dp(6), design.dp(7));
            row.setBackground(design.shape(design.surface, 20));
            spaced(ruleList, row, 6);

            String address = rule.address.label();
            if (selectionMode) {
                CheckBox select = checkbox("");
                select.setContentDescription("Select " + address);
                select.setChecked(selected.contains(rule.address));
                row.addView(select, new LinearLayout.LayoutParams(design.dp(48), design.dp(48)));
                select.setOnCheckedChangeListener((view, checked) -> {
                    if (checked) selected.add(rule.address); else selected.remove(rule.address);
                    if (selected.isEmpty()) exitSelection();
                    else updateSelectionActions();
                });
            }

            LinearLayout copy = design.column();
            copy.setGravity(Gravity.CENTER_VERTICAL);
            copy.setMinimumHeight(design.dp(48));
            copy.setPadding(design.dp(selectionMode ? 0 : 12), 0, 0, 0);
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView url = design.text(address, 15, design.foreground, true);
            copy.addView(url);
            View.OnLongClickListener beginSelection = new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) {
                    if (Build.VERSION.SDK_INT >= 34) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                    // Keep the pressed view attached until Android has completed the long-click.
                    view.post(() -> selectRule(rule.address));
                    return true;
                }

                @Override public boolean onLongClickUseDefaultHapticFeedback(View view) {
                    return false;
                }
            };
            row.setOnLongClickListener(beginSelection);
            copy.setOnLongClickListener(beginSelection);
            url.setOnLongClickListener(beginSelection);
            if (selectionMode) {
                copy.setOnClickListener(view -> selectRule(rule.address));
                url.setOnClickListener(view -> selectRule(rule.address));
            }
            boolean paused = rule.isPaused(now);
            if (paused) {
                long remaining = rule.pausedUntil - now;
                long minutes = (remaining + 59_999) / 60_000;
                copy.addView(design.text("Paused · " + minutes + " min left", 12, design.accent, false));
                nextRefresh = Math.min(nextRefresh, 1 + (remaining - 1) % 60_000);
            }
            ImageButton pause = design.icon(paused ? R.drawable.ic_play : R.drawable.ic_pause,
                    design.accent, (paused ? "Resume " : "Pause ") + address);
            row.addView(pause);
            pause.setOnClickListener(view -> {
                if (!rule.isPaused(System.currentTimeMillis())) {
                    chooseDuration(Set.of(rule.address), false);
                    return;
                }
                resumeRules(Set.of(rule.address), false);
            });
            ImageButton remove = design.icon(R.drawable.ic_remove, design.danger, "Remove " + address);
            row.addView(remove);
            remove.setOnClickListener(view -> confirmRemoval(Set.of(rule.address)));
        }
        if (nextRefresh != Long.MAX_VALUE) handler.postDelayed(refresh, nextRefresh);
    }

    private CheckBox checkbox(String label) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(design.foreground);
        box.setButtonTintList(ColorStateList.valueOf(design.accent));
        box.setMinHeight(design.dp(48));
        return box;
    }

    private void updateSelectionActions() {
        updatingSelection = true;
        int visibility = selectionMode ? android.view.View.VISIBLE : android.view.View.GONE;
        selectAll.setVisibility(visibility);
        selectionActions.setVisibility(visibility);
        selectAll.setEnabled(!rules.isEmpty());
        selectAll.setChecked(!rules.isEmpty() && selected.size() == rules.size());
        updatingSelection = false;
        boolean hasSelection = !selected.isEmpty();
        long now = System.currentTimeMillis();
        boolean canResume = rules.stream()
                .anyMatch(rule -> selected.contains(rule.address) && rule.isPaused(now));
        pauseSelected.setEnabled(hasSelection);
        resumeSelected.setEnabled(canResume);
        resumeSelected.setAlpha(canResume ? 1f : .45f);
        removeSelected.setEnabled(hasSelection);
        pauseSelected.setAlpha(hasSelection ? 1f : .45f);
        removeSelected.setAlpha(hasSelection ? 1f : .45f);
        ruleCount.setText(hasSelection ? selected.size() + " selected" : String.valueOf(rules.size()));
    }

    private void spaced(LinearLayout parent, android.view.View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = design.dp(top);
        parent.addView(child, params);
    }
}
