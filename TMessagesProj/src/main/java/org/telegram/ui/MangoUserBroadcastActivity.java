package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DailyMessageTasksManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoBroadcastTargets;
import org.telegram.messenger.MangoUserBroadcastManager;
import org.telegram.messenger.MangoUserBroadcastService;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/** Explicit review, owner-bound confirmation and progress; opening the page never sends. */
public final class MangoUserBroadcastActivity extends BaseFragment {
    private EditTextBoldCursor editor;
    private TextView status, send, cancel;
    private LinearLayout statusCard;
    private MangoBroadcastTargets loader;
    private long owner;
    private boolean destroyed, resumed;
    private String draft = "";
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!current() || !resumed) return;
            updateStatus();
            AndroidUtilities.runOnUIThread(this, 1000);
        }
    };
    private boolean current() {
        return !destroyed && owner != 0 && UserConfig.getInstance(currentAccount).isClientActivated()
                && UserConfig.getInstance(currentAccount).getClientUserId() == owner;
    }
    @Override public boolean onFragmentCreate() {
        owner = getUserConfig().getClientUserId();
        return super.onFragmentCreate();
    }
    @Override public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoBroadcastTitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout infoCard = new LinearLayout(context);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        infoCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), getThemedColor(Theme.key_windowBackgroundWhite)));
        TextView infoTitle = label(context, 15, Theme.key_windowBackgroundWhiteBlueHeader);
        infoTitle.setTypeface(AndroidUtilities.bold());
        infoTitle.setText(getString(R.string.MangoBroadcastTitle));
        infoCard.addView(infoTitle, LayoutHelper.createLinear(-1, -2));
        TextView info = label(context, 13, Theme.key_windowBackgroundWhiteGrayText);
        info.setText(getString(R.string.MangoBroadcastInfo));
        info.setLineSpacing(AndroidUtilities.dp(2), 1f);
        infoCard.addView(info, LayoutHelper.createLinear(-1, -2, 0, 7, 0, 0));
        content.addView(infoCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 16));

        LinearLayout editorCard = new LinearLayout(context);
        editorCard.setOrientation(LinearLayout.VERTICAL);
        editorCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        editorCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), getThemedColor(Theme.key_windowBackgroundWhite)));
        TextView header = label(context, 15, Theme.key_windowBackgroundWhiteBlackText);
        header.setTypeface(AndroidUtilities.bold());
        header.setText(getString(R.string.MangoBroadcastMessage));
        editorCard.addView(header, LayoutHelper.createLinear(-1, -2));
        editor = new EditTextBoldCursor(context);
        editor.setTextSize(16);
        editor.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editor.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editor.setHint(getString(R.string.BroadcastToGroupsHint));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setMinLines(5);
        editor.setMaxLines(12);
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(1, Math.min(4096, getMessagesController().maxMessageLength)))});
        editor.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        editor.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getThemedColor(Theme.key_windowBackgroundGray)));
        editor.setText(draft);
        editorCard.addView(editor, LayoutHelper.createLinear(-1, -2, 0, 12, 0, 0));
        content.addView(editorCard, LayoutHelper.createLinear(-1, -2));

        statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        statusCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), getThemedColor(Theme.key_windowBackgroundWhite)));
        status = label(context, 13, Theme.key_windowBackgroundWhiteGrayText);
        status.setLineSpacing(AndroidUtilities.dp(2), 1f);
        statusCard.addView(status, LayoutHelper.createLinear(-1, -2));
        content.addView(statusCard, LayoutHelper.createLinear(-1, -2, 0, 14, 0, 0));
        statusCard.setVisibility(View.GONE);

        send = button(context, false);
        content.addView(send, LayoutHelper.createLinear(-1, 52, 0, 18, 0, 0));
        send.setOnClickListener(v -> review());
        cancel = button(context, true);
        cancel.setText(getString(R.string.Cancel));
        content.addView(cancel, LayoutHelper.createLinear(-1, 48, 0, 10, 0, 0));
        cancel.setOnClickListener(v -> {
            if (!current()) return;
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MangoBroadcastTitle)).setMessage(getString(R.string.MangoBroadcastCancelConfirm))
                    .setNegativeButton(getString(R.string.Back), null)
                    .setPositiveButton(getString(R.string.Cancel), (d, which) -> {
                        if (current()) { MangoUserBroadcastManager.getInstance(currentAccount).cancel(); updateStatus(); }
                    }).create());
        });
        fragmentView = scroll;
        updateStatus();
        return fragmentView;
    }
    private TextView label(Context context, int size, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size); view.setTextColor(getThemedColor(color));
        return view;
    }
    private TextView button(Context context, boolean secondary) {
        TextView view = label(context, 16, secondary ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_featuredStickers_buttonText);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(AndroidUtilities.bold());
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                getThemedColor(secondary ? Theme.key_windowBackgroundWhite : Theme.key_featuredStickers_addButton),
                getThemedColor(Theme.key_listSelector)));
        return view;
    }
    private void updateStatus() {
        if (status == null || !current()) return;
        MangoUserBroadcastManager manager = MangoUserBroadcastManager.getInstance(currentAccount);
        int state = manager.state();
        String text = manager.total() == 0 ? "" : LocaleController.formatString(R.string.MangoBroadcastProgress,
                manager.submitted(), manager.total(), manager.skipped());
        int message = state == MangoUserBroadcastManager.PAUSED ? R.string.MangoBroadcastPaused
                : state == MangoUserBroadcastManager.DONE ? R.string.MangoBroadcastCompleted
                : state == MangoUserBroadcastManager.CANCELLED ? R.string.MangoBroadcastCancelled
                : manager.isRunning() && !DailyMessageTasksManager.isConnected(currentAccount) ? R.string.MangoBroadcastWaiting : 0;
        if (message != 0) text += "\n" + getString(message);
        status.setText(text.trim());
        statusCard.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        boolean busy = loader != null || manager.isRunning();
        send.setText(getString(loader != null ? R.string.Loading : state == MangoUserBroadcastManager.PAUSED
                ? R.string.MangoBroadcastResume : R.string.MangoBroadcastLoad));
        send.setEnabled(!busy); send.setAlpha(busy ? .45f : 1f);
        editor.setEnabled(!manager.hasPending() && loader == null);
        if (manager.hasPending() && !manager.message().contentEquals(editor.getText())) editor.setText(manager.message());
        cancel.setVisibility(manager.hasPending() ? View.VISIBLE : View.GONE);
    }
    private void review() {
        if (!current() || loader != null || getParentActivity() == null) return;
        MangoUserBroadcastManager manager = MangoUserBroadcastManager.getInstance(currentAccount);
        if (manager.isRunning()) return;
        AndroidUtilities.hideKeyboard(editor);
        if (manager.hasPending()) {
            loadAndReview(true, manager.message());
            return;
        }
        final String text = editor.getText().toString().trim();
        if (text.isEmpty()) { error(getString(R.string.BroadcastToGroupsEmptyText)); return; }
        loadAndReview(false, text);
    }
    private void loadAndReview(boolean resume, String text) {
        MangoUserBroadcastManager manager = MangoUserBroadcastManager.getInstance(currentAccount);
        AlertDialog loading = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        loader = new MangoBroadcastTargets(currentAccount, (users, failure) -> {
            loader = null;
            loading.dismiss();
            if (!current() || isPaused()) return;
            updateStatus();
            if (failure != null) { error(getString(R.string.MangoBroadcastLoadFailed)); return; }
            if (resume) {
                if (!manager.hasPending() || manager.isRunning()) return;
                confirm(manager.total() - manager.processed(), text, () -> {
                    if (manager.resume()) launchService(); else error(getString(R.string.MangoBroadcastFailed));
                });
                return;
            }
            if (users.isEmpty()) { error(getString(R.string.MangoBroadcastNoTargets)); return; }
            confirm(users.size(), text, () -> {
                if (manager.start(text, users)) launchService(); else error(getString(R.string.MangoBroadcastFailed));
            });
        });
        loading.setOnCancelListener(d -> { if (loader != null) { loader.cancel(); loader = null; updateStatus(); } });
        showDialog(loading);
        if (resume) loader.loadBlockedOnly(); else loader.load();
        updateStatus();
    }
    private void confirm(int count, String text, Runnable action) {
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.MangoBroadcastReview))
                .setMessage(LocaleController.formatString(R.string.MangoBroadcastConfirm, count) + "\n\n" + text)
                .setPositiveButton(getString(R.string.Send), (dialog, which) -> {
                    if (!current() || isPaused()) return;
                    action.run(); updateStatus();
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }
    private void launchService() {
        if (!MangoUserBroadcastService.start()) {
            MangoUserBroadcastManager.getInstance(currentAccount).pause();
            error(getString(R.string.MangoBroadcastFailed));
        }
    }
    private void error(String text) { BulletinFactory.of(this).createErrorBulletin(text).show(); }
    @Override public void onResume() {
        super.onResume(); resumed = true;
        if (!current()) { finishFragment(); return; }
        AndroidUtilities.cancelRunOnUIThread(refresh); refresh.run();
    }
    @Override public void onPause() {
        resumed = false;
        if (editor != null) draft = editor.getText().toString();
        AndroidUtilities.cancelRunOnUIThread(refresh);
        if (loader != null) { loader.cancel(); loader = null; }
        super.onPause();
    }
    @Override public void onFragmentDestroy() {
        destroyed = true;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        if (loader != null) loader.cancel();
        super.onFragmentDestroy();
    }
}
