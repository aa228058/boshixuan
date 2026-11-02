package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AutoReplyManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class AutoReplySettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ITEM_ID_ENABLE = 1;
    private static final int ITEM_ID_ANY_MESSAGE_ENABLE = 2;
    private static final int ITEM_ID_ANY_MESSAGE_PRESET = 3;
    private static final int ITEM_ID_ADD_RULE = 4;
    private static final int ITEM_ID_RULE = 5;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AutoReplySettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setSections();
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.quickRepliesUpdated);
        loadPresetMessageCounts();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.quickRepliesUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.quickRepliesUpdated) {
            loadPresetMessageCounts();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPresetMessageCounts();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AutoReplyManager manager = getAutoReplyManager();
        ArrayList<AutoReplyManager.Rule> rules = manager.getRules();

        items.add(UItem.asSwitch(ITEM_ID_ENABLE, getString(R.string.AutoReplyEnable)).setChecked(manager.isEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_ANY_MESSAGE_ENABLE, getString(R.string.AutoReplyAnyMessageEnable)).setChecked(manager.isAnyMessageEnabled()));
        items.add(UItem.asSettingsCell(
                ITEM_ID_ANY_MESSAGE_PRESET,
                getString(R.string.AutoReplyAnyMessageMessages),
                getAnyMessageSummary()
        ));
        items.add(UItem.asShadow(getString(R.string.AutoReplyEnableInfo)));

        items.add(UItem.asHeader(getString(R.string.AutoReplyRules)));
        items.add(UItem.asButton(ITEM_ID_ADD_RULE, "+ " + getString(R.string.AutoReplyAddRule)).accent());
        if (rules.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.AutoReplyNoRules)));
        } else {
            for (int i = 0; i < rules.size(); i++) {
                AutoReplyManager.Rule rule = rules.get(i);
                UItem item = UItem.asSettingsCell(
                        ITEM_ID_RULE,
                        rule.keyword,
                        getRuleSummary(rule)
                );
                item.intValue = rule.id;
                item.object = rule;
                items.add(item);
            }
        }
        items.add(UItem.asShadow(getString(R.string.AutoReplyRulesInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        AutoReplyManager manager = getAutoReplyManager();
        switch (item.id) {
            case ITEM_ID_ENABLE: {
                boolean enabled = !manager.isEnabled();
                manager.setEnabled(enabled);
                if (view instanceof TextCheckCell2) {
                    ((TextCheckCell2) view).setChecked(enabled);
                }
                refreshList();
                break;
            }
            case ITEM_ID_ANY_MESSAGE_ENABLE: {
                boolean enabled = !manager.isAnyMessageEnabled();
                manager.setAnyMessageEnabled(enabled);
                if (view instanceof TextCheckCell2) {
                    ((TextCheckCell2) view).setChecked(enabled);
                }
                refreshList();
                break;
            }
            case ITEM_ID_ANY_MESSAGE_PRESET:
                openAnyMessageQuickReplyEditor();
                break;
            case ITEM_ID_ADD_RULE:
                showRuleDialog(null);
                break;
            case ITEM_ID_RULE:
                openQuickReplyEditor((AutoReplyManager.Rule) item.object);
                break;
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id != ITEM_ID_RULE) {
            return false;
        }
        showDeleteRuleAlert((AutoReplyManager.Rule) item.object);
        return true;
    }

    private void showDeleteRuleAlert(AutoReplyManager.Rule rule) {
        Context context = getParentActivity();
        if (context == null || rule == null) {
            return;
        }
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.AutoReplyDeleteRule))
                .setMessage(LocaleController.formatString(R.string.AutoReplyDeleteRuleMessage, rule.keyword))
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    getAutoReplyManager().removeRule(rule.id);
                    refreshList();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
    }

    private void showRuleDialog(AutoReplyManager.Rule existingRule) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        EditTextBoldCursor keywordEditText = createEditText(context);
        keywordEditText.setHintText(getString(R.string.AutoReplyKeywordHint));
        keywordEditText.setSingleLine(true);
        keywordEditText.setMaxLines(1);
        keywordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        keywordEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        container.addView(keywordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        if (existingRule != null) {
            keywordEditText.setText(existingRule.keyword);
            keywordEditText.setSelection(keywordEditText.length());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(existingRule == null ? R.string.AutoReplyAddRule : R.string.AutoReplyEditRule));
        builder.setMessage(getString(R.string.AutoReplyEditRuleInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            String keyword = keywordEditText.getText().toString().trim();
            if (TextUtils.isEmpty(keyword)) {
                showRuleInputError("关键词不能为空，原规则及其快捷回复不会被删除。");
                return;
            }
            if (existingRule == null) {
                AutoReplyManager.Rule rule = getAutoReplyManager().addRule(keyword);
                if (rule == null) {
                    showRuleInputError("添加失败：该关键词可能已经存在，请更换关键词。");
                    return;
                }
                refreshList();
                openQuickReplyEditor(rule);
            } else {
                if (!getAutoReplyManager().updateRuleKeyword(existingRule.id, keyword)) {
                    showRuleInputError("修改失败：该关键词可能已被其他规则使用，请更换关键词。");
                    return;
                }
                refreshList();
            }
        });
        if (existingRule != null) {
            builder.setNeutralButton(getString(R.string.Delete), (dialog, which) -> {
                getAutoReplyManager().removeRule(existingRule.id);
                refreshList();
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);

        AlertDialog dialog = builder.create();
        showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            keywordEditText.requestFocus();
            AndroidUtilities.showKeyboard(keywordEditText);
        }, 120);
    }

    private void showRuleInputError(String message) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton(getString(R.string.OK), null)
                .show();
    }

    private EditTextBoldCursor createEditText(Context context) {
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        editText.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12));
        return editText;
    }

    private void openQuickReplyEditor(AutoReplyManager.Rule rule) {
        if (rule == null || TextUtils.isEmpty(rule.shortcut)) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", getUserConfig().getClientUserId());
        args.putInt("chatMode", ChatActivity.MODE_QUICK_REPLIES);
        args.putString("quick_reply", rule.shortcut);
        args.putString("quick_reply_title", rule.keyword);
        args.putBoolean("quick_reply_disable_rename", true);
        args.putBoolean("quick_reply_local_preset", true);
        args.putInt("quick_reply_local_id", AutoReplyManager.getLocalPresetTopicId(rule.id));
        ChatActivity chatActivity = new ChatActivity(args);
        chatActivity.setQuickReplyId(AutoReplyManager.getLocalPresetTopicId(rule.id));
        presentFragment(chatActivity);
    }

    private void openAnyMessageQuickReplyEditor() {
        Bundle args = new Bundle();
        args.putLong("user_id", getUserConfig().getClientUserId());
        args.putInt("chatMode", ChatActivity.MODE_QUICK_REPLIES);
        args.putString("quick_reply", AutoReplyManager.getAnyMessageShortcut());
        args.putString("quick_reply_title", getString(R.string.AutoReplyAnyMessageTitle));
        args.putBoolean("quick_reply_disable_rename", true);
        args.putBoolean("quick_reply_local_preset", true);
        args.putInt("quick_reply_local_id", AutoReplyManager.getAnyMessagePresetTopicId());
        ChatActivity chatActivity = new ChatActivity(args);
        chatActivity.setQuickReplyId(AutoReplyManager.getAnyMessagePresetTopicId());
        presentFragment(chatActivity);
    }

    private String getAnyMessageSummary() {
        int count = getAutoReplyManager().getAnyMessagePresetMessagesCount();
        if (count > 0) {
            return LocaleController.formatString(R.string.AutoReplyRuleSummary, count);
        }
        return getString(R.string.AutoReplyRuleNoMessages);
    }

    private String getRuleSummary(AutoReplyManager.Rule rule) {
        int presetMessagesCount = getAutoReplyManager().getPresetMessagesCount(rule.id);
        if (presetMessagesCount > 0) {
            return LocaleController.formatString(R.string.AutoReplyRuleSummary, presetMessagesCount);
        }
        if (rule.legacyReplies != null && !rule.legacyReplies.isEmpty()) {
            return LocaleController.formatString(R.string.AutoReplyRuleSummary, rule.legacyReplies.size());
        }
        return getString(R.string.AutoReplyRuleNoMessages);
    }

    private void loadPresetMessageCounts() {
        getAutoReplyManager().loadPresetMessageCounts(this::refreshList);
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private AutoReplyManager getAutoReplyManager() {
        return AutoReplyManager.getInstance(currentAccount);
    }
}
