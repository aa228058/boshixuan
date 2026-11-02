package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AutoReplyManager;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.CustomDownloadPathHelper;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ExtensionMediaSettings;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.KeywordMessageFilterManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoUpdateManager;
import org.telegram.messenger.MangoGroupBroadcastTaskStore;
import org.telegram.messenger.MangoSettings;
import org.telegram.messenger.MangoLegacySettings;
import org.telegram.messenger.MangoFeatureRegistry;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageActionSettings;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.StarsController;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class ExtensionFeaturesActivity extends BaseFragment {

    private static final int REQUEST_CODE_CUSTOM_DOWNLOAD_PATH = 501;
    private static final int REQUEST_CODE_CUSTOM_VIDEO_WALLPAPER = 502;

    private static final int ITEM_ID_HIDE_ONLINE_STATUS = 1;
    private static final int ITEM_ID_HIDE_TYPING_STATUS = 2;
    private static final int ITEM_ID_HIDE_READ_STATUS = 3;
    private static final int ITEM_ID_ANTI_REVOKE = 4;
    private static final int ITEM_ID_FORCE_FORWARD = 5;
    private static final int ITEM_ID_UNLIMITED_PIN = 6;
    private static final int ITEM_ID_DIALOGS_TITLE_TEXT = 7;
    private static final int ITEM_ID_KEYWORD_FILTER = 8;
    private static final int ITEM_ID_UNREAD_DIALOGS_TAB = 9;
    private static final int ITEM_ID_AUTO_REPLY = 10;
    private static final int ITEM_ID_CONTINUE_SCROLL_CHANNEL_JUMP = 11;
    private static final int ITEM_ID_DOWNLOAD_SPEED = 12;
    private static final int ITEM_ID_CHINESE_READ_STATUS_TEXT = 13;
    private static final int ITEM_ID_UPLOAD_SPEED = 14;
    private static final int ITEM_ID_CUSTOM_DOWNLOAD_PATH = 15;
    private static final int ITEM_ID_HIDE_CHANNEL_SEND_AS = 16;
    private static final int ITEM_ID_DEFAULT_HIGH_QUALITY_MEDIA = 17;
    private static final int ITEM_ID_STICKER_MESSAGE_SIZE = 18;
    private static final int ITEM_ID_MANGO_UPDATE = 19;
    private static final int ITEM_ID_MESSAGE_TEXT_SIZE = 20;
    private static final int ITEM_ID_TABLET_FULL_WIDTH = 21;
    private static final int ITEM_ID_AUTO_TRANSLATE = 22;
    private static final int ITEM_ID_CUSTOM_STAR_BALANCE = 23;
    private static final int ITEM_ID_INPUT_BAR_TEXT = 24;
    private static final int ITEM_ID_LOCAL_VIRTUAL_GIFT_BUY = 25;
    private static final int ITEM_ID_CLEAR_LOCAL_VIRTUAL_GIFTS = 26;
    private static final int ITEM_ID_AUTO_REACTION = 27;
    private static final int ITEM_ID_AUTO_REACTION_EMOJI = 28;
    private static final int ITEM_ID_AUTO_REACTION_TARGET = 29;
    private static final int ITEM_ID_BROADCAST_TO_GROUPS = 30;
    private static final int ITEM_ID_CHANNEL_FEED = 31;
    private static final int ITEM_ID_CONTACTS_TAB = 32;
    private static final int ITEM_ID_UNLIMITED_CHANNEL_JOIN = 33;
    private static final int ITEM_ID_BLOCKED_USERS = 34;
    private static final int ITEM_ID_TABLET_WIDTH_RATIO = 35;
    private static final int ITEM_ID_DIALOG_ROW_HEIGHT = 36;
    private static final int ITEM_ID_TRANSLATION_DISPLAY_MODE = 37;
    private static final int ITEM_ID_SEND_MP3_AS_VOICE = 38;
    private static final int ITEM_ID_CUSTOM_VIDEO_WALLPAPER = 39;
    private static final int ITEM_ID_UNLIMITED_DIALOG_FILTERS = 40;
    private static final int ITEM_ID_LOCAL_HOLD_VERIFICATION = 41;
    private static final int ITEM_ID_AUTO_REACTION_USERS = 42;
    private static final int ITEM_ID_AUDIO_AUTO_PLAY_NEXT = 43;
    private static final int ITEM_ID_PERSONAL_WALLPAPERS = 44;
    private static final int ITEM_ID_DAILY_MESSAGE_TASKS = 45;
    private static final int ITEM_ID_PRESENCE_SIMULATION = 46;
    private static final int ITEM_ID_MESSAGE_MANAGEMENT_TAB = 47;
    private static final int ITEM_ID_MESSAGE_MANAGEMENT = 48;
    private static final int ITEM_ID_MESSAGE_ACTION_SETTINGS = 49;
    private static final int ITEM_ID_SAVE_RECALLED = 50;
    private static final int ITEM_ID_RECALLED_ARCHIVE = 51;
    private static final int ITEM_ID_MESSAGE_ECHO = 52;
    private static final int ITEM_ID_SCAM_PROTECTION = 53;
    private static final int ITEM_ID_INTERACTION_STATISTICS = 54;
    private static final int ITEM_ID_MESSAGE_MEMORIAL = 55;
    private static final int ITEM_ID_DUPLICATE_FILES = 56;
    private static final int ITEM_ID_BATCH_LEAVE = 57;
    private static final int ITEM_ID_QUICK_FORWARD_ACCOUNT = 58;
    private static final int ITEM_UNIFIED_TAB = 85, ITEM_UNIFIED_INBOX = 86, ITEM_RESTRICTION_TITLE = 87, ITEM_DOWNLOAD_RETENTION = 88;
    private static final int ITEM_PERFORMANCE_MONITOR = 89, ITEM_DOWNLOAD_SPEED_HUD = 90;
    private static final int ITEM_RANDOM_REPLY_GROUPS = 91;
    private static final int ITEM_USER_BROADCAST = 92;
    private static final int ITEM_SYNC_SWITCHES = 93, ITEM_SECURITY_CHECK = 94, ITEM_GROUP_BROADCAST_TASKS = 95;
    private static final int ITEM_COMMON_CLEAR_CACHE = 20001, ITEM_COMMON_PROXY = 20002,
            ITEM_COMMON_ACCOUNT = 20003, ITEM_COMMON_PRIVACY = 20004;

    private static final int ITEM_HISTORY = 80, ITEM_RESET_ALL = 81, ITEM_NOTIFICATION_RULES = 82, ITEM_SCAM_USERS = 83, ITEM_BUILTIN_THEMES = 84;
    private UniversalRecyclerView listView;
    private int category = MangoFeatureRegistry.ALL;
    private String searchQuery = "";
    private ProfileActivity.SearchAdapter standardSearch;
    private EditTextBoldCursor searchField;
    private ImageView clearSearchButton;
    private ScrollSlidingTextTabStrip categoryTabs;

    private AlertDialog updateProgressDialog;
    private final MangoUpdateManager.Listener mangoUpdateListener = this::onMangoUpdateStateChanged;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.ExtensionFeatures));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // A measured vertical header keeps search, categories and results in separate
        // rows on phones, tablets and large-font layouts (no fixed overlay offset).
        LinearLayout contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setFocusableInTouchMode(true);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        MangoLegacySettings.initialize(currentAccount);
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        top.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        FrameLayout searchContainer = new FrameLayout(context);
        searchContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48));
        searchParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        top.addView(searchContainer, searchParams);

        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchContainer.addView(searchIcon, LayoutHelper.createFrame(44, 48,
                Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

        searchField = new EditTextBoldCursor(context);
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        searchField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
        searchField.setHint(getString(R.string.MangoCommandPanelSearchHint));
        searchField.setBackground(null);
        searchField.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        searchField.setPadding(0, 0, 0, 0);
        searchField.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.FILL, 44, 0, 44, 0));

        clearSearchButton = new ImageView(context);
        clearSearchButton.setImageResource(R.drawable.ic_close_white);
        clearSearchButton.setScaleType(ImageView.ScaleType.CENTER);
        clearSearchButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        clearSearchButton.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourceProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
        clearSearchButton.setContentDescription(getString(R.string.Clear));
        clearSearchButton.setVisibility(View.GONE);
        clearSearchButton.setOnClickListener(view -> {
            searchField.setText("");
            searchField.requestFocus();
            AndroidUtilities.showKeyboard(searchField);
        });
        searchContainer.addView(clearSearchButton, LayoutHelper.createFrame(44, 48,
                Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        categoryTabs = new ScrollSlidingTextTabStrip(context, resourceProvider);
        categoryTabs.setColors(Theme.key_windowBackgroundWhiteBlueText, Theme.key_windowBackgroundWhiteBlueText,
                Theme.key_windowBackgroundWhiteGrayText, Theme.key_listSelector);
        categoryTabs.setUseMinimalWidth(true);
        for (int i = 0; i < MangoFeatureRegistry.CATEGORY_LABELS.length; i++) {
            categoryTabs.addTextTab(i, getString(MangoFeatureRegistry.CATEGORY_LABELS[i]));
        }
        categoryTabs.finishAddingTabs();
        categoryTabs.selectTabWithId(category, 1f);
        categoryTabs.setTabTextStyle(14, 10);
        categoryTabs.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override public void onPageSelected(int page, boolean forward) {
                category = page;
                searchField.setText("");
                searchField.clearFocus();
                AndroidUtilities.hideKeyboard(searchField);
                categoryTabs.selectTabWithId(page, 1f);
                if (listView != null) { listView.adapter.update(false); listView.scrollToPosition(0); }
            }
            @Override public void onPageScrolled(float progress) {}
        });
        FrameLayout categoryTabsFrame = new FrameLayout(context);
        categoryTabsFrame.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        categoryTabsFrame.addView(categoryTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        top.addView(categoryTabsFrame, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));
        contentView.addView(top, new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
        View headerDivider = new View(context);
        headerDivider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
        contentView.addView(headerDivider, new LinearLayout.LayoutParams(-1, 1));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        contentView.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));
        standardSearch = new ProfileActivity.SearchAdapter(this, context) {
            @Override public void notifyDataSetChanged() {
                if (listView != null) listView.adapter.update(true);
            }
        };
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                clearSearchButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                standardSearch.search(searchQuery);
                if (listView != null) { listView.adapter.update(false); listView.scrollToPosition(0); }
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        searchField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                AndroidUtilities.hideKeyboard(searchField);
                searchField.clearFocus();
                return true;
            }
            return false;
        });
        searchField.setText(searchQuery);
        contentView.requestFocus();
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        MangoUpdateManager.addListener(mangoUpdateListener);
        MangoLegacySettings.initialize(currentAccount);
        MangoLegacySettings.capture(currentAccount);
        AutoReplyManager.getInstance(currentAccount).loadPresetMessageCounts(() -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MangoUpdateManager.removeListener(mangoUpdateListener);
    }

    private int categoryFor(int id) {
        MangoFeatureRegistry.Feature feature = MangoFeatureRegistry.find(id);
        if (feature != null) return feature.category;
        switch (id) {
            case 1: case 2: case 3: case 4: case 8: case 34: case 41: case 50: case 51: case 53: case ITEM_SCAM_USERS: case ITEM_SECURITY_CHECK:
                return MangoFeatureRegistry.PRIVACY;
            case 12: case 14: case 15: case 17: case 18: case 38: case 43: case 56: case ITEM_DOWNLOAD_RETENTION: case ITEM_DOWNLOAD_SPEED_HUD:
                return MangoFeatureRegistry.MEDIA;
            case 10: case 27: case 28: case 29: case 30: case 42: case 45: case 46: case 52: case ITEM_RANDOM_REPLY_GROUPS: case ITEM_USER_BROADCAST: case ITEM_GROUP_BROADCAST_TASKS:
                return MangoFeatureRegistry.AUTOMATION;
            case 7: case 9: case 13: case 20: case 21: case 23: case 24: case 25: case 26: case 31: case 32: case 35: case 36: case 39: case 44: case 47: case ITEM_BUILTIN_THEMES: case ITEM_UNIFIED_TAB: case ITEM_UNIFIED_INBOX: case ITEM_RESTRICTION_TITLE:
                return MangoFeatureRegistry.APPEARANCE;
            case 19: case 54: case 55: case 57: case ITEM_HISTORY: case ITEM_RESET_ALL: case ITEM_PERFORMANCE_MONITOR: case ITEM_SYNC_SWITCHES:
                return MangoFeatureRegistry.TOOLS;
            default: return MangoFeatureRegistry.CHAT;
        }
    }

    private boolean matches(CharSequence text) {
        return text != null && text.toString().toLowerCase(java.util.Locale.ROOT).contains(searchQuery);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ArrayList<UItem> all = new ArrayList<>();
        fillLegacyItems(all, adapter);
        all.removeIf(item -> item.id <= 0);
        for (MangoFeatureRegistry.Feature feature : MangoFeatureRegistry.FEATURES) {
            UItem item = UItem.asSwitch(feature.id, getString(feature.title)).setChecked(MangoSettings.getBoolean(currentAccount, feature.key));
            item.object = feature;
            all.add(item);
            if ("auto_read_random_reply".equals(feature.key)) {
                all.add(UItem.asButton(ITEM_RANDOM_REPLY_GROUPS, R.drawable.msg_groups,
                        getString(R.string.MangoAutoReadReplyGroups)));
            }
        }
        all.add(UItem.asButton(ITEM_USER_BROADCAST, R.drawable.msg_send,
                getString(R.string.MangoBroadcastTitle), getString(R.string.MangoBroadcastInfo)));
        all.add(UItem.asButton(ITEM_NOTIFICATION_RULES, R.drawable.msg_notifications, getString(R.string.MangoNotificationRules)));
        all.add(UItem.asButton(ITEM_SCAM_USERS, R.drawable.msg_info, getString(R.string.MangoScamUsers)));
        all.add(UItem.asButton(ITEM_BUILTIN_THEMES, R.drawable.msg_theme, getString(R.string.MangoBuiltinThemes)));
        all.add(UItem.asSwitch(ITEM_SYNC_SWITCHES, getString(R.string.MangoSyncSwitches))
                .setChecked(org.telegram.messenger.MangoSettingsSync.isEnabled()));
        all.add(UItem.asButton(ITEM_SECURITY_CHECK, R.drawable.msg_secret, getString(R.string.MangoSecurityCheck)));
        all.add(UItem.asButton(ITEM_HISTORY, R.drawable.msg_recent, getString(R.string.MangoRecentSettings)));
        all.add(UItem.asButton(ITEM_RESET_ALL, R.drawable.msg_delete, getString(R.string.MangoResetSettings)).red());
        if (!TextUtils.isEmpty(searchQuery)) {
            items.add(UItem.asHeader(getString(R.string.MangoSearchResults)));
            for (UItem item : all) {
                MangoFeatureRegistry.Feature feature = MangoFeatureRegistry.find(item.id);
                if (matches(item.text) || feature != null && (matches(feature.key) || matches(getString(feature.description)))) {
                    items.add(item);
                    items.add(UItem.asShadow(getString(MangoFeatureRegistry.CATEGORY_LABELS[categoryFor(item.id)])));
                }
            }
            for (MangoSettingsSearchIndex.Entry entry : MangoSettingsSearchIndex.entries()) {
                if (matches(getString(entry.title)) || matches(entry.keywords)) {
                    UItem item = UItem.asSettingsCell(entry.destination, getString(entry.title),
                            LocaleController.formatString(R.string.MangoSearchPage, getString(entry.pageTitle)));
                    item.object = entry;
                    items.add(item);
                }
            }
            if (standardSearch != null) standardSearch.fillItems(items);
            if (items.size() == 1) items.add(UItem.asShadow(getString(R.string.MangoSearchEmpty)));
            return;
        }
        // Surface frequently used features first, without duplicating their rows
        // farther down the All list. Category tabs still show their full catalog.
        HashSet<Integer> promotedIds = new HashSet<>();
        if (category == MangoFeatureRegistry.ALL) {
            items.add(UItem.asHeader(getString(R.string.MangoCommandPanelCommonActions)));
            addPromotedItem(items, all, promotedIds, ITEM_ID_DOWNLOAD_SPEED);
            addPromotedItem(items, all, promotedIds, ITEM_ID_UPLOAD_SPEED);
            addPromotedItem(items, all, promotedIds, ITEM_ID_KEYWORD_FILTER);
            items.add(UItem.asButton(ITEM_COMMON_CLEAR_CACHE, R.drawable.msg_delete, getString(R.string.ClearCache)));
            items.add(UItem.asButton(ITEM_COMMON_PROXY, R.drawable.msg2_data, getString(R.string.ProxySettings)));
            items.add(UItem.asButton(ITEM_COMMON_ACCOUNT, R.drawable.msg_contacts,
                    getString(R.string.MangoCommandPanelAccountSummary)));
            items.add(UItem.asButton(ITEM_COMMON_PRIVACY, R.drawable.msg_secret, getString(R.string.PrivacySettings)));
            items.add(UItem.asShadow(null));
        }
        for (int group = 1; group < MangoFeatureRegistry.CATEGORY_LABELS.length; group++) {
            if (category != MangoFeatureRegistry.ALL && category != group) continue;
            items.add(UItem.asHeader(getString(MangoFeatureRegistry.CATEGORY_LABELS[group])));
            for (UItem item : all) {
                if (categoryFor(item.id) != group || promotedIds.contains(item.id)) continue;
                items.add(item);
                MangoFeatureRegistry.Feature feature = MangoFeatureRegistry.find(item.id);
                if (feature != null) items.add(UItem.asShadow(getString(feature.description)));
                else if (item.id == ITEM_SYNC_SWITCHES) items.add(UItem.asShadow(getString(R.string.MangoSyncSwitchesInfo)));
            }
            items.add(UItem.asShadow(null));
        }
    }

    private void addPromotedItem(ArrayList<UItem> items, ArrayList<UItem> all,
                                 HashSet<Integer> promotedIds, int id) {
        for (UItem item : all) {
            if (item.id == id) {
                items.add(item);
                promotedIds.add(id);
                return;
            }
        }
    }

    private void confirmReset() {
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.MangoResetFirst)).setMessage(getString(R.string.MangoResetFirstInfo))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.MangoResetContinue), (first, which) ->
                        AndroidUtilities.runOnUIThread(() -> showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                                .setTitle(getString(R.string.MangoResetSecond)).setMessage(getString(R.string.MangoResetSecondInfo))
                                .setNegativeButton(getString(R.string.Cancel), null)
                                .setPositiveButton(getString(R.string.MangoResetNow), (second, button) -> {
                                    MangoLegacySettings.reset(currentAccount);
                                    listView.adapter.update(false);
                                    Toast.makeText(getParentActivity(), getString(R.string.MangoResetDone), Toast.LENGTH_LONG).show();
                                }).create()), 150)).create());
    }

    private void fillLegacyItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        MessagesController controller = getMessagesController();

        items.add(UItem.asHeader(getString(R.string.ExtensionToolsHeader)));
        items.add(UItem.asSwitch(ITEM_ID_AUDIO_AUTO_PLAY_NEXT, getString(R.string.ExtensionAudioAutoPlayNext))
                .setChecked(ExtensionMediaSettings.isAutoPlayNextEnabled()));
        items.add(UItem.asShadow(getString(R.string.ExtensionAudioAutoPlayNextInfo)));
        items.add(UItem.asButton(ITEM_ID_PERSONAL_WALLPAPERS, R.drawable.msg_background,
                getString(R.string.ExtensionPersonalWallpapers)));
        items.add(UItem.asButton(ITEM_ID_DAILY_MESSAGE_TASKS, R.drawable.msg_send,
                getString(R.string.ExtensionDailyMessages)));
        items.add(UItem.asSwitch(ITEM_ID_QUICK_FORWARD_ACCOUNT, getString(R.string.ExtensionQuickForwardAccount))
                .setChecked(MangoSettings.getBoolean(currentAccount, "mango_quick_forward_account_selection", false)));
        items.add(UItem.asShadow(getString(R.string.ExtensionQuickForwardAccountInfo)));
        items.add(UItem.asButton(ITEM_ID_PRESENCE_SIMULATION, R.drawable.msg_customize,
                getString(R.string.ExtensionPresence)));
        items.add(UItem.asSwitch(ITEM_ID_MESSAGE_MANAGEMENT_TAB, getString(R.string.MessageManagementBottomTab))
                .setChecked(MessageActionSettings.isCenterEnabled(currentAccount)));
        items.add(UItem.asButton(ITEM_ID_MESSAGE_MANAGEMENT, R.drawable.msg_select,
                getString(R.string.MessageManagementTitle)));
        items.add(UItem.asButton(ITEM_ID_MESSAGE_ACTION_SETTINGS, R.drawable.msg_customize,
                getString(R.string.ExtensionMessageActions)));
        items.add(UItem.asSwitch(ITEM_UNIFIED_TAB, getString(R.string.MangoUnifiedBottomTab))
                .setChecked(CrossAccountInboxActivity.isEnabled(currentAccount)));
        if (CrossAccountInboxActivity.isEnabled(currentAccount)) {
            items.add(UItem.asButton(ITEM_UNIFIED_INBOX, R.drawable.msg_discussion, getString(R.string.MangoUnifiedInbox)));
        }
        items.add(UItem.asButton(ITEM_RESTRICTION_TITLE, R.drawable.msg_contacts_name,
                getString(R.string.MangoRestrictionTitleSetting), LocaleController.getCustomAppName()));
        items.add(UItem.asButton(ITEM_DOWNLOAD_RETENTION, R.drawable.msg_clear,
                getString(R.string.DownloadRetentionTitle)));
        items.add(UItem.asButton(ITEM_PERFORMANCE_MONITOR, R.drawable.msg_stats,
                getString(R.string.MangoPerformanceTitle), getString(R.string.MangoPerformanceSummary)));
        items.add(UItem.asSwitch(ITEM_DOWNLOAD_SPEED_HUD, getString(R.string.MangoDownloadSpeedHudTitle))
                .setChecked(org.telegram.messenger.MangoSettings.getBoolean(currentAccount, "mango_download_speed_hud", false)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asSwitch(ITEM_ID_SAVE_RECALLED, getString(R.string.V4RecalledSave))
                .setChecked(org.telegram.messenger.RecalledMessagesStore.isEnabled(currentAccount)));
        items.add(UItem.asButton(ITEM_ID_RECALLED_ARCHIVE, R.drawable.msg_archive, getString(R.string.V4RecalledArchive)));
        items.add(UItem.asShadow(getString(R.string.V4RecalledInfo)));
        items.add(UItem.asButton(ITEM_ID_SCAM_PROTECTION, R.drawable.msg_info, getString(R.string.V4ScamEntry)));
        items.add(UItem.asButton(ITEM_ID_MESSAGE_ECHO, R.drawable.msg_send, getString(R.string.V4MessageEcho)));
        items.add(UItem.asButton(ITEM_ID_INTERACTION_STATISTICS, R.drawable.msg_stats, getString(R.string.V4StatisticsEntry)));
        items.add(UItem.asButton(ITEM_ID_MESSAGE_MEMORIAL, R.drawable.msg_archive, getString(R.string.V4MemorialEntry)));
        items.add(UItem.asButton(ITEM_ID_DUPLICATE_FILES, R.drawable.msg_delete, getString(R.string.V4DuplicateEntry)));
        items.add(UItem.asButton(ITEM_ID_BATCH_LEAVE, R.drawable.msg_leave, getString(R.string.V4BatchLeaveEntry)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asSwitch(ITEM_ID_HIDE_ONLINE_STATUS, getString(R.string.HideOnlineStatus)).setChecked(controller.isHideOnlineStatusEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_HIDE_TYPING_STATUS, getString(R.string.HideTypingStatus)).setChecked(controller.isHideTypingStatusEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_HIDE_READ_STATUS, getString(R.string.HideReadStatus)).setChecked(controller.isHideReadStatusEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_ANTI_REVOKE, getString(R.string.AntiRevoke)).setChecked(controller.isAntiRevokeEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_FORCE_FORWARD, getString(R.string.ForceForward)).setChecked(controller.isForceForwardEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_UNLIMITED_PIN, getString(R.string.UnlimitedPin)).setChecked(controller.isUnlimitedPinEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_UNREAD_DIALOGS_TAB, getString(R.string.UnreadDialogsTab)).setChecked(controller.isUnreadDialogsTabEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_CONTINUE_SCROLL_CHANNEL_JUMP, getString(R.string.ContinueScrollChannelJump)).setChecked(controller.isContinueScrollChannelJumpEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_CHINESE_READ_STATUS_TEXT, getString(R.string.ChineseReadStatusText)).setChecked(controller.isChineseReadStatusTextEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_HIDE_CHANNEL_SEND_AS, getString(R.string.HideChannelSendAsTitle)).setChecked(SharedConfig.isHideChannelSendAsEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_DEFAULT_HIGH_QUALITY_MEDIA, getString(R.string.DefaultHighQualityMediaTitle)).setChecked(SharedConfig.isDefaultHighQualityMediaEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_TABLET_FULL_WIDTH, getString(R.string.TabletFullWidthTitle)).setChecked(SharedConfig.isTabletFullWidthEnabled()));
        items.add(UItem.asButton(
                ITEM_ID_TABLET_WIDTH_RATIO,
                R.drawable.msg_customize,
                getString(R.string.TabletWidthRatioTitle),
                SharedConfig.getTabletWidthRatioText()
        ));
        items.add(UItem.asButton(
                ITEM_ID_DIALOG_ROW_HEIGHT,
                R.drawable.msg_customize,
                getString(R.string.DialogRowHeightTitle),
                SharedConfig.getDialogRowHeightText()
        ));
        items.add(UItem.asSwitch(ITEM_ID_CONTACTS_TAB, getString(R.string.ContactsTabTitle)).setChecked(getUserConfig().showContactsTab));
        items.add(UItem.asSwitch(ITEM_ID_CHANNEL_FEED, getString(R.string.ChannelFeedTitle)).setChecked(SharedConfig.isChannelFeedEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_UNLIMITED_CHANNEL_JOIN, getString(R.string.UnlimitedChannelJoinTitle)).setChecked(SharedConfig.isUnlimitedChannelJoinEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_UNLIMITED_DIALOG_FILTERS, getString(R.string.UnlimitedDialogFiltersTitle)).setChecked(SharedConfig.isUnlimitedDialogFiltersEnabled()));
        items.add(UItem.asSwitch(ITEM_ID_LOCAL_HOLD_VERIFICATION, getString(R.string.LocalHoldVerificationTitle)).setChecked(SharedConfig.isLocalHoldVerificationEnabled()));
        items.add(UItem.asButton(
                ITEM_ID_TRANSLATION_DISPLAY_MODE,
                R.drawable.msg_translate,
                getString(R.string.TranslationDisplayModeTitle),
                SharedConfig.getTranslationDisplayModeText()
        ));
        items.add(UItem.asSwitch(ITEM_ID_AUTO_REACTION, getString(R.string.AutoReactionTitle)).setChecked(SharedConfig.isAutoReactionEnabled()));
        items.add(UItem.asButton(
                ITEM_ID_AUTO_REACTION_EMOJI,
                R.drawable.msg_reactions,
                getString(R.string.AutoReactionEmojiTitle),
                SharedConfig.getAutoReactionEmoji()
        ));
        items.add(UItem.asButton(
                ITEM_ID_AUTO_REACTION_TARGET,
                R.drawable.msg_select,
                getString(R.string.AutoReactionTargetTitle),
                getAutoReactionTargetText()
        ));
        HashSet<Long> autoReactionUsers = SharedConfig.getAutoReactionUserIds(currentAccount);
        items.add(UItem.asButton(
                ITEM_ID_AUTO_REACTION_USERS,
                R.drawable.msg_contacts,
                getString(R.string.AutoReactionUsersTitle),
                autoReactionUsers.isEmpty() ? getString(R.string.AutoReactionUsersAll) : LocaleController.formatString(R.string.AutoReactionUsersSelected, autoReactionUsers.size())
        ));
        items.add(UItem.asButton(
                ITEM_ID_BROADCAST_TO_GROUPS,
                R.drawable.msg_send,
                getString(R.string.BroadcastToGroupsTitle),
                getString(R.string.BroadcastToGroupsSummary)
        ));
        items.add(UItem.asButton(ITEM_GROUP_BROADCAST_TASKS, R.drawable.msg_recent,
                "群发任务进度", "查看正在发送的任务和历史发送记录"));
        items.add(UItem.asSwitch(ITEM_ID_LOCAL_VIRTUAL_GIFT_BUY, getString(R.string.LocalVirtualGiftBuyTitle)).setChecked(SharedConfig.isLocalVirtualGiftBuyEnabled()));
        items.add(UItem.asButton(
                ITEM_ID_DOWNLOAD_SPEED,
                R.drawable.avd_speed,
                getString(R.string.DownloadSpeedBoostTitle),
                SharedConfig.getDownloadSpeedMultiplierText()
        ));
        items.add(UItem.asButton(
                ITEM_ID_UPLOAD_SPEED,
                R.drawable.msg_premium_uploads,
                getString(R.string.UploadSpeedBoostTitle),
                SharedConfig.getUploadSpeedMultiplierText()
        ));
        items.add(UItem.asButton(
                ITEM_ID_MANGO_UPDATE,
                R.drawable.msg_mini_download,
                getString(R.string.MangoUpdateTitle),
                getMangoUpdateSummary()
        ));
        items.add(UItem.asButton(
                ITEM_ID_CUSTOM_DOWNLOAD_PATH,
                R.drawable.menu_storage_path,
                getString(R.string.CustomDownloadPathTitle),
                SharedConfig.getCustomDownloadPathText()
        ));
        items.add(UItem.asSwitch(ITEM_ID_SEND_MP3_AS_VOICE, getString(R.string.SendMp3AsVoiceTitle)).setChecked(SharedConfig.isSendMp3AsVoiceEnabled()));
        items.add(UItem.asButton(
                ITEM_ID_CUSTOM_VIDEO_WALLPAPER,
                R.drawable.msg_background,
                getString(R.string.CustomVideoWallpaperTitle),
                SharedConfig.getCustomVideoWallpaperText()
        ));
        items.add(UItem.asButton(
                ITEM_ID_STICKER_MESSAGE_SIZE,
                R.drawable.msg_sticker,
                getString(R.string.StickerMessageSizeTitle),
                SharedConfig.getStickerMessageSizeText()
        ));
        items.add(UItem.asButton(
                ITEM_ID_MESSAGE_TEXT_SIZE,
                R.drawable.msg_customize,
                getString(R.string.MessageTextSizeTitle),
                LocaleController.formatString(R.string.MessageTextSizeValue, SharedConfig.fontSize)
        ));
        items.add(UItem.asButton(
                ITEM_ID_CUSTOM_STAR_BALANCE,
                R.drawable.msg_premium_liststar,
                getString(R.string.CustomStarBalanceTitle),
                SharedConfig.hasCustomStarBalance()
                        ? LocaleController.formatNumber(SharedConfig.getCustomStarBalance(), ' ')
                        : getString(R.string.CustomStarBalanceDefault)
        ));
        items.add(UItem.asButton(
                ITEM_ID_CLEAR_LOCAL_VIRTUAL_GIFTS,
                R.drawable.settings_delete,
                getString(R.string.ClearLocalVirtualGiftsTitle),
                LocaleController.formatString(R.string.LocalVirtualGiftsCount, SharedConfig.getLocalVirtualGiftsCount(currentAccount))
        ));
        items.add(UItem.asButton(
                ITEM_ID_INPUT_BAR_TEXT,
                R.drawable.msg_customize,
                getString(R.string.InputBarTextTitle),
                TextUtils.isEmpty(SharedConfig.getInputBarText()) ? getString(R.string.InputBarTextDefault) : SharedConfig.getInputBarText()
        ));
        String dialogsTitleText = controller.getDialogsTitleText();
        items.add(UItem.asButton(
                ITEM_ID_DIALOGS_TITLE_TEXT,
                getString(R.string.DialogsTitleText),
                TextUtils.isEmpty(dialogsTitleText) ? getString(R.string.AppName) : dialogsTitleText
        ));
        KeywordMessageFilterManager keywordFilterManager = KeywordMessageFilterManager.getInstance(currentAccount);
        items.add(UItem.asButton(
                ITEM_ID_BLOCKED_USERS,
                R.drawable.msg_block,
                getString(R.string.KeywordFilterBlockedUsers),
                LocaleController.formatString(R.string.KeywordFilterBlockedUsersSummary, keywordFilterManager.getCustomBlockedUsersCount())
        ));
        items.add(UItem.asButton(
                ITEM_ID_KEYWORD_FILTER,
                getString(R.string.KeywordFilterSettings),
                keywordFilterManager.isEnabled()
                        ? LocaleController.formatString(R.string.KeywordFilterSummaryEnabled, keywordFilterManager.getKeywordsCount(), keywordFilterManager.getWhitelistCount())
                        : getString(R.string.KeywordFilterSummaryDisabled)
        ));
        AutoReplyManager autoReplyManager = AutoReplyManager.getInstance(currentAccount);
        items.add(UItem.asButton(
                ITEM_ID_AUTO_REPLY,
                getString(R.string.AutoReplySettings),
                autoReplyManager.isEnabled()
                        ? LocaleController.formatString(R.string.AutoReplySummaryEnabled, autoReplyManager.getRulesCount(), autoReplyManager.getReplyMessagesCount())
                        : getString(R.string.AutoReplySummaryDisabled)
        ));
        items.add(UItem.asShadow(getString(R.string.ExtensionFeaturesInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof ProfileActivity.SearchAdapter.SearchResult) {
            ((ProfileActivity.SearchAdapter.SearchResult) item.object).open(getParentLayout());
            AndroidUtilities.hideKeyboard(searchField);
            return;
        }
        MangoFeatureRegistry.Feature feature = MangoFeatureRegistry.find(item.id);
        if (feature != null) {
            MangoSettings.setBoolean(currentAccount, feature.key, !MangoSettings.getBoolean(currentAccount, feature.key));
            listView.adapter.update(false);
            return;
        }
        MessagesController controller = getMessagesController();
        boolean enabled;

        switch (item.id) {
            case ITEM_COMMON_CLEAR_CACHE:
                presentExtensionPage(new CacheControlActivity());
                return;
            case ITEM_COMMON_PROXY:
                presentExtensionPage(new ProxyListActivity());
                return;
            case ITEM_COMMON_ACCOUNT: {
                Activity activity = getParentActivity();
                if (activity != null && UserConfig.getActivatedAccountsCount() > 1) {
                    AlertDialog dialog = AlertsCreator.createAccountSelectDialog(activity, account -> {
                        if (LaunchActivity.instance != null) LaunchActivity.instance.switchToAccount(account, true);
                    });
                    if (dialog != null) showDialog(dialog);
                } else {
                    presentExtensionPage(new SettingsActivity());
                }
                return;
            }
            case ITEM_COMMON_PRIVACY:
                presentExtensionPage(new PrivacySettingsActivity());
                return;
            case ITEM_SYNC_SWITCHES:
                org.telegram.messenger.MangoSettingsSync.setEnabled(currentAccount,
                        !org.telegram.messenger.MangoSettingsSync.isEnabled());
                listView.adapter.update(false);
                return;
            case ITEM_SECURITY_CHECK:
                presentExtensionPage(new MangoSecurityCheckActivity());
                return;
            case ITEM_USER_BROADCAST:
                presentExtensionPage(new MangoUserBroadcastActivity());
                return;
            case ITEM_HISTORY:
                presentExtensionPage(new MangoSettingsHistoryActivity());
                return;
            case ITEM_RESET_ALL:
                confirmReset();
                return;
            case ITEM_NOTIFICATION_RULES:
                presentExtensionPage(new MangoNotificationRulesActivity());
                return;
            case ITEM_RANDOM_REPLY_GROUPS:
                presentExtensionPage(new MangoAutoReadReplyGroupsActivity());
                return;
            case ITEM_SCAM_USERS:
                presentExtensionPage(new ScamUsersActivity());
                return;
            case ITEM_BUILTIN_THEMES:
                AndroidUtilities.hideKeyboard(searchField);
                MangoBuiltinThemes.show(this);
                return;
            case ITEM_ID_SAVE_RECALLED:
                enabled = !org.telegram.messenger.RecalledMessagesStore.isEnabled(currentAccount);
                org.telegram.messenger.RecalledMessagesStore.setEnabled(currentAccount, enabled);
                break;
            case ITEM_ID_RECALLED_ARCHIVE:
                presentExtensionPage(new RecalledMessagesActivity());
                return;
            case ITEM_ID_MESSAGE_ECHO:
                presentExtensionPage(new MessageEchoActivity());
                return;
            case ITEM_ID_SCAM_PROTECTION:
                presentExtensionPage(new ScamProtectionActivity());
                return;
            case ITEM_ID_INTERACTION_STATISTICS:
                presentExtensionPage(new InteractionStatisticsActivity());
                return;
            case ITEM_ID_MESSAGE_MEMORIAL:
                presentExtensionPage(new MessageMemorialActivity());
                return;
            case ITEM_ID_DUPLICATE_FILES:
                presentExtensionPage(new DuplicateFilesActivity());
                return;
            case ITEM_ID_BATCH_LEAVE:
                presentExtensionPage(new ChannelBatchLeaveActivity());
                return;
            case ITEM_ID_AUDIO_AUTO_PLAY_NEXT:
                enabled = !ExtensionMediaSettings.isAutoPlayNextEnabled();
                ExtensionMediaSettings.setAutoPlayNextEnabled(enabled);
                break;
            case ITEM_ID_PERSONAL_WALLPAPERS:
                presentExtensionPage(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
                return;
            case ITEM_ID_DAILY_MESSAGE_TASKS:
                presentExtensionPage(new DailyMessageTasksActivity());
                return;
            case ITEM_ID_QUICK_FORWARD_ACCOUNT:
                enabled = !MangoSettings.getBoolean(currentAccount, "mango_quick_forward_account_selection", false);
                MangoSettings.setBoolean(currentAccount, "mango_quick_forward_account_selection", enabled);
                break;
            case ITEM_ID_PRESENCE_SIMULATION:
                presentExtensionPage(new PresenceSimulationActivity());
                return;
            case ITEM_UNIFIED_TAB:
                enabled = !CrossAccountInboxActivity.isEnabled(currentAccount);
                CrossAccountInboxActivity.setEnabled(currentAccount, enabled);
                break;
            case ITEM_UNIFIED_INBOX:
                // Keep the switch authoritative: a disabled summary is not
                // instantiated from the extension page or the tab pager.
                if (!CrossAccountInboxActivity.isEnabled(currentAccount)) {
                    listView.adapter.update(false);
                    return;
                }
                presentExtensionPage(new CrossAccountInboxActivity());
                return;
            case ITEM_RESTRICTION_TITLE:
                presentExtensionPage(new AppIdentitySettingsActivity());
                return;
            case ITEM_DOWNLOAD_RETENTION:
                presentExtensionPage(new DownloadRetentionActivity());
                return;
            case ITEM_PERFORMANCE_MONITOR:
                presentExtensionPage(new MangoPerformanceActivity());
                return;
            case ITEM_DOWNLOAD_SPEED_HUD:
                enabled = !org.telegram.messenger.MangoSettings.getBoolean(currentAccount, "mango_download_speed_hud", false);
                org.telegram.ui.Components.MangoDownloadSpeedHud.setEnabled(currentAccount, enabled);
                break;
            case ITEM_ID_MESSAGE_MANAGEMENT_TAB:
                enabled = !MessageActionSettings.isCenterEnabled(currentAccount);
                MessageActionSettings.setCenterEnabled(currentAccount, enabled);
                break;
            case ITEM_ID_MESSAGE_MANAGEMENT:
                presentExtensionPage(new MessageManagementActivity());
                return;
            case ITEM_ID_MESSAGE_ACTION_SETTINGS:
                presentExtensionPage(new MessageActionSettingsActivity());
                return;
            case ITEM_ID_HIDE_ONLINE_STATUS:
                enabled = !controller.isHideOnlineStatusEnabled();
                controller.setHideOnlineStatusEnabled(enabled);
                break;
            case ITEM_ID_HIDE_TYPING_STATUS:
                enabled = !controller.isHideTypingStatusEnabled();
                controller.setHideTypingStatusEnabled(enabled);
                break;
            case ITEM_ID_HIDE_READ_STATUS:
                enabled = !controller.isHideReadStatusEnabled();
                controller.setHideReadStatusEnabled(enabled);
                break;
            case ITEM_ID_ANTI_REVOKE:
                enabled = !controller.isAntiRevokeEnabled();
                controller.setAntiRevokeEnabled(enabled);
                break;
            case ITEM_ID_FORCE_FORWARD:
                enabled = !controller.isForceForwardEnabled();
                controller.setForceForwardEnabled(enabled);
                break;
            case ITEM_ID_UNLIMITED_PIN:
                enabled = !controller.isUnlimitedPinEnabled();
                controller.setUnlimitedPinEnabled(enabled);
                break;
            case ITEM_ID_UNREAD_DIALOGS_TAB:
                enabled = !controller.isUnreadDialogsTabEnabled();
                controller.setUnreadDialogsTabEnabled(enabled);
                break;
            case ITEM_ID_CONTINUE_SCROLL_CHANNEL_JUMP:
                enabled = !controller.isContinueScrollChannelJumpEnabled();
                controller.setContinueScrollChannelJumpEnabled(enabled);
                break;
            case ITEM_ID_CHINESE_READ_STATUS_TEXT:
                enabled = !controller.isChineseReadStatusTextEnabled();
                controller.setChineseReadStatusTextEnabled(enabled);
                break;
            case ITEM_ID_HIDE_CHANNEL_SEND_AS:
                enabled = !SharedConfig.isHideChannelSendAsEnabled();
                SharedConfig.setHideChannelSendAsEnabled(enabled);
                break;
            case ITEM_ID_DEFAULT_HIGH_QUALITY_MEDIA:
                enabled = !SharedConfig.isDefaultHighQualityMediaEnabled();
                SharedConfig.setDefaultHighQualityMediaEnabled(enabled);
                break;
            case ITEM_ID_TABLET_FULL_WIDTH:
                enabled = !SharedConfig.isTabletFullWidthEnabled();
                SharedConfig.setTabletFullWidthEnabled(enabled);
                AndroidUtilities.runOnUIThread(() -> {
                    Activity activity = getParentActivity();
                    if (activity != null) {
                        activity.recreate();
                    }
                }, 150);
                break;
            case ITEM_ID_CONTACTS_TAB:
                enabled = !getUserConfig().showContactsTab;
                getUserConfig().setShowContactsTab(enabled);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                break;
            case ITEM_ID_CHANNEL_FEED:
                enabled = !SharedConfig.isChannelFeedEnabled();
                SharedConfig.setChannelFeedEnabled(enabled);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                break;
            case ITEM_ID_UNLIMITED_CHANNEL_JOIN:
                enabled = !SharedConfig.isUnlimitedChannelJoinEnabled();
                SharedConfig.setUnlimitedChannelJoinEnabled(enabled);
                break;
            case ITEM_ID_UNLIMITED_DIALOG_FILTERS:
                enabled = !SharedConfig.isUnlimitedDialogFiltersEnabled();
                SharedConfig.setUnlimitedDialogFiltersEnabled(enabled);
                break;
            case ITEM_ID_LOCAL_HOLD_VERIFICATION:
                enabled = !SharedConfig.isLocalHoldVerificationEnabled();
                SharedConfig.setLocalHoldVerificationEnabled(enabled);
                break;
            case ITEM_ID_AUTO_TRANSLATE:
                // Kept for compatibility with older saved UI state.
                SharedConfig.setAutoTranslateMessagesEnabled(false);
                return;
            case ITEM_ID_TRANSLATION_DISPLAY_MODE:
                showTranslationDisplayModeDialog();
                return;
            case ITEM_ID_AUTO_REACTION:
                enabled = !SharedConfig.isAutoReactionEnabled();
                SharedConfig.setAutoReactionEnabled(enabled);
                break;
            case ITEM_ID_AUTO_REACTION_EMOJI:
                showAutoReactionEmojiDialog();
                return;
            case ITEM_ID_AUTO_REACTION_TARGET:
                showAutoReactionTargetDialog();
                return;
            case ITEM_ID_AUTO_REACTION_USERS:
                showAutoReactionUsersDialog();
                return;
            case ITEM_ID_BROADCAST_TO_GROUPS:
                showBroadcastToGroupsDialog();
                return;
            case ITEM_GROUP_BROADCAST_TASKS:
                showBroadcastTasksDialog();
                return;
            case ITEM_ID_LOCAL_VIRTUAL_GIFT_BUY:
                enabled = !SharedConfig.isLocalVirtualGiftBuyEnabled();
                SharedConfig.setLocalVirtualGiftBuyEnabled(enabled);
                break;
            case ITEM_ID_DOWNLOAD_SPEED:
                showDownloadSpeedDialog();
                return;
            case ITEM_ID_UPLOAD_SPEED:
                showUploadSpeedDialog();
                return;
            case ITEM_ID_MANGO_UPDATE:
                showMangoUpdateDialog();
                return;
            case ITEM_ID_CUSTOM_DOWNLOAD_PATH:
                showCustomDownloadPathDialog();
                return;
            case ITEM_ID_SEND_MP3_AS_VOICE:
                enabled = !SharedConfig.isSendMp3AsVoiceEnabled();
                SharedConfig.setSendMp3AsVoiceEnabled(enabled);
                break;
            case ITEM_ID_CUSTOM_VIDEO_WALLPAPER:
                showCustomVideoWallpaperDialog();
                return;
            case ITEM_ID_STICKER_MESSAGE_SIZE:
                showStickerMessageSizeDialog();
                return;
            case ITEM_ID_MESSAGE_TEXT_SIZE:
                showMessageTextSizeDialog();
                return;
            case ITEM_ID_TABLET_WIDTH_RATIO:
                showTabletWidthRatioDialog();
                return;
            case ITEM_ID_DIALOG_ROW_HEIGHT:
                showDialogRowHeightDialog();
                return;
            case ITEM_ID_CUSTOM_STAR_BALANCE:
                showCustomStarBalanceDialog();
                return;
            case ITEM_ID_CLEAR_LOCAL_VIRTUAL_GIFTS:
                showClearLocalVirtualGiftsDialog();
                return;
            case ITEM_ID_INPUT_BAR_TEXT:
                showInputBarTextDialog();
                return;
            case ITEM_ID_DIALOGS_TITLE_TEXT:
                showDialogsTitleDialog();
                return;
            case ITEM_ID_BLOCKED_USERS:
                showBlockedUsersDialog();
                return;
            case ITEM_ID_KEYWORD_FILTER:
                presentFragment(new KeywordFilterActivity());
                return;
            case ITEM_ID_AUTO_REPLY:
                presentFragment(new AutoReplySettingsActivity());
                return;
            default:
                return;
        }

        if (view instanceof TextCheckCell2) {
            ((TextCheckCell2) view).setChecked(enabled);
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private void presentExtensionPage(BaseFragment page) {
        page.setCurrentAccount(currentAccount);
        presentFragment(page);
    }

    private void setAutoTranslateMessagesEnabled(boolean enabled) {
        SharedConfig.setAutoTranslateMessagesEnabled(enabled);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private void showAutoTranslateConfirmDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AutoTranslateMessagesTitle));
        builder.setMessage(getString(R.string.AutoTranslateMessagesConfirmInfo));
        builder.setPositiveButton(getString(R.string.Enable), (dialog, which) -> setAutoTranslateMessagesEnabled(true));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String getAutoReactionTargetText() {
        switch (SharedConfig.getAutoReactionTarget()) {
            case SharedConfig.AUTO_REACTION_TARGET_OUTGOING:
                return getString(R.string.AutoReactionTargetOutgoing);
            case SharedConfig.AUTO_REACTION_TARGET_BOTH:
                return getString(R.string.AutoReactionTargetBoth);
            case SharedConfig.AUTO_REACTION_TARGET_INCOMING:
            default:
                return getString(R.string.AutoReactionTargetIncoming);
        }
    }

    private void showAutoReactionEmojiDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditTextBoldCursor editText = createDialogEditText(context, getString(R.string.AutoReactionEmojiHint), 16);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(SharedConfig.getAutoReactionEmoji());
        editText.setSelection(editText.length());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AutoReactionEmojiTitle));
        builder.setMessage(getString(R.string.AutoReactionEmojiInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            SharedConfig.setAutoReactionEmoji(editText.getText().toString());
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 120);
    }

    private void showAutoReactionTargetDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        int[] targets = new int[]{
                SharedConfig.AUTO_REACTION_TARGET_INCOMING,
                SharedConfig.AUTO_REACTION_TARGET_OUTGOING,
                SharedConfig.AUTO_REACTION_TARGET_BOTH
        };
        CharSequence[] labels = new CharSequence[]{
                getString(R.string.AutoReactionTargetIncoming),
                getString(R.string.AutoReactionTargetOutgoing),
                getString(R.string.AutoReactionTargetBoth)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AutoReactionTargetTitle));
        builder.setItems(labels, (dialog, which) -> {
            SharedConfig.setAutoReactionTarget(targets[which]);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAutoReactionUsersDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AutoReactionUsersTitle));
        builder.setMessage(getString(R.string.AutoReactionUsersInfo));
        builder.setItems(new CharSequence[]{
                getString(R.string.AutoReactionUsersAll),
                getString(R.string.AutoReactionUsersChoose),
                getString(R.string.AutoReactionUsersAddByIdOrUsername)
        }, (dialog, which) -> {
            if (which == 0) {
                SharedConfig.setAutoReactionUserIds(currentAccount, null);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(false);
                }
            } else if (which == 1) {
                showAutoReactionUserSelector();
            } else {
                showAutoReactionUserInputDialog();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAutoReactionUserSelector() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowGroups", false);
        args.putBoolean("allowMegagroups", false);
        args.putBoolean("allowLegacyGroups", false);
        args.putBoolean("allowChannels", false);
        args.putBoolean("allowUsers", true);
        args.putBoolean("allowBots", true);
        args.putBoolean("checkCanWrite", false);
        args.putBoolean("allowGlobalSearch", true);
        args.putString("selectAlertString", LocaleController.getString(R.string.AutoReactionUsersChoose));

        DialogsActivity fragment = new DialogsActivity(args) {
            @Override
            public ArrayList<TLRPC.Dialog> getDialogsArray(int account, int dialogsType, int folderId, boolean frozen) {
                ArrayList<TLRPC.Dialog> sourceDialogs = super.getDialogsArray(account, DialogsActivity.DIALOGS_TYPE_DEFAULT, folderId, frozen);
                ArrayList<TLRPC.Dialog> users = new ArrayList<>();
                for (int i = 0; i < sourceDialogs.size(); i++) {
                    TLRPC.Dialog dialog = sourceDialogs.get(i);
                    if (dialog != null && dialog.id > 0) {
                        users.add(dialog);
                    }
                }
                return users;
            }

            @Override
            public boolean shouldShowNextButton(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                return dids != null && !dids.isEmpty();
            }
        };
        fragment.setDelegate((dialogsActivity, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            HashSet<Long> selectedUsers = new HashSet<>();
            for (int i = 0; i < dids.size(); i++) {
                long dialogId = dids.get(i).dialogId;
                if (dialogId > 0) {
                    selectedUsers.add(dialogId);
                }
            }
            SharedConfig.setAutoReactionUserIds(currentAccount, selectedUsers);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
            dialogsActivity.finishFragment();
            return true;
        });
        presentFragment(fragment);
    }

    private void showAutoReactionUserInputDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditTextBoldCursor editText = createDialogEditText(context, getString(R.string.AutoReactionUserInputHint), 64);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.AutoReactionUsersAddByIdOrUsername));
        builder.setMessage(getString(R.string.AutoReactionUserInputInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Add), (dialog, which) -> addAutoReactionUser(editText.getText().toString()));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 120);
    }

    private void addAutoReactionUser(String input) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        String value = input == null ? "" : input.trim();
        while (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (TextUtils.isEmpty(value)) {
            Toast.makeText(context, getString(R.string.AutoReactionUserInputInvalid), Toast.LENGTH_SHORT).show();
            return;
        }

        if (AndroidUtilities.isNumeric(value)) {
            try {
                long userId = Long.parseLong(value);
                if (userId > 0) {
                    saveAutoReactionUser(userId, value);
                    return;
                }
            } catch (NumberFormatException ignore) {
            }
            Toast.makeText(context, getString(R.string.AutoReactionUserInputInvalid), Toast.LENGTH_SHORT).show();
            return;
        }

        String username = value;
        AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER, resourceProvider);
        boolean[] completed = new boolean[1];
        Runnable[] cancelRequest = new Runnable[1];
        cancelRequest[0] = getMessagesController().getUserNameResolver().resolve(username, peerId -> {
            completed[0] = true;
            progressDialog.dismiss();
            if (isFinished || getParentActivity() == null) {
                return;
            }
            if (peerId == null || peerId <= 0 || peerId == Long.MAX_VALUE) {
                Toast.makeText(getParentActivity(), getString(R.string.AutoReactionUserNotFound), Toast.LENGTH_SHORT).show();
                return;
            }
            saveAutoReactionUser(peerId, "@" + username);
        });
        if (!completed[0]) {
            progressDialog.setOnCancelListener(dialog -> {
                if (cancelRequest[0] != null) {
                    cancelRequest[0].run();
                }
            });
            showDialog(progressDialog);
        }
    }

    private void saveAutoReactionUser(long userId, String label) {
        HashSet<Long> selectedUsers = SharedConfig.getAutoReactionUserIds(currentAccount);
        selectedUsers.add(userId);
        SharedConfig.setAutoReactionUserIds(currentAccount, selectedUsers);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        Context context = getParentActivity();
        if (context != null) {
            Toast.makeText(context, LocaleController.formatString(R.string.AutoReactionUserAdded, label), Toast.LENGTH_SHORT).show();
        }
    }

    private void showBroadcastToGroupsDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(4));
        TextView intro = new TextView(context);
        intro.setText(getString(R.string.BroadcastToGroupsInputInfo));
        intro.setTextSize(14);
        intro.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        intro.setLineSpacing(AndroidUtilities.dp(2), 1f);
        container.addView(intro, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 12));
        TextView label = new TextView(context);
        label.setText("群发内容");
        label.setTextSize(14);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        container.addView(label, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 7));
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        editText.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_dialogBackground, resourceProvider)));
        editText.setHintText(getString(R.string.BroadcastToGroupsHint));
        editText.setMinLines(5); editText.setMaxLines(9); editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(1, Math.min(4096, getMessagesController().maxMessageLength)))});
        editText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        container.addView(editText, LayoutHelper.createLinear(-1, -2));
        TextView count = new TextView(context);
        count.setText("0 / " + Math.min(4096, getMessagesController().maxMessageLength));
        count.setTextSize(12); count.setGravity(Gravity.RIGHT);
        count.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        container.addView(count, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 12));
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int c) { count.setText(s.length() + " / " + Math.min(4096, getMessagesController().maxMessageLength)); }
            @Override public void afterTextChanged(Editable e) { }
        });
        LinearLayout example = new LinearLayout(context);
        example.setOrientation(LinearLayout.VERTICAL);
        example.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(11), AndroidUtilities.dp(13), AndroidUtilities.dp(11));
        example.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.getColor(Theme.key_dialogBackgroundGray, resourceProvider)));
        TextView exampleTitle = new TextView(context);
        exampleTitle.setText("消息示例"); exampleTitle.setTextSize(13);
        exampleTitle.setTypeface(AndroidUtilities.bold());
        exampleTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        example.addView(exampleTitle, LayoutHelper.createLinear(-1, -2));
        TextView exampleBody = new TextView(context);
        exampleBody.setText("大家好！本周活动安排如下：\n周五 20:00 在线交流\n有兴趣的朋友可以在群里回复。");
        exampleBody.setTextSize(13); exampleBody.setLineSpacing(AndroidUtilities.dp(2), 1f);
        exampleBody.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        example.addView(exampleBody, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 0));
        container.addView(example, LayoutHelper.createLinear(-1, -2));
        ScrollView composerScroll = new ScrollView(context);
        composerScroll.setFillViewport(false);
        composerScroll.addView(container);
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.BroadcastToGroupsTitle)).setView(composerScroll);
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String message = SendMessagesHelper.getTrimmedString(editText.getText().toString());
            if (TextUtils.isEmpty(message)) {
                Toast.makeText(context, getString(R.string.BroadcastToGroupsEmptyText), Toast.LENGTH_SHORT).show();
                return;
            }
            ArrayList<Long> groups = getBroadcastTargetGroups();
            if (groups.isEmpty()) {
                Toast.makeText(context, getString(R.string.BroadcastToGroupsNoGroups), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showBroadcastToGroupsConfirm(message, groups);
        }));
        showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> { editText.requestFocus(); AndroidUtilities.showKeyboard(editText); }, 120);
    }

    private void showBroadcastToGroupsConfirm(String text, ArrayList<Long> allGroups) {
        Context context = getParentActivity();
        if (context == null) return;
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (long id : allGroups) {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            if (chat != null) { ids.add(id); names.add(chat.title == null ? "未命名群组" : chat.title); }
        }
        if (ids.isEmpty()) { Toast.makeText(context, getString(R.string.BroadcastToGroupsNoGroups), Toast.LENGTH_SHORT).show(); return; }
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(4));
        TextView intro = new TextView(context);
        intro.setText("找到 " + ids.size() + " 个可发送消息的群组。默认全选，可取消不需要发送的群组。");
        intro.setTextSize(14); intro.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        content.addView(intro, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
        android.widget.CheckBox all = new android.widget.CheckBox(context);
        all.setText("全选群组"); all.setTextSize(14); all.setChecked(true);
        all.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        content.addView(all, LayoutHelper.createLinear(-1, 44));
        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
        ArrayList<android.widget.CheckBox> checks = new ArrayList<>();
        for (String name : names) {
            android.widget.CheckBox check = new android.widget.CheckBox(context);
            check.setText(name); check.setTextSize(14); check.setChecked(true);
            check.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
            rows.addView(check, LayoutHelper.createLinear(-1, 44)); checks.add(check);
        }
        scroll.addView(rows);
        content.addView(scroll, LayoutHelper.createLinear(-1, Math.min(AndroidUtilities.dp(320), AndroidUtilities.dp(44) * checks.size())));
        TextView count = new TextView(context);
        count.setText("已选择 " + checks.size() + " / " + checks.size() + " 个群组");
        count.setTextSize(12); count.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        content.addView(count, LayoutHelper.createLinear(-1, -2, 0, 7, 0, 0));
        final boolean[] updatingSelection = {false};
        Runnable refresh = () -> {
            int selected = 0;
            for (android.widget.CheckBox check : checks) if (check.isChecked()) selected++;
            count.setText("已选择 " + selected + " / " + checks.size() + " 个群组");
            updatingSelection[0] = true;
            all.setChecked(selected == checks.size());
            updatingSelection[0] = false;
        };
        for (android.widget.CheckBox check : checks) {
            check.setOnCheckedChangeListener((button, checked) -> {
                if (!updatingSelection[0]) refresh.run();
            });
        }
        all.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSelection[0]) return;
            updatingSelection[0] = true;
            for (android.widget.CheckBox item : checks) item.setChecked(checked);
            updatingSelection[0] = false;
            refresh.run();
        });
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context).setTitle("选择群组").setView(content)
                .setPositiveButton(getString(R.string.OK), null)
                .setNegativeButton(getString(R.string.Cancel), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ArrayList<Long> selected = new ArrayList<>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isChecked()) selected.add(ids.get(i));
            if (selected.isEmpty()) {
                Toast.makeText(context, "请至少选择一个群组", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showIntervalInputDialog(text, selected);
        }));
        dialog.show();
    }

// 新增：设置最小/最大间隔
private void showIntervalInputDialog(String text, ArrayList<Long> selectedGroups) {
    Context context = getParentActivity();
    if (context == null) return;
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(4));
    TextView intro = new TextView(context);
    intro.setText("已选择 " + selectedGroups.size() + " 个群组。设置每条消息之间的随机发送间隔。");
    intro.setTextSize(14); intro.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
    layout.addView(intro, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 12));
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
    card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_dialogBackgroundGray, resourceProvider)));
    TextView title = new TextView(context);
    title.setText("发送间隔（秒）"); title.setTextSize(14); title.setTypeface(AndroidUtilities.bold());
    title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
    card.addView(title, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8));
    android.widget.EditText minInput = new android.widget.EditText(context);
    minInput.setSingleLine(true); minInput.setText("5"); minInput.setHint("最小间隔");
    minInput.setInputType(InputType.TYPE_CLASS_NUMBER); minInput.setTextSize(15);
    minInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
    minInput.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
    minInput.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_dialogBackground, resourceProvider)));
    card.addView(minInput, LayoutHelper.createLinear(-1, 44, 0, 0, 0, 8));
    android.widget.EditText maxInput = new android.widget.EditText(context);
    maxInput.setSingleLine(true); maxInput.setText("15"); maxInput.setHint("最大间隔");
    maxInput.setInputType(InputType.TYPE_CLASS_NUMBER); maxInput.setTextSize(15);
    maxInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
    maxInput.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
    maxInput.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_dialogBackground, resourceProvider)));
    card.addView(maxInput, LayoutHelper.createLinear(-1, 44));
    TextView preview = new TextView(context);
    preview.setText("示例：每次发送前随机等待 5～15 秒。");
    preview.setTextSize(12); preview.setTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
    card.addView(preview, LayoutHelper.createLinear(-1, -2, 0, 9, 0, 0));
    layout.addView(card, LayoutHelper.createLinear(-1, -2));
    android.text.TextWatcher watcher = new TextWatcher() {
        private void update() {
            int min=5,max=15;
            try { min=Integer.parseInt(minInput.getText().toString()); } catch(Exception ignored) { }
            try { max=Integer.parseInt(maxInput.getText().toString()); } catch(Exception ignored) { }
            min=Math.max(1,min); max=Math.max(min+1,max);
            preview.setText("示例：每次发送前随机等待 " + min + "～" + max + " 秒。");
        }
        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
        @Override public void onTextChanged(CharSequence s,int start,int before,int count) { update(); }
        @Override public void afterTextChanged(Editable e) { }
    };
    minInput.addTextChangedListener(watcher); maxInput.addTextChangedListener(watcher);
    android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context).setTitle(getString(R.string.BroadcastToGroupsWarningTitle))
            .setView(layout).setPositiveButton(getString(R.string.OK), (d, which) -> {
                int minSec=5,maxSec=15;
                try { if (!TextUtils.isEmpty(minInput.getText())) minSec=Integer.parseInt(minInput.getText().toString()); } catch(Exception ignored) { }
                try { if (!TextUtils.isEmpty(maxInput.getText())) maxSec=Integer.parseInt(maxInput.getText().toString()); } catch(Exception ignored) { }
                if (minSec<1) minSec=1; if (maxSec<=minSec) maxSec=minSec+1;
                sendTextToGroups(text, selectedGroups, minSec, maxSec);
            }).setNegativeButton(getString(R.string.Cancel), null).create();
    dialog.show();
}

private void sendTextToGroups(String text, ArrayList<Long> targetGroups, int minSec, int maxSec) {
    if (TextUtils.isEmpty(text) || targetGroups == null || targetGroups.isEmpty()) {
        return;
    }
    SendMessagesHelper sendMessagesHelper = SendMessagesHelper.getInstance(currentAccount);
    ArrayList<String> targetNames = new ArrayList<>();
    for (long dialogId : targetGroups) {
        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        targetNames.add(chat == null ? "群组" : chat.title);
    }
    final long taskId = MangoGroupBroadcastTaskStore.create(currentAccount, text, targetNames);

    // Keep the existing randomized interval and SendMessagesHelper payload unchanged.
    org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
        long currentDelay = 0;
        int diff = (maxSec - minSec) * 1000;
        if (diff <= 0) diff = 10000;

        for (int i = 0, size = targetGroups.size(); i < size; i++) {
            final long dialogId = targetGroups.get(i);
            long randomDelay = org.telegram.messenger.Utilities.random.nextInt(diff) + (minSec * 1000L);
            currentDelay += randomDelay;
            final long finalDelay = currentDelay;

            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                try {
                    sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of(
                            text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false
                    ));
                    MangoGroupBroadcastTaskStore.markResult(currentAccount, taskId, true);
                } catch (Throwable error) {
                    FileLog.e(error);
                    MangoGroupBroadcastTaskStore.markResult(currentAccount, taskId, false);
                }
            }, finalDelay);
        }
    });
    Toast.makeText(getParentActivity(), "已创建群发任务，共 " + targetGroups.size() + " 个群组", Toast.LENGTH_LONG).show();
    showBroadcastTasksDialog();
}

    private void showBroadcastTasksDialog() {
        Context context = getParentActivity();
        if (context == null) return;

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(list);

        final AlertDialog dialog = new AlertDialog.Builder(context, resourceProvider)
                .setTitle("群发任务进度")
                .setView(scroll)
                .setPositiveButton(getString(R.string.OK), null)
                .create();

        Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                list.removeAllViews();
                ArrayList<MangoGroupBroadcastTaskStore.Task> tasks = MangoGroupBroadcastTaskStore.getTasks(currentAccount);
                if (tasks.isEmpty()) {
                    TextView empty = new TextView(context);
                    empty.setText("还没有群发任务。创建群发后，这里会显示实时进度和历史记录。");
                    empty.setTextSize(14);
                    empty.setTextColor(Theme.getColor(Theme.key_dialogTextHint));
                    list.addView(empty, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 8));
                }
                for (MangoGroupBroadcastTaskStore.Task task : tasks) {
                    LinearLayout card = new LinearLayout(context);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
                    card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_dialogBackground)));
                    TextView title = new TextView(context);
                    title.setTextSize(14);
                    title.setTypeface(AndroidUtilities.bold());
                    title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    String state = task.state == MangoGroupBroadcastTaskStore.SENDING ? "发送中"
                            : task.state == MangoGroupBroadcastTaskStore.COMPLETED ? "已完成" : "应用中断";
                    title.setText(state + " · " + LocaleController.formatDateChat((int) (task.createdAt / 1000)));
                    card.addView(title, LayoutHelper.createLinear(-1, -2));

                    TextView count = new TextView(context);
                    count.setTextSize(13);
                    count.setTextColor(Theme.getColor(Theme.key_dialogTextHint));
                    count.setText("已处理 " + task.processed + "/" + task.total + " · 已加入发送队列 "
                            + task.queued + " · 异常 " + task.failed);
                    card.addView(count, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 6));

                    ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                    progress.setMax(1000);
                    progress.setProgress(task.total <= 0 ? 0 : (int) (task.processed * 1000L / task.total));
                    card.addView(progress, LayoutHelper.createLinear(-1, 6));

                    TextView preview = new TextView(context);
                    preview.setTextSize(13);
                    preview.setTextColor(Theme.getColor(Theme.key_dialogTextHint));
                    preview.setMaxLines(2);
                    preview.setText(task.message);
                    card.addView(preview, LayoutHelper.createLinear(-1, -2, 0, 7, 0, 0));

                    if (!task.targetNames.isEmpty()) {
                        TextView targets = new TextView(context);
                        targets.setTextSize(12);
                        targets.setTextColor(Theme.getColor(Theme.key_dialogTextHint));
                        int show = Math.min(3, task.targetNames.size());
                        StringBuilder names = new StringBuilder("目标：");
                        for (int i = 0; i < show; i++) {
                            if (i > 0) names.append("、");
                            names.append(task.targetNames.get(i));
                        }
                        if (task.targetNames.size() > show) names.append(" 等 ").append(task.targetNames.size()).append(" 个群组");
                        targets.setText(names.toString());
                        card.addView(targets, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 0));
                    }
                    list.addView(card, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 10));
                }
                if (dialog.isShowing()) AndroidUtilities.runOnUIThread(this, 1000);
            }
        };
        dialog.setOnDismissListener(d -> AndroidUtilities.cancelRunOnUIThread(refresh));
        dialog.setOnShowListener(d -> refresh.run());
        dialog.show();
    }

    private ArrayList<Long> getBroadcastTargetGroups() {
        ArrayList<Long> result = new ArrayList<>();
        ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getDialogs(0);
        for (int i = 0, size = dialogs.size(); i < size; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null || dialog.id >= 0) {
                continue;
            }
            TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
            if (chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannelAndNotMegaGroup(chat) || !ChatObject.canSendMessages(chat)) {
                continue;
            }
            result.add(dialog.id);
        }
        return result;
    }

    private void sendTextToGroups(String text, ArrayList<Long> targetGroups) {
        if (TextUtils.isEmpty(text) || targetGroups == null || targetGroups.isEmpty()) {
            return;
        }
        SendMessagesHelper sendMessagesHelper = SendMessagesHelper.getInstance(currentAccount);
        for (int i = 0, size = targetGroups.size(); i < size; i++) {
            final long dialogId = targetGroups.get(i);
            final long delay = i * 350L;
            AndroidUtilities.runOnUIThread(() -> sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false)), delay);
        }
        Context context = getParentActivity();
        if (context != null) {
            Toast.makeText(context, LocaleController.formatString(R.string.BroadcastToGroupsStarted, targetGroups.size()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDialogsTitleDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        MessagesController controller = getMessagesController();
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        editText.setHintText(getString(R.string.DialogsTitleTextHint));
        editText.setSingleLine(true);
        editText.setMaxLines(1);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        editText.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12));
        String currentValue = controller.getDialogsTitleText();
        if (!TextUtils.isEmpty(currentValue)) {
            editText.setText(currentValue);
            editText.setSelection(currentValue.length());
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.DialogsTitleText));
        builder.setMessage(getString(R.string.DialogsTitleTextInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            controller.setDialogsTitleText(editText.getText().toString());
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            controller.setDialogsTitleText(null);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 120);
    }

    private void showDownloadSpeedDialog() {
        showTransferSpeedDialog(false);
    }

    private String formatSpeedMultiplier(int value) {
        return value <= 1 ? getString(R.string.DownloadSpeedBoostNormal)
                : LocaleController.formatString(R.string.DownloadSpeedBoostValue, value);
    }

    private static int speedSliderIndex(float progress, int optionCount) {
        if (optionCount <= 1) return 0;
        return Math.max(0, Math.min(optionCount - 1, Math.round(progress * (optionCount - 1))));
    }

    private void showTransferSpeedDialog(boolean upload) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final int[] options = upload ? SharedConfig.UPLOAD_SPEED_MULTIPLIERS : SharedConfig.DOWNLOAD_SPEED_MULTIPLIERS;
        int current = upload ? SharedConfig.getUploadSpeedMultiplier() : SharedConfig.getDownloadSpeedMultiplier();
        final int[] selectedIndex = {0};
        for (int i = 0; i < options.length; i++) {
            if (options[i] == current) selectedIndex[0] = i;
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        TextView value = new TextView(context);
        value.setGravity(Gravity.CENTER);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        value.setTypeface(AndroidUtilities.bold());
        value.setTextColor(Theme.getColor(Theme.key_dialogTextBlue, resourceProvider));
        value.setText(formatSpeedMultiplier(options[selectedIndex[0]]));
        container.addView(value, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 4));

        SeekBarView slider = new SeekBarView(context, resourceProvider);
        slider.setColors(Theme.getColor(Theme.key_dialogGrayLine, resourceProvider),
                Theme.getColor(Theme.key_dialogTextBlue, resourceProvider));
        slider.setSeparatorsCount(options.length);
        slider.setReportChanges(true);
        slider.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override public void onSeekBarDrag(boolean stop, float progress) {
                selectedIndex[0] = speedSliderIndex(progress, options.length);
                value.setText(formatSpeedMultiplier(options[selectedIndex[0]]));
                // Snap to supported transfer presets, including accessibility steps.
                if (stop) slider.setProgress(selectedIndex[0] / (float) (options.length - 1), true);
            }
            @Override public int getStepsCount() { return options.length - 1; }
            @Override public CharSequence getContentDescription() {
                return getString(upload ? R.string.UploadSpeedBoostTitle : R.string.DownloadSpeedBoostTitle)
                        + ": " + formatSpeedMultiplier(options[selectedIndex[0]]);
            }
        });
        slider.setProgress(selectedIndex[0] / (float) (options.length - 1));
        container.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 0));

        FrameLayout ticks = new FrameLayout(context) {
            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                // Match label centers to the thumb track, not to equal-width cells.
                int inset = AndroidUtilities.dp(16);
                int trackWidth = Math.max(0, getWidth() - inset * 2);
                for (int i = 0; i < getChildCount(); i++) {
                    View tick = getChildAt(i);
                    int center = inset + Math.round(trackWidth * i / (float) (getChildCount() - 1));
                    int x = Math.max(0, Math.min(getWidth() - tick.getMeasuredWidth(), center - tick.getMeasuredWidth() / 2));
                    tick.layout(x, 0, x + tick.getMeasuredWidth(), tick.getMeasuredHeight());
                }
            }
        };
        for (int i = 0; i < options.length; i++) {
            TextView tick = new TextView(context);
            tick.setText(LocaleController.formatString(R.string.DownloadSpeedBoostValue, options[i]));
            tick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tick.setTextColor(Theme.getColor(Theme.key_dialogTextGray, resourceProvider));
            tick.setGravity(Gravity.CENTER);
            tick.setSingleLine(true);
            ticks.addView(tick, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        container.addView(ticks, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(upload ? R.string.UploadSpeedBoostTitle : R.string.DownloadSpeedBoostTitle));
        builder.setMessage(getString(upload ? R.string.UploadSpeedBoostInfo : R.string.DownloadSpeedBoostInfo));
        builder.setView(container);
        // Dragging only previews a value. Cancel/back leaves active transfers unchanged.
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            int selected = options[selectedIndex[0]];
            if (upload) {
                if (selected == SharedConfig.getUploadSpeedMultiplier()) return;
                SharedConfig.setUploadSpeedMultiplier(selected);
                refreshUploadSpeedSettings();
            } else {
                if (selected == SharedConfig.getDownloadSpeedMultiplier()) return;
                SharedConfig.setDownloadSpeedMultiplier(selected);
                refreshDownloadSpeedSettings();
            }
            if (listView != null && listView.adapter != null) listView.adapter.update(false);
            Toast.makeText(context, LocaleController.formatString(upload ? R.string.UploadSpeedBoostApplied : R.string.DownloadSpeedBoostApplied,
                    formatSpeedMultiplier(selected)), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showStickerMessageSizeDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        int[] options = SharedConfig.STICKER_MESSAGE_SIZE_PERCENTS;
        int current = SharedConfig.getStickerMessageSizePercent();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            String label = LocaleController.formatString(R.string.StickerMessageSizeValue, options[i]);
            if (options[i] == current) {
                label += " " + getString(R.string.DownloadSpeedBoostCurrentSuffix);
            }
            labels[i] = label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.StickerMessageSizeTitle));
        builder.setMessage(getString(R.string.StickerMessageSizeInfo));
        builder.setItems(labels, (dialog, which) -> {
            int selected = options[which];
            if (selected == SharedConfig.getStickerMessageSizePercent()) {
                return;
            }
            SharedConfig.setStickerMessageSizePercent(selected);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
            Toast.makeText(context, LocaleController.formatString(R.string.StickerMessageSizeApplied, SharedConfig.getStickerMessageSizeText()), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showMessageTextSizeDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        int[] options = SharedConfig.MESSAGE_TEXT_SIZES;
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            String label = LocaleController.formatString(R.string.MessageTextSizeValue, options[i]);
            if (options[i] == SharedConfig.fontSize) {
                label += " " + getString(R.string.DownloadSpeedBoostCurrentSuffix);
            }
            labels[i] = label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.MessageTextSizeTitle));
        builder.setMessage(getString(R.string.MessageTextSizeInfo));
        builder.setItems(labels, (dialog, which) -> {
            SharedConfig.setMessageTextSize(options[which]);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showTabletWidthRatioDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        int[] options = SharedConfig.TABLET_WIDTH_RATIO_PERCENTS;
        int current = SharedConfig.getTabletWidthRatioPercent();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            String label = LocaleController.formatString(R.string.TabletWidthRatioValue, options[i]);
            if (options[i] == current) {
                label += " " + getString(R.string.DownloadSpeedBoostCurrentSuffix);
            }
            labels[i] = label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.TabletWidthRatioTitle));
        builder.setMessage(getString(R.string.TabletWidthRatioInfo));
        builder.setItems(labels, (dialog, which) -> {
            int selected = options[which];
            if (selected == SharedConfig.getTabletWidthRatioPercent()) {
                return;
            }
            SharedConfig.setTabletWidthRatioPercent(selected);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
            AndroidUtilities.runOnUIThread(() -> {
                Activity activity = getParentActivity();
                if (activity != null) {
                    activity.recreate();
                }
            }, 150);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showDialogRowHeightDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        int[] options = SharedConfig.DIALOG_ROW_HEIGHT_PERCENTS;
        int current = SharedConfig.getDialogRowHeightPercent();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            String label = LocaleController.formatString(R.string.DialogRowHeightValue, options[i]);
            if (options[i] == current) {
                label += " " + getString(R.string.DownloadSpeedBoostCurrentSuffix);
            }
            labels[i] = label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.DialogRowHeightTitle));
        builder.setMessage(getString(R.string.DialogRowHeightInfo));
        builder.setItems(labels, (dialog, which) -> {
            int selected = options[which];
            if (selected == SharedConfig.getDialogRowHeightPercent()) {
                return;
            }
            SharedConfig.setDialogRowHeightPercent(selected);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showTranslationDisplayModeDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        int[] options = new int[]{
                SharedConfig.TRANSLATION_DISPLAY_TRANSLATED_ONLY,
                SharedConfig.TRANSLATION_DISPLAY_ORIGINAL_AND_TRANSLATED
        };
        CharSequence[] labels = new CharSequence[]{
                getString(R.string.TranslationDisplayTranslatedOnly),
                getString(R.string.TranslationDisplayOriginalAndTranslated)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.TranslationDisplayModeTitle));
        builder.setMessage(getString(R.string.TranslationDisplayModeInfo));
        builder.setItems(labels, (dialog, which) -> {
            if (options[which] == SharedConfig.getTranslationDisplayMode()) {
                return;
            }
            SharedConfig.setTranslationDisplayMode(options[which]);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCustomStarBalanceDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditTextBoldCursor editText = createDialogEditText(context, getString(R.string.CustomStarBalanceHint), 18);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (SharedConfig.hasCustomStarBalance()) {
            editText.setText(String.valueOf(SharedConfig.getCustomStarBalance()));
            editText.setSelection(editText.length());
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.CustomStarBalanceTitle));
        builder.setMessage(getString(R.string.CustomStarBalanceInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            try {
                SharedConfig.setCustomStarBalance(Long.parseLong(editText.getText().toString()));
            } catch (Exception e) {
                SharedConfig.setCustomStarBalance(-1);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            SharedConfig.setCustomStarBalance(-1);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starBalanceUpdated);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showClearLocalVirtualGiftsDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.ClearLocalVirtualGiftsTitle));
        builder.setMessage(getString(R.string.ClearLocalVirtualGiftsInfo));
        builder.setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
            SharedConfig.clearLocalVirtualGifts(currentAccount);
            StarsController.getInstance(currentAccount).invalidateLocalVirtualGifts();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showInputBarTextDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditTextBoldCursor editText = createDialogEditText(context, getString(R.string.InputBarTextHint), 40);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setText(SharedConfig.getInputBarText());
        editText.setSelection(editText.length());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.InputBarTextTitle));
        builder.setMessage(getString(R.string.InputBarTextInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            SharedConfig.setInputBarText(editText.getText().toString());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            SharedConfig.setInputBarText(null);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showBlockedUsersDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        KeywordMessageFilterManager manager = KeywordMessageFilterManager.getInstance(currentAccount);
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditTextBoldCursor editText = createDialogEditText(context, getString(R.string.KeywordFilterBlockedUsersHint), 2048);
        editText.setSingleLine(false);
        editText.setMinLines(4);
        editText.setMaxLines(8);
        editText.setImeOptions(EditorInfo.IME_ACTION_NONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setText(manager.getCustomBlockedUsersText());
        editText.setSelection(editText.length());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.KeywordFilterBlockedUsers));
        builder.setMessage(getString(R.string.KeywordFilterBlockedUsersInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            manager.setCustomBlockedUsersFromText(editText.getText().toString());
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNeutralButton(getString(R.string.Clear), (dialog, which) -> {
            manager.setCustomBlockedUsersFromText("");
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private EditTextBoldCursor createDialogEditText(Context context, String hint, int maxLength) {
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        editText.setHintText(hint);
        editText.setSingleLine(true);
        editText.setMaxLines(1);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        editText.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12));
        return editText;
    }

    private void showCustomDownloadPathDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.CustomDownloadPathTitle));
        builder.setMessage(getString(R.string.CustomDownloadPathInfo));
        builder.setPositiveButton(getString(R.string.CustomDownloadPathChoose), (dialog, which) -> openCustomDownloadPathPicker());
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            SharedConfig.setCustomDownloadPath(null);
            refreshMediaPaths();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCustomVideoWallpaperDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.CustomVideoWallpaperTitle));
        builder.setMessage(getString(R.string.CustomVideoWallpaperInfo));
        builder.setPositiveButton(getString(R.string.CustomVideoWallpaperChoose), (dialog, which) -> openCustomVideoWallpaperPicker());
        builder.setNeutralButton(getString(R.string.Reset), (dialog, which) -> {
            SharedConfig.setCustomVideoWallpaper(null, null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String getMangoUpdateSummary() {
        if (MangoUpdateManager.isChecking()) {
            return getString(R.string.MangoUpdateChecking);
        }
        if (MangoUpdateManager.isDownloading()) {
            return LocaleController.formatString(R.string.MangoUpdateDownloading, getMangoUpdateProgressPercent());
        }
        if (MangoUpdateManager.isDownloaded()) {
            return getString(R.string.MangoUpdateDownloaded);
        }
        if (!TextUtils.isEmpty(MangoUpdateManager.getError())) {
            return MangoUpdateManager.getError();
        }
        String remoteVersion = MangoUpdateManager.getRemoteVersion();
        if (MangoUpdateManager.isRemoteVersionNewer(remoteVersion)) {
            return LocaleController.formatString(R.string.MangoUpdateAvailable, remoteVersion, BuildVars.BUILD_VERSION_STRING);
        }
        return LocaleController.formatString(R.string.MangoUpdateCurrent, BuildVars.BUILD_VERSION_STRING);
    }

    private int getMangoUpdateProgressPercent() {
        return Math.max(0, Math.min(100, Math.round(MangoUpdateManager.getProgress() * 100f)));
    }

    private void onMangoUpdateStateChanged() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        if (updateProgressDialog != null) {
            int progress = getMangoUpdateProgressPercent();
            updateProgressDialog.setProgress(progress);
            updateProgressDialog.setMessage(LocaleController.formatString(R.string.MangoUpdateDownloading, progress));
            if (!MangoUpdateManager.isDownloading()) {
                if (MangoUpdateManager.isDownloaded()) {
                    updateProgressDialog.setMessage(getString(R.string.MangoUpdateDownloaded));
                    updateProgressDialog.setProgress(100);
                }
                if (!TextUtils.isEmpty(MangoUpdateManager.getError())) {
                    updateProgressDialog.setMessage(MangoUpdateManager.getError());
                }
            }
        }
    }

    private void showMangoUpdateDialog() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        if (MangoUpdateManager.isDownloaded()) {
            MangoUpdateManager.installDownloadedUpdate(activity);
            return;
        }
        if (MangoUpdateManager.isDownloading()) {
            showMangoUpdateProgressDialog();
            return;
        }
        if (MangoUpdateManager.isChecking()) {
            Toast.makeText(activity, getString(R.string.MangoUpdateChecking), Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER, resourceProvider);
        showDialog(progressDialog);
        MangoUpdateManager.checkForUpdates((hasUpdate, remoteVersion, error) -> {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (!TextUtils.isEmpty(error)) {
                Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasUpdate) {
                Toast.makeText(activity, getString(R.string.MangoUpdateNoUpdate), Toast.LENGTH_SHORT).show();
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourceProvider);
            builder.setTitle(getString(R.string.MangoUpdateTitle));
            builder.setMessage(LocaleController.formatString(R.string.MangoUpdateAvailable, remoteVersion, BuildVars.BUILD_VERSION_STRING));
            builder.setPositiveButton(getString(R.string.MangoUpdateDownload), (dialog, which) -> {
                MangoUpdateManager.downloadUpdate(activity);
                showMangoUpdateProgressDialog();
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
            showDialog(builder.create());
        });
    }

    private void showMangoUpdateProgressDialog() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        if (updateProgressDialog != null && updateProgressDialog.isShowing()) {
            onMangoUpdateStateChanged();
            return;
        }
        int progress = getMangoUpdateProgressPercent();
        AlertDialog dialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_LOADING, resourceProvider);
        dialog.setTitle(getString(R.string.MangoUpdateTitle));
        dialog.setMessage(LocaleController.formatString(R.string.MangoUpdateDownloading, progress));
        dialog.setProgress(progress);
        dialog.setNegativeButton(getString(R.string.MangoUpdateBackground), (alertDialog, which) -> {
            updateProgressDialog = null;
        });
        updateProgressDialog = dialog;
        showDialog(dialog, dismissDialog -> {
            if (updateProgressDialog == dialog) {
                updateProgressDialog = null;
            }
        });
        onMangoUpdateStateChanged();
    }

    private void openCustomDownloadPathPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_CUSTOM_DOWNLOAD_PATH);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(getParentActivity(), getString(R.string.CustomDownloadPathInvalid), Toast.LENGTH_SHORT).show();
        }
    }

    private void openCustomVideoWallpaperPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_CUSTOM_VIDEO_WALLPAPER);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(getParentActivity(), getString(R.string.CustomVideoWallpaperInvalid), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CUSTOM_VIDEO_WALLPAPER) {
            handleCustomVideoWallpaperResult(resultCode, data);
            return;
        }
        if (requestCode != REQUEST_CODE_CUSTOM_DOWNLOAD_PATH || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= 19) {
                context.getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        String treeUri = uri.toString();
        String path = getWritableFolderPath(context, uri);
        boolean filePathUsable = false;
        if (!TextUtils.isEmpty(path)) {
            try {
                File dir = new File(path);
                filePathUsable = (dir.exists() || dir.mkdirs()) && dir.isDirectory();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        boolean treeUriUsable = CustomDownloadPathHelper.canWriteTreeUri(context, treeUri);
        if (!filePathUsable && !treeUriUsable) {
            Toast.makeText(context, getString(R.string.CustomDownloadPathInvalid), Toast.LENGTH_SHORT).show();
            return;
        }
        SharedConfig.setCustomDownloadPath(TextUtils.isEmpty(path) ? treeUri : path, treeUri);
        refreshMediaPaths();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        Toast.makeText(context, getString(R.string.CustomDownloadPathApplied), Toast.LENGTH_SHORT).show();
    }

    private void handleCustomVideoWallpaperResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (Build.VERSION.SDK_INT >= 19) {
                context.getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        String path = AndroidUtilities.getPath(uri);
        SharedConfig.setCustomVideoWallpaper(path, uri.toString());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        Toast.makeText(context, getString(R.string.CustomVideoWallpaperApplied), Toast.LENGTH_SHORT).show();
    }

    private String getWritableFolderPath(Context context, Uri uri) {
        String path = AndroidUtilities.getPath(uri);
        if (!TextUtils.isEmpty(path)) {
            return path;
        }
        try {
            if (Build.VERSION.SDK_INT >= 21 && DocumentsContract.isTreeUri(uri)) {
                String treeId = DocumentsContract.getTreeDocumentId(uri);
                if (!TextUtils.isEmpty(treeId)) {
                    String[] split = treeId.split(":", 2);
                    if (split.length > 0 && "primary".equalsIgnoreCase(split[0])) {
                        String relativePath = split.length > 1 ? split[1] : "";
                        File base = Environment.getExternalStorageDirectory();
                        return TextUtils.isEmpty(relativePath) ? base.getAbsolutePath() : new File(base, relativePath).getAbsolutePath();
                    } else if (split.length > 0) {
                        String relativePath = split.length > 1 ? split[1] : "";
                        File[] dirs = context.getExternalFilesDirs(null);
                        if (dirs != null) {
                            for (File dir : dirs) {
                                if (dir == null) {
                                    continue;
                                }
                                String dirPath = dir.getAbsolutePath();
                                int androidData = dirPath.indexOf("/Android/data/");
                                if (androidData <= 0) {
                                    continue;
                                }
                                File base = new File(dirPath.substring(0, androidData));
                                if (base.getName().equalsIgnoreCase(split[0]) || dirPath.startsWith("/storage/" + split[0] + "/")) {
                                    return TextUtils.isEmpty(relativePath) ? base.getAbsolutePath() : new File(base, relativePath).getAbsolutePath();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void showUploadSpeedDialog() {
        showTransferSpeedDialog(true);
    }

    private void refreshDownloadSpeedSettings() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            FileLoader.getInstance(account).refreshDownloadSpeedSettings();
        }
    }

    private void refreshUploadSpeedSettings() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            FileLoader.getInstance(account).refreshUploadSpeedSettings();
        }
    }

    private void refreshMediaPaths() {
        ImageLoader.getInstance().checkMediaPaths();
    }
}
