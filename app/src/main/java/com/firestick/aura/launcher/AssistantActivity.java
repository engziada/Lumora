package com.firestick.aura.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.assistant.AssistantText;
import com.firestick.aura.launcher.assistant.LocalCommands;
import com.firestick.aura.launcher.assistant.OpenRouterClient;
import com.firestick.aura.launcher.assistant.PhoneBridge;
import com.firestick.aura.launcher.assistant.TvAction;
import com.firestick.aura.launcher.assistant.TvController;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.ui.LockPad;
import com.firestick.aura.launcher.ui.PhonePairDialog;

import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chat panel over whatever is on screen. Opened from the floating button on Home or by holding the
 * remote's Menu button (QuickActionService). Questions go to OpenRouter with the user's key; they can
 * also come from a phone over the home network (PhoneBridge), so the phone keyboard's mic works.
 * With TV control on, replies and plain commands run whitelisted TV actions (TvController).
 */
public class AssistantActivity extends AppCompatActivity implements PhoneBridge.Listener {
    private static final int MAX_TURNS = 12, MAX_APPS = 80, MAX_PENDING = 5;
    /** Kept for the life of the process so closing and reopening continues the conversation. */
    private static final List<OpenRouterClient.Message> HISTORY = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private AssistantSettings settings;
    private PreferenceManager preferences;
    private ScrollView scroll;
    private LinearLayout messages;
    private View setupPanel, inputRow, send, mic;
    private ImageView phoneIcon;
    private EditText input;
    private TextView status;
    private ActivityResultLauncher<Intent> speech;
    private PhoneBridge phone;
    private PhonePairDialog pairDialog;
    private boolean busy;
    private int generation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_assistant);
        settings = new AssistantSettings(this);
        preferences = new PreferenceManager(this);
        scroll = findViewById(R.id.scrollAssistant);
        messages = findViewById(R.id.assistantMessages);
        setupPanel = findViewById(R.id.assistantSetup);
        inputRow = findViewById(R.id.assistantInputRow);
        input = findViewById(R.id.inputAssistant);
        status = findViewById(R.id.txtAssistantStatus);
        send = findViewById(R.id.btnAssistantSend);
        mic = findViewById(R.id.btnAssistantMic);
        phoneIcon = findViewById(R.id.imgAssistantPhone);

        int accent = accent();
        ((ImageView) findViewById(R.id.imgAssistantMark)).setImageTintList(ColorStateList.valueOf(accent));
        ((ImageView) findViewById(R.id.imgAssistantSend)).setImageTintList(ColorStateList.valueOf(accent));

        speech = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            ArrayList<String> heard = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) {
                input.setText(heard.get(0));
                submit();
            }
        });
        // Fire OS has no speech recognizer for apps; the button appears only on devices that have one.
        boolean canListen = !getPackageManager()
                .queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isEmpty();
        mic.setVisibility(canListen ? View.VISIBLE : View.GONE);
        mic.setOnClickListener(v -> listen());
        findViewById(R.id.btnAssistantPhone).setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            openPhoneLink();
        });
        send.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            submit();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                submit();
                return true;
            }
            return false;
        });
        findViewById(R.id.btnAssistantClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnAssistantClear).setOnClickListener(v -> clearConversation());
        findViewById(R.id.btnAssistantSetup).setOnClickListener(v -> openAssistantSettings());
        renderHistory();
        updatePhoneButton();
    }

    @Override protected void onResume() {
        super.onResume();
        updateMode();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        updateMode();
    }

    /** The phone link must not stay open once the assistant is no longer on screen. */
    @Override protected void onStop() {
        stopPhoneLink();
        super.onStop();
    }

    @Override protected void onDestroy() {
        generation++;
        stopPhoneLink();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void updateMode() {
        boolean ready = settings.hasApiKey();
        setupPanel.setVisibility(ready ? View.GONE : View.VISIBLE);
        scroll.setVisibility(ready ? View.VISIBLE : View.GONE);
        inputRow.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (!busy) status.setText(getString(R.string.assistant_status_model, settings.getModel()));
        (ready ? input : findViewById(R.id.btnAssistantSetup)).requestFocus();
    }

    // ---------------------------------------------------------- phone link

    private void openPhoneLink() {
        if (phone == null || !phone.isRunning()) {
            phone = new PhoneBridge(PhoneBridge.MODE_CHAT, this);
            int result = phone.start();
            if (result != PhoneBridge.START_OK) {
                phone = null;
                updatePhoneButton();
                addBubble(getString(result == PhoneBridge.START_NO_NETWORK
                        ? R.string.phone_no_network : R.string.phone_start_failed), false, true);
                return;
            }
        }
        updatePhoneButton();
        pairDialog = PhonePairDialog.show(this, phone, getString(R.string.phone_title_chat),
                getString(R.string.phone_message_chat), null);
    }

    private void stopPhoneLink() {
        if (pairDialog != null) pairDialog.dismiss();
        pairDialog = null;
        if (phone != null) phone.stop();
        phone = null;
        pending.clear();
        if (phoneIcon != null) updatePhoneButton();
    }

    private void updatePhoneButton() {
        boolean linked = phone != null && phone.isRunning();
        phoneIcon.setImageTintList(ColorStateList.valueOf(linked ? accent() : color(R.color.nova_text)));
    }

    @Override public void onText(String text) {
        if (pairDialog != null) pairDialog.dismiss();
        if (!settings.hasApiKey() && LocalCommands.match(text) == null) return;
        if (busy) {
            if (pending.size() < MAX_PENDING) pending.addLast(text);
            return;
        }
        input.setText(text);
        submit();
    }

    @Override public void onCodeChanged(String code) {
        if (pairDialog != null && pairDialog.isShowing()) pairDialog.refresh();
    }

    @Override public void onStopped(boolean idle) {
        if (pairDialog != null) pairDialog.dismiss();
        pairDialog = null;
        phone = null;
        pending.clear();
        updatePhoneButton();
        addBubble(getString(idle ? R.string.phone_stopped_idle : R.string.phone_stopped_error), false, !idle);
    }

    // ------------------------------------------------------------ messages

    private void renderHistory() {
        messages.removeAllViews();
        if (HISTORY.isEmpty()) {
            addBubble(getString(R.string.assistant_welcome), false, false);
            return;
        }
        for (OpenRouterClient.Message message : HISTORY) {
            boolean user = "user".equals(message.role);
            if (user) {
                addBubble(message.content, true, false);
            } else {
                AssistantText.Reply reply = AssistantText.parse(message.content);
                addBubble(reply.text, false, false);
                addOpenButtons(reply.packages);
            }
        }
    }

    private void clearConversation() {
        generation++;
        HISTORY.clear();
        pending.clear();
        setBusy(false);
        renderHistory();
        input.requestFocus();
    }

    private void submit() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || busy) return;
        TvAction local = settings.isControlEnabled() ? LocalCommands.match(text) : null;
        if (local != null) {
            // Plain commands run at once, without a model request; they are not added to the history.
            input.setText("");
            if (HISTORY.isEmpty()) messages.removeAllViews();
            addBubble(text, true, false);
            if (!perform(Collections.singletonList(local), Collections.emptyList())) {
                String next = pending.pollFirst();
                if (next != null) {
                    input.setText(next);
                    submit();
                }
            }
            return;
        }
        if (!settings.hasApiKey()) {
            updateMode();
            return;
        }
        input.setText("");
        if (HISTORY.isEmpty()) messages.removeAllViews();
        HISTORY.add(new OpenRouterClient.Message("user", text));
        addBubble(text, true, false);
        setBusy(true);
        int request = ++generation;
        String key = settings.getApiKey();
        String model = settings.getModel();
        boolean shareApps = settings.isShareAppsEnabled();
        boolean control = settings.isControlEnabled();
        List<OpenRouterClient.Message> turns = recentTurns();
        String now = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.getDefault()).format(new Date());
        executor.execute(() -> {
            List<String> apps = shareApps ? installedApps() : Collections.emptyList();
            List<OpenRouterClient.Message> payload = new ArrayList<>();
            payload.add(new OpenRouterClient.Message("system", AssistantText.systemPrompt(now, apps, control)));
            payload.addAll(turns);
            OpenRouterClient.Result result = OpenRouterClient.chat(key, model, payload);
            runOnUiThread(() -> {
                if (request != generation || isFinishing() || isDestroyed()) return;
                onResult(text, result);
            });
        });
    }

    private List<OpenRouterClient.Message> recentTurns() {
        int from = Math.max(0, HISTORY.size() - MAX_TURNS);
        return new ArrayList<>(HISTORY.subList(from, HISTORY.size()));
    }

    private void onResult(String question, OpenRouterClient.Result result) {
        setBusy(false);
        if (result.ok()) {
            AssistantText.Reply reply = AssistantText.parse(result.text);
            HISTORY.add(new OpenRouterClient.Message("assistant", result.text));
            addBubble(reply.text, false, false);
            addOpenButtons(reply.packages);
            if (!result.model.isEmpty()) status.setText(getString(R.string.assistant_status_answered, result.model));
            if (settings.isControlEnabled() && perform(reply.actions, reply.packages)) {
                pending.clear();
                return;
            }
            String next = pending.pollFirst();
            if (next != null) {
                input.setText(next);
                submit();
            }
            return;
        }
        // Drop the failed question so a retry does not send it twice; put it back in the field.
        if (!HISTORY.isEmpty()) HISTORY.remove(HISTORY.size() - 1);
        pending.clear();
        input.setText(question);
        addBubble(errorText(result), false, true);
    }

    private String errorText(OpenRouterClient.Result result) {
        int res;
        switch (result.error) {
            case OpenRouterClient.ERR_NO_KEY:
            case OpenRouterClient.ERR_AUTH:
                res = R.string.assistant_err_auth;
                break;
            case OpenRouterClient.ERR_CREDITS:
                res = R.string.assistant_err_credits;
                break;
            case OpenRouterClient.ERR_RATE:
                res = R.string.assistant_err_rate;
                break;
            case OpenRouterClient.ERR_FLAGGED:
                res = R.string.assistant_err_flagged;
                break;
            case OpenRouterClient.ERR_NETWORK:
                res = R.string.assistant_err_network;
                break;
            case OpenRouterClient.ERR_EMPTY:
                res = R.string.assistant_err_empty;
                break;
            default:
                res = R.string.assistant_err_server;
                break;
        }
        String text = getString(res);
        return result.detail.isEmpty() ? text : text + "\n(" + result.detail + ")";
    }

    private void setBusy(boolean value) {
        busy = value;
        send.setAlpha(value ? .4f : 1f);
        status.setText(value ? getString(R.string.assistant_thinking)
                : getString(R.string.assistant_status_model, settings.getModel()));
    }

    private void addBubble(String text, boolean user, boolean error) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color(error ? R.color.state_error : R.color.nova_text));
        view.setTextSize(12);
        view.setLineSpacing(0f, 1.25f);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setFocusable(true);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setBackground(bubbleBackground(user));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = user ? Gravity.END : Gravity.START;
        params.topMargin = dp(8);
        if (user) params.leftMargin = dp(56);
        else params.rightMargin = dp(56);
        messages.addView(view, params);
        scrollToEnd();
    }

    private StateListDrawable bubbleBackground(boolean user) {
        int accent = accent();
        int fill = user ? (accent & 0x00FFFFFF) | 0x40000000 : color(R.color.glass_plate);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rounded(fill, accent, 2));
        states.addState(new int[]{}, rounded(fill, color(R.color.glass_card_stroke), 1));
        return states;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        drawable.setColor(fill);
        drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    /** One button per suggested app that is actually installed and launchable. */
    private void addOpenButtons(List<String> packages) {
        for (String pkg : packages) {
            if (launchIntentFor(pkg) == null) continue;
            Button button = new Button(this, null, 0, R.style.Lumora_Option);
            button.setText(getString(R.string.assistant_open_app, labelFor(pkg)));
            button.setTextColor(accent());
            button.setOnClickListener(v -> openApp(pkg));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
            params.gravity = Gravity.START;
            params.topMargin = dp(6);
            messages.addView(button, params);
        }
        scrollToEnd();
    }

    private void scrollToEnd() {
        scroll.post(() -> scroll.smoothScrollTo(0, messages.getBottom()));
    }

    // ------------------------------------------------------------- control

    /**
     * Runs TV actions. Actions that leave the panel open run now and get a status line; actions
     * that start or reveal something else close the assistant first and run a moment later. With no
     * closing action, the first app from {@code [[open:...]]} is opened.
     *
     * @return {@code true} when the assistant is closing
     */
    private boolean perform(List<TvAction> actions, List<String> packages) {
        List<TvAction> afterClose = new ArrayList<>();
        for (TvAction action : actions) {
            int check = TvController.precheck(this, action);
            if (check != TvController.OK) {
                addBubble(controlError(check), false, true);
                continue;
            }
            if (action.closesPanel()) {
                afterClose.add(action);
                continue;
            }
            int result = TvController.run(this, action);
            if (result == TvController.OK) addBubble("\u2713 " + actionLabel(action), false, false);
            else addBubble(controlError(result), false, true);
        }
        if (afterClose.isEmpty()) {
            if (packages.isEmpty() || launchIntentFor(packages.get(0)) == null) return false;
            openApp(packages.get(0));
            return true;
        }
        String locked = null;
        for (TvAction action : afterClose) {
            if (TvAction.SEARCH.equals(action.name) && preferences.isLocked(action.arg)) locked = action.arg;
        }
        Runnable close = () -> {
            for (TvAction action : afterClose) TvController.runLater(this, action);
            finish();
        };
        if (locked != null) LockPad.verify(this, preferences, getString(R.string.lock_sub_app, labelFor(locked)), close::run);
        else close.run();
        return true;
    }

    private String actionLabel(TvAction action) {
        switch (action.name) {
            case TvAction.PLAY:
                return getString(R.string.ctl_play);
            case TvAction.PAUSE:
                return getString(R.string.ctl_pause);
            case TvAction.PLAY_PAUSE:
                return getString(R.string.ctl_play_pause);
            case TvAction.NEXT:
                return getString(R.string.ctl_next);
            case TvAction.PREVIOUS:
                return getString(R.string.ctl_previous);
            case TvAction.STOP:
                return getString(R.string.ctl_stop);
            case TvAction.FORWARD:
                return getString(R.string.ctl_forward);
            case TvAction.REWIND:
                return getString(R.string.ctl_rewind);
            case TvAction.VOLUME_UP:
                return getString(R.string.ctl_volume_up, action.steps);
            case TvAction.VOLUME_DOWN:
                return getString(R.string.ctl_volume_down, action.steps);
            case TvAction.MUTE:
                return getString(R.string.ctl_mute);
            case TvAction.UNMUTE:
                return getString(R.string.ctl_unmute);
            case TvAction.THEME:
                return getString(R.string.ctl_theme, action.arg);
            default:
                return action.name;
        }
    }

    private String controlError(int result) {
        switch (result) {
            case TvController.NEEDS_QUICK:
                return getString(R.string.ctl_needs_quick);
            case TvController.VOLUME_FIXED:
                return getString(R.string.ctl_volume_fixed);
            case TvController.NOT_INSTALLED:
                return getString(R.string.ctl_not_installed);
            case TvController.UNSUPPORTED:
                return getString(R.string.ctl_unsupported);
            default:
                return getString(R.string.ctl_failed);
        }
    }

    // ---------------------------------------------------------------- apps

    /** Lumora opens the app so app locks and launch history apply as usual. */
    private void openApp(String pkg) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_LAUNCH_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private Intent launchIntentFor(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    private String labelFor(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return pkg;
        }
    }

    /** "Label (package)" for launchable, non-hidden apps. Runs on the executor. */
    private List<String> installedApps() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = preferences.getHiddenApps();
        Set<String> seen = new HashSet<>();
        List<String> apps = new ArrayList<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
                apps.add(info.loadLabel(pm) + " (" + pkg + ")");
                if (apps.size() >= MAX_APPS) return apps;
            }
        }
        return apps;
    }

    // -------------------------------------------------------------- other

    private void listen() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_hint));
        try {
            speech.launch(intent);
        } catch (RuntimeException exception) {
            mic.setVisibility(View.GONE);
        }
    }

    private void openAssistantSettings() {
        Runnable open = () -> startActivity(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_ASSISTANT));
        if (preferences.isSettingsLocked()) {
            LockPad.verify(this, preferences, getString(R.string.lock_sub_settings), open::run);
        } else {
            open.run();
        }
    }

    private int accent() {
        return preferences.getAccentColor(color(R.color.nova_accent));
    }

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
