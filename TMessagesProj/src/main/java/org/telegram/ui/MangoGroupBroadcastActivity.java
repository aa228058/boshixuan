package org.telegram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoGroupBroadcastManager;
import org.telegram.messenger.MangoGroupBroadcastTaskStore;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashSet;

/** User-facing group broadcast composer and task manager. */
public final class MangoGroupBroadcastActivity extends BaseFragment implements DialogsActivity.DialogsActivityDelegate {
    private EditText messageField;
    private EditText intervalField;
    private TextView selectedInfo;
    private LinearLayout taskContainer;
    private final ArrayList<Long> selectedGroups = new ArrayList<>();
    private final ArrayList<String> selectedNames = new ArrayList<>();
    private boolean resumed;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!resumed || fragmentView == null) return;
            renderTasks();
            AndroidUtilities.runOnUIThread(this, 2000);
        }
    };

    @Override
    public View createView(Context context) {
        actionBar.setTitle("群组群发");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout compose = card(context);
        root.addView(compose, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 14));
        TextView title = label(context, "发送内容", 16, Theme.key_windowBackgroundWhiteBlackText, true);
        compose.addView(title, LayoutHelper.createLinear(-1, -2));
        messageField = new EditText(context);
        messageField.setTextSize(15);
        messageField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        messageField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        messageField.setHint("输入要发送到群组的消息");
        messageField.setGravity(Gravity.TOP | Gravity.START);
        messageField.setMinLines(4);
        messageField.setMaxLines(10);
        messageField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageField.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        messageField.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getThemedColor(Theme.key_windowBackgroundGray)));
        compose.addView(messageField, LayoutHelper.createLinear(-1, -2, 0, 10, 0, 0));

        TextView intervalTitle = label(context, "发送间隔（秒）", 14, Theme.key_windowBackgroundWhiteBlackText, true);
        compose.addView(intervalTitle, LayoutHelper.createLinear(-1, -2, 0, 14, 0, 0));
        intervalField = new EditText(context);
        intervalField.setText("10");
        intervalField.setSingleLine(true);
        intervalField.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalField.setTextSize(15);
        intervalField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        intervalField.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        intervalField.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), getThemedColor(Theme.key_windowBackgroundGray)));
        compose.addView(intervalField, LayoutHelper.createLinear(-1, 44, 0, 7, 0, 0));

        LinearLayout selectionActions = new LinearLayout(context);
        selectionActions.setOrientation(LinearLayout.HORIZONTAL);
        compose.addView(selectionActions, LayoutHelper.createLinear(-1, 46, 0, 14, 0, 0));
        TextView choose = button(context, "搜索并选择群组", false);
        selectionActions.addView(choose, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(44)));
        choose.setOnClickListener(v -> openGroupPicker());
        selectedInfo = label(context, "尚未选择群组", 13, Theme.key_windowBackgroundWhiteGrayText, false);
        compose.addView(selectedInfo, LayoutHelper.createLinear(-1, -2, 0, 9, 0, 0));

        TextView start = button(context, "确认并创建群发任务", false);
        compose.addView(start, LayoutHelper.createLinear(-1, 48, 0, 12, 0, 0));
        start.setOnClickListener(v -> confirmAndStart());

        LinearLayout tasks = card(context);
        root.addView(tasks, LayoutHelper.createLinear(-1, -2));
        TextView taskTitle = label(context, "群发任务与发送日志", 16, Theme.key_windowBackgroundWhiteBlackText, true);
        tasks.addView(taskTitle, LayoutHelper.createLinear(-1, -2));
        taskContainer = new LinearLayout(context);
        taskContainer.setOrientation(LinearLayout.VERTICAL);
        tasks.addView(taskContainer, LayoutHelper.createLinear(-1, -2, 0, 10, 0, 0));

        fragmentView = scroll;
        renderTasks();
        return fragmentView;
    }

    private LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(14), AndroidUtilities.dp(15), AndroidUtilities.dp(14));
        layout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), getThemedColor(Theme.key_windowBackgroundWhite)));
        return layout;
    }

    private TextView label(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getThemedColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView button(Context context, String value, boolean secondary) {
        TextView view = label(context, value, 15, secondary ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_featuredStickers_buttonText, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                getThemedColor(secondary ? Theme.key_windowBackgroundGray : Theme.key_featuredStickers_addButton),
                getThemedColor(Theme.key_listSelector)));
        return view;
    }

    private void openGroupPicker() {
        if (getParentActivity() == null) return;
        ArrayList<TLRPC.Chat> available = new ArrayList<>();
        for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) {
            if (dialog == null || dialog.id >= 0 || DialogObject.isFolderDialogId(dialog.id)) continue;
            TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
            if (chat == null || chat.left || chat.kicked || chat.deactivated) continue;
            if (org.telegram.messenger.ChatObject.isChannel(chat)
                    && !chat.megagroup && !chat.gigagroup) continue;
            available.add(chat);
        }
        available.sort((a, b) -> String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title)));

        LinearLayout panel = new LinearLayout(getParentActivity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16),
                AndroidUtilities.dp(18), AndroidUtilities.dp(10));

        LinearLayout header = new LinearLayout(getParentActivity());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(header, LayoutHelper.createLinear(-1, 42));
        TextView heading = label(getParentActivity(), "选择群组", 17,
                Theme.key_windowBackgroundWhiteBlackText, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView selectAll = button(getParentActivity(), "全选", false);
        header.addView(selectAll, LayoutHelper.createLinear(76, 36));

        EditText search = new EditText(getParentActivity());
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        search.setHint("搜索群组名称");
        search.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        search.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                getThemedColor(Theme.key_windowBackgroundGray)));
        panel.addView(search, LayoutHelper.createLinear(-1, 42, 0, 10, 0, 0));

        TextView count = label(getParentActivity(), "", 12,
                Theme.key_windowBackgroundWhiteGrayText, false);
        panel.addView(count, LayoutHelper.createLinear(-1, -2, 0, 9, 0, 7));

        ScrollView listScroll = new ScrollView(getParentActivity());
        listScroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(getParentActivity());
        rows.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        panel.addView(listScroll, LayoutHelper.createLinear(-1, 380));

        Runnable render = () -> {
            rows.removeAllViews();
            String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase();
            int visible = 0;
            for (TLRPC.Chat chat : available) {
                String name = chat.title == null ? "" : chat.title;
                if (!query.isEmpty() && !name.toLowerCase().contains(query)) continue;
                visible++;
                long dialogId = -chat.id;
                boolean selected = selectedGroups.contains(dialogId);
                LinearLayout row = new LinearLayout(getParentActivity());
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9),
                        AndroidUtilities.dp(12), AndroidUtilities.dp(9));
                int background = selected
                        ? getThemedColor(Theme.key_windowBackgroundWhiteBlueText)
                        : getThemedColor(Theme.key_windowBackgroundGray);
                row.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), background));
                TextView nameView = label(getParentActivity(), (selected ? "✓  " : "") + name, 14,
                        selected ? Theme.key_featuredStickers_buttonText
                                : Theme.key_windowBackgroundWhiteBlackText, selected);
                row.addView(nameView, LayoutHelper.createLinear(-1, -2));
                rows.addView(row, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6));
                row.setOnClickListener(v -> {
                    int index = selectedGroups.indexOf(dialogId);
                    if (index >= 0) {
                        selectedGroups.remove(index);
                        if (index < selectedNames.size()) selectedNames.remove(index);
                    } else {
                        selectedGroups.add(dialogId);
                        selectedNames.add(name);
                    }
                    render.run();
                });
            }
            count.setText("已选择 " + selectedGroups.size() + " 个群组 · 当前显示 " + visible + " 个");
        };
        selectAll.setOnClickListener(v -> {
            selectedGroups.clear();
            selectedNames.clear();
            String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase();
            for (TLRPC.Chat chat : available) {
                String name = chat.title == null ? "" : chat.title;
                if (!query.isEmpty() && !name.toLowerCase().contains(query)) continue;
                selectedGroups.add(-chat.id);
                selectedNames.add(name);
            }
            render.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        render.run();

        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setView(panel)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton("完成", (d, w) -> {
                    if (selectedInfo != null) selectedInfo.setText("已选择 " + selectedGroups.size() + " 个群组");
                }).show();
        roundDialog(dialog);
    }

    private void roundDialog(AlertDialog dialog) {
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(Theme.createRoundRectDrawable(
                    AndroidUtilities.dp(18), getThemedColor(Theme.key_windowBackgroundWhite)));
        }
    }

    @Override
    public boolean didSelectDialogs(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids,
            CharSequence comment, boolean param, boolean notify, int scheduleDate,
            int scheduleRepeatPeriod, TopicsFragment topicsFragment) {
        selectedGroups.clear();
        selectedNames.clear();
        HashSet<Long> unique = new HashSet<>();
        for (MessagesStorage.TopicKey key : dids) {
            if (key == null || key.dialogId >= 0 || !unique.add(key.dialogId)) continue;
            long chatId = -key.dialogId;
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat == null || chat.left || chat.kicked || chat.deactivated) continue;
            selectedGroups.add(key.dialogId);
            selectedNames.add(chat.title == null ? ("群组 " + chatId) : chat.title);
        }
        if (selectedInfo != null) selectedInfo.setText("已选择 " + selectedGroups.size() + " 个群组");
        return true;
    }

    private void confirmAndStart() {
        String text = messageField == null ? "" : messageField.getText().toString().trim();
        if (text.isEmpty()) { showMessage("请先输入群发内容"); return; }
        if (selectedGroups.isEmpty()) { showMessage("请先选择群组"); return; }
        int interval;
        try { interval = Integer.parseInt(intervalField.getText().toString().trim()); }
        catch (Exception e) { interval = 10; }
        interval = Math.max(1, Math.min(3600, interval));
        final int min = Math.max(1, interval - 2);
        final int max = Math.max(min, interval + 2);
        final int delay = interval;
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle("确认群发")
                .setMessage("将向 " + selectedGroups.size() + " 个群组发送以下内容：\n\n" + text
                        + "\n\n间隔约 " + delay + " 秒（随机浮动 2 秒）")
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton("创建任务", (dialog, which) -> {
                    long id = MangoGroupBroadcastManager.start(currentAccount, text,
                            new ArrayList<>(selectedGroups), new ArrayList<>(selectedNames), min, max);
                    if (id == 0) showMessage("创建群发任务失败");
                    else {
                        selectedGroups.clear();
                        selectedNames.clear();
                        if (selectedInfo != null) selectedInfo.setText("尚未选择群组");
                        renderTasks();
                        showMessage("群发任务已创建");
                    }
                }).show();
        roundDialog(dialog);
    }

    private void showMessage(String text) {
        if (getParentActivity() == null) return;
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider).setMessage(text)
                .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
        roundDialog(dialog);
    }

    private String stateName(int state) {
        switch (state) {
            case MangoGroupBroadcastTaskStore.SENDING: return "发送中";
            case MangoGroupBroadcastTaskStore.COMPLETED: return "处理完成";
            case MangoGroupBroadcastTaskStore.INTERRUPTED: return "中断";
            case MangoGroupBroadcastTaskStore.PAUSED: return "已暂停";
            case MangoGroupBroadcastTaskStore.CANCELLED: return "已取消";
            default: return "未知";
        }
    }

    private void renderTasks() {
        if (taskContainer == null) return;
        taskContainer.removeAllViews();
        ArrayList<MangoGroupBroadcastTaskStore.Task> tasks = MangoGroupBroadcastTaskStore.getTasks(currentAccount);
        if (tasks.isEmpty()) {
            taskContainer.addView(label(getParentActivity(), "暂无群发任务", 13, Theme.key_windowBackgroundWhiteGrayText, false),
                    LayoutHelper.createLinear(-1, -2));
            return;
        }
        for (MangoGroupBroadcastTaskStore.Task task : tasks) {
            LinearLayout item = new LinearLayout(getParentActivity());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(AndroidUtilities.dp(11), AndroidUtilities.dp(10), AndroidUtilities.dp(11), AndroidUtilities.dp(10));
            item.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getThemedColor(Theme.key_windowBackgroundGray)));
            taskContainer.addView(item, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 9));
            TextView heading = label(getParentActivity(), stateName(task.state) + " · 已完成 " + task.queued
                    + " · 失败 " + task.failed, 14, Theme.key_windowBackgroundWhiteBlackText, true);
            item.addView(heading, LayoutHelper.createLinear(-1, -2));
            ProgressBar progress = new ProgressBar(getParentActivity(), null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(Math.max(1, task.total));
            progress.setProgress(task.processed);
            progress.setProgressTintList(ColorStateList.valueOf(getThemedColor(Theme.key_windowBackgroundWhiteBlueText)));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(getThemedColor(Theme.key_windowBackgroundGray)));
            item.addView(progress, LayoutHelper.createLinear(-1, 6, 0, 8, 0, 0));
            TextView preview = label(getParentActivity(), task.message, 13, Theme.key_windowBackgroundWhiteGrayText, false);
            preview.setMaxLines(3);
            item.addView(preview, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 0));

            LinearLayout actions = new LinearLayout(getParentActivity());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            item.addView(actions, LayoutHelper.createLinear(-1, 38, 0, 7, 0, 0));
            if (task.state == MangoGroupBroadcastTaskStore.SENDING) {
                addAction(actions, "暂停", () -> {
                    MangoGroupBroadcastManager.pause(currentAccount, task.id);
                    renderTasks();
                });
            } else if (task.state == MangoGroupBroadcastTaskStore.PAUSED || task.state == MangoGroupBroadcastTaskStore.INTERRUPTED) {
                addAction(actions, "继续", () -> {
                    MangoGroupBroadcastManager.resume(currentAccount, task.id);
                    renderTasks();
                });
            }
            if (task.state != MangoGroupBroadcastTaskStore.COMPLETED && task.state != MangoGroupBroadcastTaskStore.CANCELLED) {
                addAction(actions, "取消任务", () -> {
                    AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                            .setMessage("确定取消这个群发任务吗？")
                            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                            .setPositiveButton("确认取消", (d, w) -> {
                                MangoGroupBroadcastManager.cancel(currentAccount, task.id);
                                renderTasks();
                            }).show();
                    roundDialog(dialog);
                });
            }
            String recentTime = "";
            if (!task.logs.isEmpty()) {
                int separator = task.logs.get(0).indexOf(" · ");
                recentTime = separator > 0 ? task.logs.get(0).substring(0, separator) : "";
            }
            TextView logs = label(getParentActivity(), "最近记录  " + (recentTime.isEmpty() ? "暂无" : recentTime)
                    + "   ·   " + stateName(task.state) + "   ·   查看全部", 13,
                    Theme.key_windowBackgroundWhiteBlueText, true);
            logs.setOnClickListener(v -> showTaskDetails(task));
            logs.setMinHeight(AndroidUtilities.dp(54));
            logs.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(logs, LayoutHelper.createLinear(-1, 54, 0, 7, 0, 0));
        }
    }

    private void showTaskDetails(MangoGroupBroadcastTaskStore.Task task) {
        if (getParentActivity() == null) return;
        LinearLayout content = new LinearLayout(getParentActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14),
                AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        TextView summary = label(getParentActivity(), stateName(task.state) + " · 已完成 "
                + task.queued + " · 失败 " + task.failed + " · 共 " + task.total + " 个群组",
                14, Theme.key_windowBackgroundWhiteBlackText, true);
        content.addView(summary, LayoutHelper.createLinear(-1, -2));
        ScrollView scroll = new ScrollView(getParentActivity());
        LinearLayout rows = new LinearLayout(getParentActivity());
        rows.setOrientation(LinearLayout.VERTICAL);
        if (task.logs.isEmpty()) {
            rows.addView(label(getParentActivity(), "暂无发送记录", 13,
                    Theme.key_windowBackgroundWhiteGrayText, false), LayoutHelper.createLinear(-1, -2));
        } else {
            for (String entry : task.logs) {
                boolean success = entry.contains("发送成功");
                boolean failed = entry.contains("失败") || entry.contains("调用异常");
                TextView logRow = label(getParentActivity(), entry, 13,
                        success ? Theme.key_windowBackgroundWhiteGreenText
                                : failed ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlackText,
                        false);
                logRow.setTextIsSelectable(true);
                logRow.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(9),
                        AndroidUtilities.dp(10), AndroidUtilities.dp(9));
                logRow.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8),
                        getThemedColor(Theme.key_windowBackgroundGray)));
                rows.addView(logRow, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6));
            }
        }
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, LayoutHelper.createLinear(-1, 420, 0, 12, 0, 0));
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle("群发任务详情")
                .setView(content)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
        roundDialog(dialog);
    }

    private void addAction(LinearLayout parent, String text, Runnable action) {
        TextView button = button(getParentActivity(), text, true);
        parent.addView(button, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(36), 1));
        button.setOnClickListener(v -> action.run());
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        refresh.run();
    }

    @Override public void onPause() {
        resumed = false;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        super.onPause();
    }

    @Override public void onFragmentDestroy() {
        resumed = false;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        super.onFragmentDestroy();
    }
}
