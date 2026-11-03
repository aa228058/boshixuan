package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.CrossAccountInboxStore;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoSettings;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

/** A live view over account-owned dialog caches. No messages are copied between accounts. */
public class CrossAccountInboxActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate,
        NotificationCenter.NotificationCenterDelegate {
    public static final String SETTING_KEY = "cross_account_inbox";
    private static final int ITEM_UNREAD = 1, ITEM_MORE = 2, ITEM_MESSAGE = 3, ITEM_REPLY_ROW = 5, ITEM_REPLY_HANDLED = 6, ITEM_REPLY_SEND = 7, ITEM_SCOPE = 8, ITEM_SHOW_HANDLED = 9;
    private final boolean inMainTabs;
    private UniversalRecyclerView listView;
    private ItemTouchHelper replySwipeHelper;
    private final ArrayList<Integer> observedAccounts = new ArrayList<>();
    /** Keep a snapshot so repeated account notifications do not rebuild every row. */
    private final ArrayList<Entry> cachedRows = new ArrayList<>();
    private boolean snapshotDirty = true;
    private final HashSet<Integer> dirtyAccounts = new HashSet<>();
    private boolean cachedUnreadOnly;
    private boolean cachedHasMore;
    private boolean unreadOnly;
    private boolean showHandledReplies;
    private boolean active;
    private boolean pendingSingleDialogRefresh;
    private int pendingAccount = -1;
    private long pendingDialogId;
    private int pendingOldRowIndex = -1;
    private int pendingOldRowCount = -1;
    private final Runnable refresh = this::runRefresh;

    private static class Entry {
        int account, date, unread, messageId;
        long owner, dialogId;
        String accountName, title, preview;
        TLRPC.Dialog dialog;
        MessageObject latestMessage;
        int topMessage, unreadMentions, unreadReactions, folderId, pinnedNum;
        boolean pinned, unreadMark;
        Object draftState, notifySettingsState;
        boolean privatePreview;
    }

    public CrossAccountInboxActivity() { this(false); }
    public CrossAccountInboxActivity(boolean inMainTabs) { this.inMainTabs = inMainTabs; }

    public static boolean isEnabled(int account) {
        return MangoSettings.getBoolean(account, SETTING_KEY, true);
    }

    /** Account-scoped switch used by both the extension page and the bottom tab. */
    public static void setEnabled(int account, boolean enabled) {
        MangoSettings.setBoolean(account, SETTING_KEY, enabled);
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoUnifiedInbox));
        if (!inMainTabs) actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setClipToPadding(false);
        replySwipeHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getAdapterPosition() == RecyclerView.NO_POSITION) return 0;
                UItem item = listView.adapter.getItem(viewHolder.getAdapterPosition());
                return item != null && item.id == ITEM_REPLY_ROW
                        ? super.getSwipeDirs(recyclerView, viewHolder) : 0;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || listView == null || listView.adapter == null) return;
                UItem item = listView.adapter.getItem(position);
                if (item == null || item.id != ITEM_REPLY_ROW
                        || !(item.object instanceof CrossAccountInboxStore.Entry)) {
                    listView.adapter.notifyItemChanged(position);
                    return;
                }
                CrossAccountInboxStore.Entry reply = (CrossAccountInboxStore.Entry) item.object;
                // Swipe left marks handled; swipe right restores the pending state.
                CrossAccountInboxStore.setHandled(reply.account, reply.ownerId, reply.dialogId,
                        reply.messageId, direction == ItemTouchHelper.LEFT);
            }
        });
        replySwipeHelper.attachToRecyclerView(listView);
        updatePadding(AndroidUtilities.navigationBarHeight);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);
        if (inMainTabs) ViewCompat.setOnApplyWindowInsetsListener(content, this::onInsetsInternal);
        return fragmentView = content;
    }

    private void updatePadding(int bottomInset) {
        if (listView == null) return;
        int top = inMainTabs ? ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) : 0;
        listView.setPadding(0, top, 0, bottomInset + (inMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0));
    }

    @NonNull @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat insets) {
        updatePadding(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
        return super.onInsetsInternal(view, insets);
    }

    @Override public void onResume() {
        super.onResume();
        active = true;
        snapshotDirty = true;
        clearObservers();
        for (int account : UserConfig.getActivatedAccounts()) {
            observedAccounts.add(account);
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(this, NotificationCenter.dialogsNeedReload);
            center.addObserver(this, NotificationCenter.updateInterfaces);
            center.addObserver(this, NotificationCenter.didReceiveNewMessages);
            center.addObserver(this, NotificationCenter.crossAccountInboxUpdated);
            center.addObserver(this, NotificationCenter.messagesDeleted);
            center.addObserver(this, NotificationCenter.notificationsCountUpdated);
            center.addObserver(this, NotificationCenter.appDidLogout);
            MessagesController controller = MessagesController.getInstance(account);
            if (controller.getAllDialogs().isEmpty()) {
                controller.loadDialogs(0, 0, 100, true);
                controller.loadDialogs(1, 0, 100, true);
            }
        }
        refresh.run();
    }

    private void clearObservers() {
        for (int account : observedAccounts) {
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.removeObserver(this, NotificationCenter.dialogsNeedReload);
            center.removeObserver(this, NotificationCenter.updateInterfaces);
            center.removeObserver(this, NotificationCenter.didReceiveNewMessages);
            center.removeObserver(this, NotificationCenter.crossAccountInboxUpdated);
            center.removeObserver(this, NotificationCenter.messagesDeleted);
            center.removeObserver(this, NotificationCenter.notificationsCountUpdated);
            center.removeObserver(this, NotificationCenter.appDidLogout);
        }
        observedAccounts.clear();
    }
    @Override public void onPause() {
        active = false;
        pendingSingleDialogRefresh = false;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        clearObservers();
        super.onPause();
    }
    @Override public void onFragmentDestroy() {
        active = false;
        pendingSingleDialogRefresh = false;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        clearObservers();
        super.onFragmentDestroy();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.crossAccountInboxUpdated) {
            // Store writes are serialized; this event is emitted only after persistence.
            queueRefresh();
            return;
        }
        if (id == NotificationCenter.didReceiveNewMessages) {
            // CrossAccountInboxStore posts a separate update after persistence; the
            // regular dialog summary can still use its targeted row update below.
            // New-message notifications identify their dialog. Updating one snapshot
            // row avoids walking and sorting every dialog for high-traffic accounts.
            if (args != null && args.length > 2 && args[0] instanceof Long
                    && args[2] instanceof Boolean && !(Boolean) args[2]
                    && !snapshotDirty && dirtyAccounts.isEmpty() && cachedUnreadOnly == unreadOnly) {
                long dialogId = (Long) args[0];
                int oldRowIndex = findDialogRowIndexInAdapter(account, dialogId);
                int oldRowCount = cachedRows.size();
                if (updateDialogSnapshot(account, dialogId)) {
                    queueSingleDialogRefresh(account, dialogId, oldRowIndex, oldRowCount);
                } else {
                    markSnapshotDirty();
                }
                return;
            }
            markSnapshotDirty();
            return;
        }
        if (id == NotificationCenter.dialogsNeedReload) {
            markAccountSnapshotDirty(account);
            return;
        }
        if (id == NotificationCenter.messagesDeleted) {
            // Remove captured replies for this exact dialog as well as refreshing
            // the ordinary dialog summary below.
            if (args != null && args.length > 1 && args[0] instanceof ArrayList
                    && args[1] instanceof Long && (Long) args[1] != 0) {
                CrossAccountInboxStore.removeDeletedMessages(account, -((Long) args[1]),
                        (ArrayList<Integer>) args[0]);
            }
            // Channel deletions carry the owning channel id and can refresh one row.
            // Other deletion paths omit a dialog id, so rebuild only this account.
            if (args != null && args.length > 1 && args[1] instanceof Long && (Long) args[1] != 0
                    && !snapshotDirty && dirtyAccounts.isEmpty() && cachedUnreadOnly == unreadOnly) {
                long dialogId = -(Long) args[1];
                int oldRowIndex = findDialogRowIndexInAdapter(account, dialogId);
                int oldRowCount = cachedRows.size();
                if (updateDialogSnapshot(account, dialogId)) {
                    queueSingleDialogRefresh(account, dialogId, oldRowIndex, oldRowCount);
                } else {
                    markAccountSnapshotDirty(account);
                }
            } else {
                markAccountSnapshotDirty(account);
            }
            return;
        }
        if (id == NotificationCenter.appDidLogout) {
            markAccountSnapshotDirty(account);
            return;
        }
        // This is an aggregate notification badge, not a dialog unread-count change.
        // It has no effect on any row in this view.
        if (id == NotificationCenter.notificationsCountUpdated) return;

        boolean affectsRows = false;
        if (id == NotificationCenter.updateInterfaces && args != null && args.length > 0 && args[0] instanceof Integer) {
            int mask = (Integer) args[0];
            boolean readStateChanged = (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0;
            affectsRows = (mask & (MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME
                    | MessagesController.UPDATE_MASK_CHAT)) != 0 || unreadOnly && readStateChanged;
            int visualMask = MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_STATUS
                    | MessagesController.UPDATE_MASK_CHAT_AVATAR | MessagesController.UPDATE_MASK_CHAT_MEMBERS
                    | MessagesController.UPDATE_MASK_USER_PRINT | MessagesController.UPDATE_MASK_USER_PHONE
                    | MessagesController.UPDATE_MASK_PHONE | MessagesController.UPDATE_MASK_EMOJI_INTERACTIONS
                    | MessagesController.UPDATE_MASK_EMOJI_STATUS | MessagesController.UPDATE_MASK_REACTIONS_READ;
            if (!affectsRows && ((mask & visualMask) != 0 || readStateChanged && !unreadOnly)) {
                // DialogCell reads its bound dialog object directly; a read marker
                // update does not change summary ordering or its row preview.
                updateVisibleDialogCells(mask);
            }
        }
        if (!affectsRows) return;
        markAccountSnapshotDirty(account);
    }

    private void markSnapshotDirty() {
        snapshotDirty = true;
        dirtyAccounts.clear();
        queueRefresh();
    }

    private void markAccountSnapshotDirty(int account) {
        if (snapshotDirty) {
            queueRefresh();
            return;
        }
        dirtyAccounts.add(account);
        queueRefresh();
    }

    private void queueRefresh() {
        pendingSingleDialogRefresh = false;
        AndroidUtilities.cancelRunOnUIThread(refresh);
        AndroidUtilities.runOnUIThread(refresh, 150);
    }

    private void queueSingleDialogRefresh(int account, long dialogId, int oldRowIndex, int oldRowCount) {
        if (!pendingSingleDialogRefresh) {
            pendingSingleDialogRefresh = true;
            pendingAccount = account;
            pendingDialogId = dialogId;
            pendingOldRowIndex = oldRowIndex;
            pendingOldRowCount = oldRowCount;
        } else if (pendingAccount != account || pendingDialogId != dialogId) {
            // Multiple dialogs changed before the debounce elapsed. Keep the safe
            // full diff path for that batch; a targeted move can only describe one row.
            snapshotDirty = true;
            queueRefresh();
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(refresh);
        AndroidUtilities.runOnUIThread(refresh, 150);
    }

    private void runRefresh() {
        if (!active || listView == null || isFinished) {
            pendingSingleDialogRefresh = false;
            return;
        }
        if (!pendingSingleDialogRefresh) {
            listView.adapter.update(true);
            return;
        }

        int oldRowIndex = pendingOldRowIndex;
        int oldRowCount = pendingOldRowCount;
        int account = pendingAccount;
        long dialogId = pendingDialogId;
        int newRowIndex = findDialogRowIndex(account, dialogId);
        pendingSingleDialogRefresh = false;
        pendingAccount = -1;
        pendingDialogId = 0;
        pendingOldRowIndex = -1;
        pendingOldRowCount = -1;

        // The empty-state placeholder occupies the same slot as the first row.
        // Let the standard diff handle transitions to/from that state. Also guard
        // the More row at the tail in case pagination state changed concurrently.
        if ((oldRowCount == 0) != cachedRows.isEmpty() || oldRowIndex < 0 && cachedRows.size() == 1) {
            listView.adapter.update(true);
            return;
        }
        if (listView.isComputingLayout()) {
            pendingSingleDialogRefresh = true;
            pendingAccount = account;
            pendingDialogId = dialogId;
            pendingOldRowIndex = oldRowIndex;
            pendingOldRowCount = oldRowCount;
            listView.post(refresh);
            return;
        }

        // Reply controls are rendered before dialog rows, so their count is dynamic.
        // Resolve the actual first dialog-row position instead of assuming a fixed offset.
        int dialogStart = findDialogRowsStartPosition();
        if (dialogStart < 0) {
            listView.adapter.update(true);
            return;
        }

        // Keep the adapter's existing array and replace/move only this one dialog row.
        boolean updated;
        if (oldRowIndex >= 0 && newRowIndex >= 0) {
            updated = listView.adapter.replaceAndMoveItem(dialogStart + oldRowIndex,
                    dialogStart + newRowIndex, createRowItem(cachedRows.get(newRowIndex)));
        } else if (oldRowIndex >= 0) {
            updated = listView.adapter.removeItemAt(dialogStart + oldRowIndex);
        } else if (newRowIndex >= 0) {
            updated = listView.adapter.insertItemAt(dialogStart + newRowIndex, createRowItem(cachedRows.get(newRowIndex)));
        } else {
            updated = true;
        }
        if (!updated) listView.adapter.update(true);
    }

    private int findDialogRowsStartPosition() {
        if (listView == null || listView.adapter == null) return -1;
        for (int i = 0; i < listView.adapter.getItemCount(); i++) {
            UItem item = listView.adapter.getItem(i);
            if (item != null && item.id == ITEM_MESSAGE) return i;
        }
        return -1;
    }

    private int findDialogRowIndexInAdapter(int account, long dialogId) {
        // Adapter positions include filter controls and a variable number of reply rows.
        // The cached row index is the stable index used by the targeted diff path.
        return findDialogRowIndex(account, dialogId);
    }

    private int findDialogRowIndex(int account, long dialogId) {
        for (int i = 0; i < cachedRows.size(); i++) {
            Entry entry = cachedRows.get(i);
            if (entry.account == account && entry.dialogId == dialogId) return i;
        }
        return -1;
    }

    private void updateVisibleDialogCells(int mask) {
        if (listView == null || listView.getChildCount() == 0) return;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (!(child instanceof InboxDialogCell)) continue;
            InboxDialogCell row = (InboxDialogCell) child;
            if (row.dialogCell != null) {
                row.dialogCell.update(mask, false);
                row.invalidate();
            }
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // The unified inbox is a filtered message list, not a duplicate of every
        // account's ordinary dialog list. Only captured messages passing the
        // selected scope are shown.
        items.add(UItem.asButton(ITEM_SCOPE, R.drawable.msg_retry,
                "筛选设置 · " + getReplyFilterSummary()));

        ArrayList<CrossAccountInboxStore.Entry> replies = CrossAccountInboxStore.getAllEntries();
        int shownReplies = 0;
        for (CrossAccountInboxStore.Entry reply : replies) {
            if (reply.handled || !matchesReplyFilters(reply)) continue;
            String accountLabel = "账号 " + (reply.account + 1);
            String title = TextUtils.isEmpty(reply.groupTitle)
                    ? (reply.dialogId < 0 ? "群组消息" : "私聊消息") : reply.groupTitle;
            String typeLabel = reply.messageType == 2 ? "媒体消息" : "文字消息";
            String sourceLabel = reply.dialogId < 0 ? "群组回复" : "私聊消息";
            String detail = accountLabel + " · " + sourceLabel + " · " + typeLabel
                    + (TextUtils.isEmpty(reply.senderName) ? "" : " · " + reply.senderName)
                    + (reply.date > 0 ? " · " + LocaleController.formatDateChat(reply.date) : "")
                    + "\n" + reply.preview;
            UItem row = UItem.asButton(ITEM_REPLY_ROW, R.drawable.msg_retry, title);
            row.subtext = detail;
            row.object = reply;
            items.add(row);
            shownReplies++;
        }
        if (shownReplies == 0) {
            items.add(UItem.asShadow("当前筛选条件下没有符合条件的消息"));
        }
    }

    private UItem createRowItem(Entry row) {
        String title = row.title + (row.unread > 0 ? " · " + LocaleController.formatString(R.string.MangoUnifiedUnreadCount, row.unread) : "");
        String detail = LocaleController.formatString(R.string.MangoUnifiedAccount, row.accountName)
                + (row.date > 0 ? " · " + LocaleController.formatDateChat(row.date) : "") + "\n" + row.preview;
        UItem item = InboxRowFactory.of(ITEM_MESSAGE, title, detail);
        item.object = row;
        return item;
    }

    /** Rebuild only after a source notification, avoiding repeated O(accounts * dialogs) work. */
    private void rebuildSnapshot() {
        HashMap<Integer, ArrayList<Entry>> previousSortedRows = new HashMap<>();
        HashMap<Integer, HashMap<Long, Entry>> previousRows = indexCachedRows(previousSortedRows);
        ArrayList<Entry> mergedRows = new ArrayList<>();
        cachedHasMore = false;
        cachedUnreadOnly = unreadOnly;
        for (int account : UserConfig.getActivatedAccounts()) {
            if (hasMoreDialogs(account)) cachedHasMore = true;
            mergedRows = mergeSortedRows(mergedRows, buildRowsForAccount(account, previousRows.get(account), previousSortedRows.get(account)));
        }
        cachedRows.clear();
        cachedRows.addAll(mergedRows);
        dirtyAccounts.clear();
        snapshotDirty = false;
    }

    /** Reconcile only changed account caches, then linearly merge them with retained sorted rows. */
    private void rebuildDirtySnapshots() {
        if (dirtyAccounts.isEmpty()) return;
        HashSet<Integer> rebuilding = new HashSet<>(dirtyAccounts);
        HashMap<Integer, ArrayList<Entry>> previousSortedRows = new HashMap<>();
        HashMap<Integer, HashMap<Long, Entry>> previousRows = indexCachedRows(previousSortedRows);
        ArrayList<Entry> mergedRows = new ArrayList<>(cachedRows.size());
        for (Entry row : cachedRows) {
            if (!rebuilding.contains(row.account)) mergedRows.add(row);
        }
        for (int account : rebuilding) {
            mergedRows = mergeSortedRows(mergedRows, buildRowsForAccount(account, previousRows.get(account), previousSortedRows.get(account)));
        }
        cachedRows.clear();
        cachedRows.addAll(mergedRows);
        refreshHasMoreDialogs();
        dirtyAccounts.removeAll(rebuilding);
        cachedUnreadOnly = unreadOnly;
    }

    private HashMap<Integer, HashMap<Long, Entry>> indexCachedRows(HashMap<Integer, ArrayList<Entry>> sortedRows) {
        HashMap<Integer, HashMap<Long, Entry>> indexed = new HashMap<>();
        for (Entry old : cachedRows) {
            HashMap<Long, Entry> accountRows = indexed.get(old.account);
            if (accountRows == null) {
                accountRows = new HashMap<>();
                indexed.put(old.account, accountRows);
            }
            accountRows.put(old.dialogId, old);
            if (sortedRows != null) {
                ArrayList<Entry> ordered = sortedRows.get(old.account);
                if (ordered == null) {
                    ordered = new ArrayList<>();
                    sortedRows.put(old.account, ordered);
                }
                ordered.add(old);
            }
        }
        return indexed;
    }

    private ArrayList<Entry> buildRowsForAccount(int account, HashMap<Long, Entry> previousRows, ArrayList<Entry> previousSortedRows) {
        ArrayList<Entry> accountRows = new ArrayList<>();
        boolean orderChanged = false;
        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) return accountRows;
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User me = config.getCurrentUser();
        String username = UserObject.getPublicUsername(me);
        String accountName = (me == null ? "" : UserObject.getUserName(me))
                + " · " + (TextUtils.isEmpty(username) ? Long.toString(config.getClientUserId()) : "@" + username);
        for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
            Entry row = createEntry(account, config, controller, accountName, dialog);
            if (row != null) {
                Entry previous = previousRows == null ? null : previousRows.remove(dialog.id);
                if (previous == null || previous.date != row.date) orderChanged = true;
                if (previous != null && sameEntryContents(previous, row)) {
                    // UniversalAdapter compares row payload identity. Reusing the
                    // old payload avoids rebinding unchanged dialogs in this account.
                    accountRows.add(previous);
                } else {
                    accountRows.add(row);
                }
            }
        }
        if (previousRows != null && !previousRows.isEmpty()) orderChanged = true;
        if (!orderChanged && previousSortedRows != null && previousSortedRows.size() == accountRows.size()) {
            HashMap<Long, Entry> updatedByDialog = new HashMap<>(accountRows.size());
            for (Entry row : accountRows) updatedByDialog.put(row.dialogId, row);
            ArrayList<Entry> orderedRows = new ArrayList<>(accountRows.size());
            for (Entry previous : previousSortedRows) {
                Entry updated = updatedByDialog.remove(previous.dialogId);
                if (updated != null) orderedRows.add(updated);
            }
            if (orderedRows.size() == accountRows.size()) return orderedRows;
        }
        accountRows.sort(Comparator.comparingInt((Entry e) -> e.date).reversed()
                .thenComparingInt(e -> e.account).thenComparingLong(e -> e.dialogId));
        return accountRows;
    }

    private ArrayList<Entry> mergeSortedRows(ArrayList<Entry> first, ArrayList<Entry> second) {
        ArrayList<Entry> merged = new ArrayList<>(first.size() + second.size());
        int left = 0;
        int right = 0;
        while (left < first.size() && right < second.size()) {
            Entry a = first.get(left);
            Entry b = second.get(right);
            int comparison = Integer.compare(b.date, a.date);
            if (comparison == 0) comparison = Integer.compare(a.account, b.account);
            if (comparison == 0) comparison = Long.compare(a.dialogId, b.dialogId);
            if (comparison <= 0) {
                merged.add(a);
                left++;
            } else {
                merged.add(b);
                right++;
            }
        }
        while (left < first.size()) merged.add(first.get(left++));
        while (right < second.size()) merged.add(second.get(right++));
        return merged;
    }

    private void refreshHasMoreDialogs() {
        cachedHasMore = false;
        for (int account : UserConfig.getActivatedAccounts()) {
            if (hasMoreDialogs(account)) {
                cachedHasMore = true;
                return;
            }
        }
    }

    private boolean hasMoreDialogs(int account) {
        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) return false;
        MessagesController controller = MessagesController.getInstance(account);
        return !controller.isServerDialogsEndReached(0) || !controller.isServerDialogsEndReached(1);
    }

    private boolean sameEntryContents(Entry left, Entry right) {
        return left.account == right.account
                && left.owner == right.owner
                && left.dialogId == right.dialogId
                && left.dialog == right.dialog
                && left.latestMessage == right.latestMessage
                && left.topMessage == right.topMessage
                && left.unreadMentions == right.unreadMentions
                && left.unreadReactions == right.unreadReactions
                && left.folderId == right.folderId
                && left.pinnedNum == right.pinnedNum
                && left.pinned == right.pinned
                && left.unreadMark == right.unreadMark
                && left.draftState == right.draftState
                && left.notifySettingsState == right.notifySettingsState
                && left.date == right.date
                && left.messageId == right.messageId
                && left.unread == right.unread
                && left.privatePreview == right.privatePreview
                && TextUtils.equals(left.accountName, right.accountName)
                && TextUtils.equals(left.title, right.title)
                && TextUtils.equals(left.preview, right.preview);
    }

    private Entry createEntry(int account, UserConfig config, MessagesController controller, String accountName, TLRPC.Dialog dialog) {
        if (dialog == null || dialog.id == 0 || DialogObject.isFolderDialogId(dialog.id)
                || unreadOnly && dialog.unread_count == 0 && !dialog.unread_mark) return null;
        MessageObject latest = null;
        // MessagesController keeps the dialog top message indexed by id;
        // use it first instead of scanning every cached message in a dialog.
        if (!DialogObject.isChannel(dialog) && dialog.top_message != 0) {
            latest = controller.dialogMessagesByIds.get(dialog.top_message);
            if (latest != null && (latest.messageOwner == null || latest.deleted)) latest = null;
        }
        if (latest == null) {
            ArrayList<MessageObject> messages = controller.dialogMessage.get(dialog.id);
            if (messages != null) {
                // The dialog cache is normally just the top message (or one album).
                // Prefer its explicit top-message id and stop immediately rather than
                // date-scanning every cached message for every account row.
                if (dialog.top_message != 0) {
                    for (int i = 0; i < messages.size(); i++) {
                        MessageObject message = messages.get(i);
                        if (message != null && message.getId() == dialog.top_message
                                && message.messageOwner != null && !message.deleted) {
                            latest = message;
                            break;
                        }
                    }
                }
                if (latest == null) {
                    for (MessageObject message : messages) {
                        if (message == null || message.messageOwner == null || message.deleted) continue;
                        if (latest == null || message.messageOwner.date > latest.messageOwner.date
                                || message.messageOwner.date == latest.messageOwner.date && message.getId() > latest.getId()) latest = message;
                    }
                }
            }
        }
        Entry row = new Entry();
        row.account = account;
        row.owner = config.getClientUserId();
        row.accountName = accountName;
        row.dialogId = dialog.id;
        row.dialog = dialog;
        row.latestMessage = latest;
        row.topMessage = dialog.top_message;
        row.unreadMentions = dialog.unread_mentions_count;
        row.unreadReactions = dialog.unread_reactions_count;
        row.folderId = dialog.folder_id;
        row.pinnedNum = dialog.pinnedNum;
        row.pinned = dialog.pinned;
        row.unreadMark = dialog.unread_mark;
        row.draftState = dialog.draft;
        row.notifySettingsState = dialog.notify_settings;
        row.date = latest == null ? dialog.last_message_date : latest.messageOwner.date;
        row.messageId = latest == null ? 0 : latest.getId();
        row.unread = dialog.unread_count;
        if (DialogObject.isEncryptedDialog(dialog.id)) row.title = getString(R.string.SecretChat);
        else if (dialog.id == config.getClientUserId()) row.title = getString(R.string.SavedMessages);
        else if (dialog.id < 0) {
            TLRPC.Chat chat = controller.getChat(-dialog.id);
            row.title = chat == null ? Long.toString(dialog.id) : chat.title;
        } else {
            TLRPC.User user = controller.getUser(dialog.id);
            row.title = user == null ? Long.toString(dialog.id) : UserObject.getUserName(user);
        }
        CharSequence preview = latest == null ? null : !TextUtils.isEmpty(latest.caption) ? latest.caption : latest.messageText;
        if (DialogObject.isEncryptedDialog(dialog.id) || latest != null && latest.isSecretMedia()) {
            row.privatePreview = true;
            row.preview = getString(R.string.MessageTagPrivateMessage);
        } else {
            row.preview = TextUtils.isEmpty(preview) ? getString(R.string.MangoUnifiedOpenChat)
                    : preview.subSequence(0, Math.min(200, preview.length())).toString().replace('\n', ' ');
        }
        return row;
    }

    private boolean updateDialogSnapshot(int account, long dialogId) {
        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) return false;
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
        if (dialog == null) return false;
        TLRPC.User me = config.getCurrentUser();
        String username = UserObject.getPublicUsername(me);
        String accountName = (me == null ? "" : UserObject.getUserName(me))
                + " · " + (TextUtils.isEmpty(username) ? Long.toString(config.getClientUserId()) : "@" + username);
        int oldIndex = findDialogRowIndex(account, dialogId);
        if (oldIndex >= 0) cachedRows.remove(oldIndex);
        Entry updated = createEntry(account, config, controller, accountName, dialog);
        if (updated != null) {
            int low = 0;
            int high = cachedRows.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                Entry current = cachedRows.get(middle);
                int comparison = Integer.compare(current.date, updated.date);
                if (comparison == 0) comparison = Integer.compare(updated.account, current.account);
                if (comparison == 0) comparison = Long.compare(updated.dialogId, current.dialogId);
                if (comparison < 0) high = middle;
                else low = middle + 1;
            }
            cachedRows.add(low, updated);
        }
        return true;
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ITEM_SCOPE) {
            showInboxFilterMenu();
        } else if (item.id == ITEM_REPLY_ROW && item.object instanceof CrossAccountInboxStore.Entry) {
            openReply((CrossAccountInboxStore.Entry) item.object);
        } else if (item.id == ITEM_REPLY_SEND && item.object instanceof CrossAccountInboxStore.Entry) {
            showQuickReply((CrossAccountInboxStore.Entry) item.object);
        } else if (item.id == ITEM_REPLY_HANDLED && item.object instanceof CrossAccountInboxStore.Entry) {
            CrossAccountInboxStore.Entry reply = (CrossAccountInboxStore.Entry) item.object;
            CrossAccountInboxStore.setHandled(reply.account, reply.ownerId, reply.dialogId,
                    reply.messageId, !reply.handled);
        } else if (item.id == ITEM_UNREAD) {
            unreadOnly = !unreadOnly;
            snapshotDirty = true;
            listView.adapter.update(true);
        } else if (item.id == ITEM_SHOW_HANDLED) {
            showHandledReplies = !showHandledReplies;
            listView.adapter.update(true);
        } else if (item.id == ITEM_MORE) {
            for (int account : UserConfig.getActivatedAccounts()) {
                MessagesController controller = MessagesController.getInstance(account);
                for (int folder = 0; folder <= 1; folder++) {
                    if (!controller.isServerDialogsEndReached(folder) && !controller.isLoadingDialogs(folder))
                        controller.loadDialogs(folder, 0, 100, false);
                }
            }
            snapshotDirty = true;
        } else if (item.id == ITEM_MESSAGE && item.object instanceof Entry) {
            open((Entry) item.object);
        }
    }

    private static final String INBOX_SCOPE_KEY = "cross_account_inbox_scope_accounts";
    private static final String INBOX_GROUPS_KEY = "cross_account_inbox_scope_groups";
    private static final String INBOX_TYPE_KEY = "cross_account_inbox_reply_type";

    private java.util.HashSet<Integer> getSelectedInboxAccounts() {
        java.util.HashSet<Integer> active = new java.util.HashSet<>();
        for (int account : UserConfig.getActivatedAccounts()) active.add(account);
        java.util.HashSet<Integer> selected = new java.util.HashSet<>();
        String saved = ApplicationLoader.applicationContext.getSharedPreferences(
                "cross_account_inbox_ui", Context.MODE_PRIVATE).getString(INBOX_SCOPE_KEY, "");
        if (TextUtils.isEmpty(saved)) {
            selected.addAll(active);
            return selected;
        }
        String[] values = saved.split(",");
        for (String value : values) {
            try {
                int account = Integer.parseInt(value);
                if (active.contains(account)) selected.add(account);
            } catch (Exception ignore) {
            }
        }
        // If all saved accounts were removed or logged out, recover to all
        // currently active accounts instead of presenting an empty inbox.
        if (selected.isEmpty()) selected.addAll(active);
        return selected;
    }

    private String getInboxScopeLabel() {
        java.util.HashSet<Integer> selected = getSelectedInboxAccounts();
        int activeCount = UserConfig.getActivatedAccounts().length;
        return selected.size() == activeCount ? "全部账号" : selected.size() + "/" + activeCount + " 个账号";
    }

    private void showInboxAccountPicker() {
        if (getParentActivity() == null) return;
        int[] accounts = UserConfig.getActivatedAccounts();
        if (accounts.length == 0) return;
        java.util.HashSet<Integer> selected = getSelectedInboxAccounts();
        CharSequence[] labels = new CharSequence[accounts.length];
        boolean[] checked = new boolean[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            int account = accounts[i];
            TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
            String name = user == null ? "账号 " + (account + 1) : UserObject.getUserName(user);
            labels[i] = name + "（账号 " + (account + 1) + "）";
            checked[i] = selected.contains(account);
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle("选择汇总账号")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StringBuilder value = new StringBuilder();
                    int count = 0;
                    for (int i = 0; i < accounts.length; i++) {
                        if (!checked[i]) continue;
                        if (value.length() > 0) value.append(',');
                        value.append(accounts[i]);
                        count++;
                    }
                    if (count == 0) {
                        new AlertDialog.Builder(getParentActivity())
                                .setMessage("至少选择一个账号。")
                                .setPositiveButton(android.R.string.ok, null).show();
                        return;
                    }
                    // An empty value means "all active accounts", so newly
                    // added accounts are included automatically.
                    String savedScope = count == accounts.length ? "" : value.toString();
                    ApplicationLoader.applicationContext.getSharedPreferences("cross_account_inbox_ui", Context.MODE_PRIVATE)
                            .edit().putString(INBOX_SCOPE_KEY, savedScope)
                            // Keep group selections: each key includes account slot and owner id,
                            // so selections for other accounts cannot match the newly selected scope.
                            .apply();
                    if (active && listView != null) listView.adapter.update(true);
                }).show();
    }

    private void showInboxFilterMenu() {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity())
                .setTitle("回复筛选条件")
                .setItems(new CharSequence[]{"选择账号范围", "选择群组范围", "选择回复类型"},
                        (dialog, which) -> {
                            if (which == 0) showInboxAccountPicker();
                            else if (which == 1) showInboxGroupPicker();
                            else showInboxTypePicker();
                        }).show();
    }

    private java.util.HashSet<String> getSelectedInboxGroups() {
        return new java.util.HashSet<>(ApplicationLoader.applicationContext
                .getSharedPreferences("cross_account_inbox_ui", Context.MODE_PRIVATE)
                .getStringSet(INBOX_GROUPS_KEY, new java.util.HashSet<>()));
    }

    private int getReplyTypeFilter() {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "cross_account_inbox_ui", Context.MODE_PRIVATE).getInt(INBOX_TYPE_KEY, 0);
    }

    private String getReplyFilterSummary() {
        java.util.HashSet<String> groups = getSelectedInboxGroups();
        int type = getReplyTypeFilter();
        String groupLabel = groups.isEmpty() ? "全部群组" : groups.size() + " 个群组";
        String typeLabel = type == 1 ? "文字" : type == 2 ? "媒体" : "全部类型";
        return getInboxScopeLabel() + " · " + groupLabel + " · " + typeLabel;
    }

    private boolean matchesReplyFilters(CrossAccountInboxStore.Entry reply) {
        if (!getSelectedInboxAccounts().contains(reply.account)) return false;
        java.util.HashSet<String> groups = getSelectedInboxGroups();
        // Group scope applies only to group replies. Private incoming messages
        // are controlled by the account and message-type filters, not a group picker.
        // Group ids are only unique within an account; never match a bare dialog id.
        if (reply.dialogId < 0 && !groups.isEmpty()
                && !groups.contains(getInboxGroupKey(reply.account, reply.ownerId, reply.dialogId))) return false;
        int type = getReplyTypeFilter();
        return type == 0 || reply.messageType == type;
    }

    private String getInboxGroupKey(int account, long ownerId, long dialogId) {
        // Account slots can be reused after logout. Include the signed-in owner
        // so a saved filter can never leak across different accounts in one slot.
        return account + ":" + ownerId + ":" + dialogId;
    }

    private void showInboxGroupPicker() {
        if (getParentActivity() == null) return;
        java.util.HashSet<Integer> accounts = getSelectedInboxAccounts();
        java.util.HashMap<String, String> labelsById = new java.util.HashMap<>();
        // Build the picker from the selected accounts' actual dialog list, not
        // from captured replies. This lets users configure group filters before
        // the first qualifying reply arrives.
        for (int account : accounts) {
            UserConfig config = UserConfig.getInstance(account);
            if (!config.isClientActivated()) continue;
            MessagesController controller = MessagesController.getInstance(account);
            for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
                if (dialog == null || dialog.id >= 0 || DialogObject.isFolderDialogId(dialog.id)) continue;
                TLRPC.Chat chat = controller.getChat(-dialog.id);
                if (chat == null || org.telegram.messenger.ChatObject.isChannel(chat)
                        && !chat.megagroup && !chat.gigagroup) continue;
                String key = getInboxGroupKey(account, config.getClientUserId(), dialog.id);
                String title = TextUtils.isEmpty(chat.title) ? "群组消息" : chat.title;
                labelsById.put(key, "账号 " + (account + 1) + " · " + title);
            }
        }
        if (labelsById.isEmpty()) {
            java.util.HashSet<String> selectedGroups = getSelectedInboxGroups();
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity())
                    .setMessage("所选账号当前没有已加载的群组。");
            if (!selectedGroups.isEmpty()) {
                builder.setMessage("当前账号范围内已没有可选群组，是否清除之前保存的群组筛选？")
                        .setPositiveButton("清除筛选", (dialog, which) -> {
                            ApplicationLoader.applicationContext.getSharedPreferences("cross_account_inbox_ui", Context.MODE_PRIVATE)
                                    .edit().remove(INBOX_GROUPS_KEY).apply();
                            if (active && listView != null) listView.adapter.update(true);
                        })
                        .setNegativeButton(android.R.string.cancel, null);
            } else {
                builder.setPositiveButton(android.R.string.ok, null);
            }
            builder.show();
            return;
        }
        java.util.ArrayList<String> ids = new java.util.ArrayList<>(labelsById.keySet());
        ids.sort((left, right) -> labelsById.get(left).compareToIgnoreCase(labelsById.get(right)));
        CharSequence[] labels = new CharSequence[ids.size()];
        boolean[] checked = new boolean[ids.size()];
        java.util.HashSet<String> selected = getSelectedInboxGroups();
        boolean hasMatchingSelection = false;
        for (String id : ids) {
            if (selected.contains(id)) {
                hasMatchingSelection = true;
                break;
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            labels[i] = labelsById.get(ids.get(i));
            // A stale selection must not leave every current group unchecked.
            // Only account-qualified keys are valid; legacy bare dialog ids are ambiguous.
            checked[i] = selected.isEmpty() || !hasMatchingSelection || selected.contains(ids.get(i));
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle("选择群组（默认全部）")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // Preserve selections belonging to accounts outside the current
                    // account scope; the picker only edits the groups it displays.
                    int checkedCount = 0;
                    for (boolean value : checked) if (value) checkedCount++;
                    if (checkedCount == 0) {
                        new AlertDialog.Builder(getParentActivity()).setMessage("至少选择一个群组，或勾选全部群组。")
                                .setPositiveButton(android.R.string.ok, null).show();
                        return;
                    }
                    java.util.HashSet<String> values = getSelectedInboxGroups();
                    values.removeAll(ids);
                    for (int i = 0; i < ids.size(); i++) if (checked[i]) values.add(ids.get(i));
                    if (checkedCount == ids.size() && values.size() == ids.size()) values.clear();
                    ApplicationLoader.applicationContext.getSharedPreferences("cross_account_inbox_ui", Context.MODE_PRIVATE)
                            .edit().putStringSet(INBOX_GROUPS_KEY, values).apply();
                    if (active && listView != null) listView.adapter.update(true);
                }).show();
    }

    private void showInboxTypePicker() {
        if (getParentActivity() == null) return;
        final int[] selected = {getReplyTypeFilter()};
        new AlertDialog.Builder(getParentActivity())
                .setTitle("回复类型")
                .setSingleChoiceItems(new CharSequence[]{"全部类型", "文字回复", "媒体回复"}, selected[0],
                        (dialog, which) -> selected[0] = which)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    ApplicationLoader.applicationContext.getSharedPreferences("cross_account_inbox_ui", Context.MODE_PRIVATE)
                            .edit().putInt(INBOX_TYPE_KEY, selected[0]).apply();
                    if (active && listView != null) listView.adapter.update(true);
                }).show();
    }

    private MessageObject findReplyMessage(CrossAccountInboxStore.Entry row) {
        MessagesController controller = MessagesController.getInstance(row.account);
        MessageObject message = controller.dialogMessagesByIds.get(row.messageId);
        if (isValidReplyMessage(message, row)) return message;
        ArrayList<MessageObject> cached = controller.dialogMessage.get(row.dialogId);
        if (cached != null) {
            for (int i = 0; i < cached.size(); i++) {
                MessageObject candidate = cached.get(i);
                if (isValidReplyMessage(candidate, row)) return candidate;
            }
        }
        return null;
    }

    private boolean isValidReplyMessage(MessageObject message, CrossAccountInboxStore.Entry row) {
        return message != null && message.messageOwner != null && !message.deleted
                && message.getId() == row.messageId && message.getDialogId() == row.dialogId
                && !message.messageOwner.out;
    }

    private void showQuickReply(CrossAccountInboxStore.Entry row) {
        if (getParentActivity() == null) return;
        UserConfig config = UserConfig.getInstance(row.account);
        if (!config.isClientActivated() || config.getClientUserId() != row.ownerId) {
            new AlertDialog.Builder(getParentActivity())
                    .setMessage("该消息所属账号已退出或账号已切换，无法发送。")
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        MessageObject replyMessage = findReplyMessage(row);
        if (replyMessage == null) {
            new AlertDialog.Builder(getParentActivity())
                    .setMessage("原始消息当前不在本地缓存中。请先打开对应群组加载消息，再使用快捷回复。")
                    .setPositiveButton("打开群组", (dialog, which) -> openReply(row))
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        EditText input = new EditText(getParentActivity());
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setHint("输入回复内容");
        input.setMinLines(2);
        input.setMaxLines(5);
        int horizontal = AndroidUtilities.dp(20);
        input.setPadding(horizontal, AndroidUtilities.dp(12), horizontal, AndroidUtilities.dp(8));
        new AlertDialog.Builder(getParentActivity())
                .setTitle("回复 " + (TextUtils.isEmpty(row.senderName) ? "群成员" : row.senderName))
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("发送", (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (TextUtils.isEmpty(text)) return;
                    // The account may have logged out or been replaced while the
                    // confirmation dialog was open. Revalidate ownership immediately
                    // before resolving the message and submitting the send request.
                    UserConfig currentConfig = UserConfig.getInstance(row.account);
                    if (!currentConfig.isClientActivated() || currentConfig.getClientUserId() != row.ownerId) {
                        new AlertDialog.Builder(getParentActivity())
                                .setMessage("该消息所属账号已退出或账号已切换，已取消发送。")
                                .setPositiveButton(android.R.string.ok, null).show();
                        return;
                    }
                    // Resolve and send through the source account only, replying to
                    // the exact incoming message id captured by Telegram.
                    MessageObject currentReply = findReplyMessage(row);
                    if (currentReply == null) {
                        new AlertDialog.Builder(getParentActivity())
                                .setMessage("消息缓存已变化，请打开群组后重试。")
                                .setPositiveButton(android.R.string.ok, null).show();
                        return;
                    }
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                            text, row.dialogId, currentReply, null, null, true,
                            null, null, null, true, 0, 0, null, false);
                    AccountInstance.getInstance(row.account).getSendMessagesHelper().sendMessage(params);
                    // sendMessage() is asynchronous and may wait for send guards or
                    // fail at the network layer. Do not mark the inbox entry handled
                    // merely because the send request was submitted.
                    new AlertDialog.Builder(getParentActivity())
                            .setMessage("已进入发送流程。如出现发送确认提示，请完成确认；请确认群组中确实发送成功后，再将这条收件箱消息标记为已处理。")
                            .setPositiveButton("知道了", null).show();
                }).show();
    }

    private void openReply(CrossAccountInboxStore.Entry row) {
        UserConfig config = UserConfig.getInstance(row.account);
        if (!config.isClientActivated() || config.getClientUserId() != row.ownerId || getParentActivity() == null) {
            if (listView != null) listView.adapter.update(false);
            return;
        }
        Bundle args = new Bundle();
        if (row.dialogId < 0) args.putLong("chat_id", -row.dialogId);
        else args.putLong("user_id", row.dialogId);
        MessagesController controller = MessagesController.getInstance(row.account);
        if (!controller.checkCanOpenChat(args, this)) return;
        args.putInt("message_id", row.messageId);
        ChatActivity chat = new ChatActivity(args);
        chat.setCurrentAccount(row.account);
        if (getParentActivity() instanceof LaunchActivity) {
            LaunchActivity activity = (LaunchActivity) getParentActivity();
            activity.switchToAccount(row.account, true);
            activity.presentFragment(chat);
        } else {
            presentFragment(chat);
        }
    }

    private void open(Entry row) {
        UserConfig config = UserConfig.getInstance(row.account);
        if (!config.isClientActivated() || config.getClientUserId() != row.owner || getParentActivity() == null) {
            if (listView != null) listView.adapter.update(false);
            return;
        }
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(row.dialogId)) args.putInt("enc_id", DialogObject.getEncryptedChatId(row.dialogId));
        else if (row.dialogId < 0) args.putLong("chat_id", -row.dialogId);
        else args.putLong("user_id", row.dialogId);
        MessagesController controller = MessagesController.getInstance(row.account);
        if (!controller.checkCanOpenChat(args, this)) return;
        ChatActivity chat = new ChatActivity(args);
        chat.setCurrentAccount(row.account);
        // Switch the app account as well, so outgoing messages and account-level UI agree.
        if (getParentActivity() instanceof LaunchActivity) {
            LaunchActivity activity = (LaunchActivity) getParentActivity();
            activity.switchToAccount(row.account, true);
            activity.presentFragment(chat);
        } else {
            presentFragment(chat);
        }
    }

    @Override public boolean canParentTabsSlide(MotionEvent event, boolean forward) { return true; }
    @Override public void onParentScrollToTop() { if (listView != null) listView.smoothScrollToPosition(0); }

    /** Native dialog row plus a compact account label. A recycled row never changes account in place. */
    public static final class InboxDialogCell extends LinearLayout {
        private final Theme.ResourcesProvider resources;
        private final TextView accountLabel;
        private DialogCell dialogCell;
        private int boundAccount = -1;
        private long boundOwner;
        private boolean boundPrivate;
        private boolean divider;

        public InboxDialogCell(Context context, Theme.ResourcesProvider resources) {
            super(context);
            this.resources = resources;
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            accountLabel = new TextView(context);
            accountLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            accountLabel.setSingleLine(true);
            accountLabel.setEllipsize(TextUtils.TruncateAt.END);
            accountLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            accountLabel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            accountLabel.setPadding(0, 0, 0, AndroidUtilities.dp(6));
            addView(accountLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 24,
                    LocaleController.isRTL ? 16 : 78, -3, LocaleController.isRTL ? 78 : 16, 0));
        }

        private void bind(Entry row, boolean divider) {
            this.divider = divider;
            // DialogCell owns image receivers and account-specific state. Recreate it when ownership changes.
            if (dialogCell == null || boundAccount != row.account || boundOwner != row.owner || boundPrivate != row.privatePreview) {
                if (dialogCell != null) removeView(dialogCell);
                boundAccount = row.account;
                boundOwner = row.owner;
                boundPrivate = row.privatePreview;
                dialogCell = new DialogCell(null, getContext(), false, false, row.account, resources);
                dialogCell.drawAvatarStories = false;
                dialogCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                addView(dialogCell, 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            dialogCell.useSeparator = false;
            if (row.privatePreview) {
                // Do not hand sensitive message objects to the row or its thumbnail/accessibility renderers.
                DialogCell.CustomDialog hidden = new DialogCell.CustomDialog();
                hidden.id = row.dialogId;
                hidden.name = row.title;
                hidden.message = row.preview;
                hidden.date = row.date;
                hidden.unread_count = row.unread;
                hidden.type = DialogObject.isEncryptedDialog(row.dialogId) ? 2 : 0;
                MessagesController controller = MessagesController.getInstance(row.account);
                hidden.muted = controller.isDialogMuted(row.dialogId, 0);
                if (DialogObject.isEncryptedDialog(row.dialogId)) {
                    TLRPC.EncryptedChat encrypted = controller.getEncryptedChat(DialogObject.getEncryptedChatId(row.dialogId));
                    if (encrypted != null) hidden.dialogObject = controller.getUser(encrypted.user_id);
                } else {
                    hidden.dialogObject = row.dialogId < 0 ? controller.getChat(-row.dialogId) : controller.getUser(row.dialogId);
                }
                dialogCell.setDialog(hidden);
            } else {
                dialogCell.setDialog(row.dialog, DialogsActivity.DIALOGS_TYPE_DEFAULT, row.dialog.folder_id);
            }
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resources));
            accountLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources));
            accountLabel.setText(LocaleController.formatString(R.string.MangoUnifiedAccount, row.accountName));
            setContentDescription(row.title + ", " + accountLabel.getText() + ", " + row.preview
                    + (row.unread > 0 ? ", " + LocaleController.formatString(R.string.MangoUnifiedUnreadCount, row.unread) : "")
                    + (row.date > 0 ? ", " + LocaleController.stringForMessageListDate(row.date) : ""));
            invalidate();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            // The whole row opens its owning account; embedded avatar story actions must not use the selected account.
            return true;
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (divider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(78), getHeight() - 1,
                        getWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(78) : 0), getHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public static final class InboxRowFactory extends UItem.UItemFactory<InboxDialogCell> {
        static { setup(new InboxRowFactory()); }
        @Override public InboxDialogCell createView(Context context, RecyclerListView list, int account, int guid, Theme.ResourcesProvider resources) {
            return new InboxDialogCell(context, resources);
        }
        @Override public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView list) {
            if (item.object instanceof Entry) ((InboxDialogCell) view).bind((Entry) item.object, divider);
        }
        @Override public boolean contentsEquals(UItem a, UItem b) {
            // Native cells also render drafts, send state, avatars, mute state and typing indicators.
            // Notifications rebuild the snapshots, including changes not represented by text alone.
            return a.object == b.object;
        }
        @Override public boolean equals(UItem a, UItem b) {
            if (!(a.object instanceof Entry) || !(b.object instanceof Entry)) return false;
            Entry left = (Entry) a.object, right = (Entry) b.object;
            return left.account == right.account && left.owner == right.owner && left.dialogId == right.dialogId;
        }
        public static UItem of(int id, CharSequence title, CharSequence detail) {
            UItem item = UItem.ofFactory(InboxRowFactory.class);
            item.id = id;
            item.text = title;
            item.subtext = detail;
            return item;
        }
    }
}
