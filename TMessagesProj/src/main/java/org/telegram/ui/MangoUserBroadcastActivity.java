package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private TextView status, progressStats, send, cancel, historyToggle;
    private ProgressBar progressBar;
    private LinearLayout historyList;
    private boolean showHistory;
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
        scroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(24));
        scroll.addView(content);
        TextView info = label(context, 14, Theme.key_windowBackgroundWhiteGrayText);
        info.setText(getString(R.string.MangoBroadcastInfo));
        content.addView(info, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 16));
        TextView header = label(context, 15, Theme.key_windowBackgroundWhiteBlueHeader);
        header.setText(getString(R.string.MangoBroadcastMessage));
        content.addView(header, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
        editor = new EditTextBoldCursor(context);
        editor.setTextSize(17);
        editor.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editor.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editor.setHint(getString(R.string.BroadcastToGroupsHint));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setMinLines(5);
        editor.setMaxLines(12);
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(1, Math.min(4096, getMessagesController().maxMessageLength)))});
        editor.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        editor.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), getThemedColor(Theme.key_windowBackgroundWhite)));
        editor.setText(draft);
        content.addView(editor, LayoutHelper.createLinear(-1, -2));
        LinearLayout progressCard = new LinearLayout(context);
        progressCard.setOrientation(LinearLayout.VERTICAL);
        progressCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        progressCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), getThemedColor(Theme.key_windowBackgroundWhite)));
        content.addView(progressCard, LayoutHelper.createLinear(-1, -2, 0, 18, 0, 14));
        TextView progressTitle = label(context, 16, Theme.key_windowBackgroundWhiteBlackText);
        progressTitle.setTypeface(AndroidUtilities.bold());
        progressTitle.setText("当前发送任务");
        progressCard.addView(progressTitle, LayoutHelper.createLinear(-1, -2));
        status = label(context, 13, Theme.key_windowBackgroundWhiteGrayText);
        status.setText("暂无进行中的任务");
        progressCard.addView(status, LayoutHelper.createLinear(-1, -2, 0, 7, 0, 8));
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressCard.addView(progressBar, LayoutHelper.createLinear(-1, 6, 0, 0, 0, 8));
        progressStats = label(context, 13, Theme.key_windowBackgroundWhiteGrayText);
        progressCard.addView(progressStats, LayoutHelper.createLinear(-1, -2));
        historyToggle = button(context, true);
        historyToggle.setText("查看群发任务记录");
        content.addView(historyToggle, LayoutHelper.createLinear(-1, 44, 0, 0, 0, 8));
        historyToggle.setOnClickListener(v -> {
            showHistory = !showHistory;
            historyList.setVisibility(showHistory ? View.VISIBLE : View.GONE);
            historyToggle.setText(showHistory ? "收起任务记录" : "查看群发任务记录");
            renderHistory();
        });
        historyList = new LinearLayout(context);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyList.setVisibility(View.GONE);
        content.addView(historyList, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 14));
        send = button(context, false);
        content.addView(send, LayoutHelper.createLinear(-1, 50));
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
        int total = manager.total();
        int processed = manager.processed();
        int skipped = manager.skipped();
        int remaining = Math.max(0, total - processed);
        int success = Math.max(0, processed - skipped);
        String stateLabel = state == MangoUserBroadcastManager.RUNNING ? "发送中"
                : state == MangoUserBroadcastManager.PAUSED ? "已暂停，可继续"
                : state == MangoUserBroadcastManager.DONE ? "已完成"
                : state == MangoUserBroadcastManager.CANCELLED ? "已停止" : "暂无进行中的任务";
        if (manager.isRunning() && !DailyMessageTasksManager.isConnected(currentAccount)) stateLabel = "等待账号连接";
        status.setText(stateLabel + (total > 0 ? " · " + processed + "/" + total + " 已处理" : ""));
        progressBar.setProgress(total <= 0 ? 0 : (int) (processed * 1000L / total));
        progressBar.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        progressStats.setText(total > 0
                ? "成功 " + success + "    跳过/失败 " + skipped + "    剩余 " + remaining
                : "任务开始后，这里会显示实时进度和发送结果");
        progressStats.setVisibility(View.VISIBLE);
        boolean busy = loader != null || manager.isRunning();
        send.setText(getString(loader != null ? R.string.Loading : state == MangoUserBroadcastManager.PAUSED
                ? R.string.MangoBroadcastResume : R.string.MangoBroadcastLoad));
        send.setEnabled(!busy); send.setAlpha(busy ? .45f : 1f);
        editor.setEnabled(!manager.hasPending() && loader == null);
        if (manager.hasPending() && !manager.message().contentEquals(editor.getText())) editor.setText(manager.message());
        cancel.setVisibility(manager.hasPending() ? View.VISIBLE : View.GONE);
        historyToggle.setVisibility(manager.history().isEmpty() ? View.GONE : View.VISIBLE);
        if (showHistory) renderHistory();
    }
    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        java.util.ArrayList<MangoUserBroadcastManager.TaskRecord> records =
                MangoUserBroadcastManager.getInstance(currentAccount).history();
        if (records.isEmpty()) {
            TextView empty = label(getParentActivity(), 13, Theme.key_windowBackgroundWhiteGrayText);
            empty.setText("还没有群发任务记录");
            historyList.addView(empty, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 8));
            return;
        }
        for (MangoUserBroadcastManager.TaskRecord record : records) {
            LinearLayout card = new LinearLayout(getParentActivity());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
            card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), getThemedColor(Theme.key_windowBackgroundWhite)));
            TextView title = label(getParentActivity(), 14, Theme.key_windowBackgroundWhiteBlackText);
            title.setTypeface(AndroidUtilities.bold());
            String stateText = record.state == MangoUserBroadcastManager.RUNNING ? "发送中"
                    : record.state == MangoUserBroadcastManager.PAUSED ? "已暂停"
                    : record.state == MangoUserBroadcastManager.DONE ? "已完成"
                    : record.state == MangoUserBroadcastManager.CANCELLED ? "已停止" : "未开始";
            title.setText(stateText + " · " + LocaleController.formatDateChat((int) (record.createdAt / 1000)));
            card.addView(title, LayoutHelper.createLinear(-1, -2));
            TextView stats = label(getParentActivity(), 13, Theme.key_windowBackgroundWhiteGrayText);
            int done = Math.min(record.total, record.processed);
            stats.setText("成功 " + Math.max(0, done - record.skipped) + " · 跳过/失败 " + record.skipped
                    + " · 剩余 " + Math.max(0, record.total - done) + " · 总计 " + record.total);
            card.addView(stats, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 0));
            TextView preview = label(getParentActivity(), 13, Theme.key_windowBackgroundWhiteGrayText);
            preview.setMaxLines(2);
            preview.setText(record.message == null ? "" : record.message);
            card.addView(preview, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 0));
            historyList.addView(card, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
        }
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
