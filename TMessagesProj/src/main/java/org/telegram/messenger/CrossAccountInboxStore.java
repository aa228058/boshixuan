package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/**
 * Local, account-isolated persistence for messages collected by the unified inbox.
 * Message identity always includes the owning account, dialog and message id.
 */
public final class CrossAccountInboxStore {

    private static final String PREFS_PREFIX = "cross_account_inbox_";
    private static final String KEY_MESSAGES = "messages";
    private static final int MAX_MESSAGES_PER_ACCOUNT = 500;
    private static final DispatchQueue storageQueue = new DispatchQueue("crossAccountInboxStore");

    private CrossAccountInboxStore() {
    }

    public static final class Entry {
        public final int account;
        public final long ownerId;
        public final long dialogId;
        public final int messageId;
        public final int replyToMessageId;
        public final int messageType;
        public final int date;
        public final long senderId;
        public final String groupTitle;
        public final String senderName;
        public final String preview;
        public final boolean handled;

        private Entry(int account, long ownerId, long dialogId, int messageId,
                      int replyToMessageId, int messageType, int date, long senderId,
                      String groupTitle, String senderName, String preview, boolean handled) {
            this.account = account;
            this.ownerId = ownerId;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.replyToMessageId = replyToMessageId;
            this.messageType = messageType;
            this.date = date;
            this.senderId = senderId;
            this.groupTitle = groupTitle;
            this.senderName = senderName;
            this.preview = preview;
            this.handled = handled;
        }
    }

    private static SharedPreferences preferences(int account, long ownerId) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                PREFS_PREFIX + account + "_" + ownerId, Context.MODE_PRIVATE);
    }

    /** Called from MessagesController for each newly integrated, non-scheduled message. */
    public static void recordReply(int account, long dialogId, MessageObject message) {
        recordReply(account, dialogId, message, null);
    }

    private static void recordReply(int account, long dialogId, MessageObject message,
                                    ArrayList<MessageObject> candidates) {
        if (message == null || message.messageOwner == null || message.messageOwner.out
                || !CrossAccountInboxReplyMatcher.isReplyToOwnGroupMessage(account, dialogId, message, candidates)) {
            return;
        }

        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat chat = dialogId < 0 ? controller.getChat(-dialogId) : null;
        if (dialogId < 0 && chat == null) {
            return;
        }

        TLRPC.MessageReplyHeader reply = message.messageOwner.reply_to;
        TLRPC.Peer senderPeer = message.messageOwner.from_id;
        long senderId = senderPeer == null ? 0
                : senderPeer.user_id != 0 ? senderPeer.user_id
                : senderPeer.chat_id != 0 ? -senderPeer.chat_id
                : senderPeer.channel_id != 0 ? -senderPeer.channel_id : 0;
        TLRPC.User sender = senderPeer != null && senderPeer.user_id != 0
                ? controller.getUser(senderPeer.user_id) : null;
        String senderName = sender == null ? "" : UserObject.getUserName(sender);
        CharSequence body = !TextUtils.isEmpty(message.caption) ? message.caption : message.messageText;
        String preview = body == null ? message.messageOwner.message : body.toString();
        if (preview == null) {
            preview = "";
        }
        preview = preview.replace('\n', ' ').trim();
        // Media-only replies can have an empty textual body. Keep a meaningful
        // native dialog preview instead of rendering only the sender's name.
        if (TextUtils.isEmpty(preview) && !MessageObject.isMediaEmpty(message.messageOwner)) {
            preview = "媒体消息";
        }
        if (preview.length() > 500) {
            preview = preview.substring(0, 500);
        }

        final long ownerId = config.getClientUserId();
        final int messageId = message.getId();
        final int replyToMessageId = reply == null ? 0 : reply.reply_to_msg_id;
        final int messageType = MessageObject.isMediaEmpty(message.messageOwner) ? 1 : 2;
        // Apply the user's saved account/group/type scope before writing anything
        // to the unified-inbox store. The UI filter remains a defensive second check.
        if (!matchesSavedInboxFilters(account, ownerId, dialogId, messageType)) {
            return;
        }
        final int date = message.messageOwner.date;
        TLRPC.User privateUser = dialogId > 0 ? controller.getUser(dialogId) : null;
        final String savedGroupTitle = chat != null ? (chat.title == null ? "" : chat.title)
                : privateUser == null ? "私聊" : UserObject.getUserName(privateUser);
        final String savedSenderName = senderName;
        final String savedPreview = preview;
        storageQueue.postRunnable(() -> persistReply(account, ownerId, dialogId, messageId,
                replyToMessageId, messageType, date, senderId, savedGroupTitle, savedSenderName, savedPreview));
    }

    /**
     * Enforce the unified inbox scope at collection time, not only when rendering rows.
     * Account scope is stored as comma-separated account slots (empty means all active
     * accounts); group keys include account slot, owner id and dialog id.
     */
    private static boolean matchesSavedInboxFilters(int account, long ownerId,
                                                    long dialogId, int messageType) {
        SharedPreferences ui = ApplicationLoader.applicationContext.getSharedPreferences(
                "cross_account_inbox_ui", Context.MODE_PRIVATE);
        String accountScope = ui.getString("cross_account_inbox_scope_accounts", "");
        if (!TextUtils.isEmpty(accountScope)) {
            boolean included = false;
            boolean hasActiveSelection = false;
            HashSet<Integer> activeAccounts = new HashSet<>();
            for (int activeAccount : UserConfig.getActivatedAccounts()) {
                activeAccounts.add(activeAccount);
            }
            String[] accountIds = accountScope.split(",");
            for (String value : accountIds) {
                try {
                    int selectedAccount = Integer.parseInt(value.trim());
                    if (activeAccounts.contains(selectedAccount)) {
                        hasActiveSelection = true;
                        if (selectedAccount == account) {
                            included = true;
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            // Match the settings UI recovery behavior: if every saved account
            // was removed or logged out, the effective scope returns to all
            // currently active accounts rather than silently collecting nothing.
            if (hasActiveSelection && !included) return false;
        }

        int typeFilter = ui.getInt("cross_account_inbox_reply_type", 0);
        if (typeFilter != 0 && typeFilter != messageType) return false;

        // A group selection never suppresses private incoming messages.
        if (dialogId < 0) {
            HashSet<String> selectedGroups = new HashSet<>(ui.getStringSet(
                    "cross_account_inbox_scope_groups", new HashSet<>()));
            if (!selectedGroups.isEmpty()) {
                String groupKey = account + ":" + ownerId + ":" + dialogId;
                if (!selectedGroups.contains(groupKey)) return false;
            }
        }
        return true;
    }

    /** Collect replies from a regular history page, allowing exact targets in the same page. */
    public static void recordRepliesFromHistory(int account, long dialogId, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty() || dialogId == 0) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            recordReply(account, dialogId, messages.get(i), messages);
        }
    }

    private static void persistReply(int account, long ownerId, long dialogId, int messageId,
                                     int replyToMessageId, int messageType, int date, long senderId,
                                     String groupTitle, String senderName, String preview) {
        SharedPreferences prefs = preferences(account, ownerId);
        JSONArray old = readArray(prefs);
        try {
            boolean wasHandled = false;
            ArrayList<JSONObject> records = new ArrayList<>();
            for (int i = 0; i < old.length(); i++) {
                JSONObject existing = old.optJSONObject(i);
                if (existing == null) {
                    continue;
                }
                if (existing.optLong("dialog_id") == dialogId
                        && existing.optInt("message_id") == messageId) {
                    // Preserve handled state even if a duplicated historical copy exists.
                    wasHandled |= existing.optBoolean("handled", false);
                    continue;
                }
                records.add(existing);
            }

            JSONObject record = new JSONObject();
            record.put("dialog_id", dialogId);
            record.put("message_id", messageId);
            record.put("reply_to_message_id", replyToMessageId);
            record.put("message_type", messageType);
            record.put("date", date);
            record.put("sender_id", senderId);
            record.put("group_title", groupTitle);
            record.put("sender_name", senderName);
            record.put("preview", preview);
            record.put("handled", wasHandled);
            records.add(record);

            // History reloads can revisit older messages after newer ones. Sort by
            // Telegram message date before applying the per-account retention cap.
            Collections.sort(records, (first, second) -> {
                int comparison = Integer.compare(second.optInt("date"), first.optInt("date"));
                if (comparison == 0) {
                    comparison = Integer.compare(second.optInt("message_id"), first.optInt("message_id"));
                }
                if (comparison == 0) {
                    comparison = Long.compare(second.optLong("dialog_id"), first.optLong("dialog_id"));
                }
                return comparison;
            });

            JSONArray updated = new JSONArray();
            int limit = Math.min(records.size(), MAX_MESSAGES_PER_ACCOUNT);
            for (int i = 0; i < limit; i++) {
                updated.put(records.get(i));
            }
            prefs.edit().putString(KEY_MESSAGES, updated.toString()).apply();
            // Notify inbox screens only after the queued preference write is complete.
            NotificationCenter.getInstance(account).postNotificationNameOnUIThread(
                    NotificationCenter.crossAccountInboxUpdated);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static ArrayList<Entry> getAllEntries() {
        ArrayList<Entry> result = new ArrayList<>();
        for (int account : UserConfig.getActivatedAccounts()) {
            UserConfig config = UserConfig.getInstance(account);
            if (!config.isClientActivated()) {
                continue;
            }
            JSONArray array = readArray(preferences(account, config.getClientUserId()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                result.add(new Entry(account, config.getClientUserId(),
                        item.optLong("dialog_id"), item.optInt("message_id"),
                        item.optInt("reply_to_message_id"), item.optInt("message_type", 1),
                        item.optInt("date"), item.optLong("sender_id"), item.optString("group_title"),
                        item.optString("sender_name"), item.optString("preview"),
                        item.optBoolean("handled", false)));
            }
        }
        Collections.sort(result, Comparator.comparingInt((Entry entry) -> entry.date).reversed()
                .thenComparingInt(entry -> entry.account)
                .thenComparingLong(entry -> entry.dialogId)
                .thenComparingInt(entry -> entry.messageId));
        return result;
    }

    /**
     * Remove collected replies when Telegram reports deletion for a known dialog.
     * Message ids are only unique within a dialog, so never purge by id alone.
     */
    public static void removeDeletedMessages(int account, long dialogId, ArrayList<Integer> messageIds) {
        if (dialogId == 0 || messageIds == null || messageIds.isEmpty()) {
            return;
        }
        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) {
            return;
        }
        final long ownerId = config.getClientUserId();
        // Deletion batches can contain many ids. Build one lookup set so pruning
        // the bounded inbox history stays linear instead of scanning the list
        // of deleted ids for every stored reply.
        final HashSet<Integer> deletedIds = new HashSet<>(messageIds);
        storageQueue.postRunnable(() -> {
            JSONArray old = readArray(preferences(account, ownerId));
            JSONArray updated = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                boolean deleted = item.optLong("dialog_id") == dialogId
                        && deletedIds.contains(item.optInt("message_id"));
                if (deleted) {
                    changed = true;
                } else {
                    updated.put(item);
                }
            }
            if (changed) {
                preferences(account, ownerId).edit().putString(KEY_MESSAGES, updated.toString()).apply();
                NotificationCenter.getInstance(account).postNotificationNameOnUIThread(
                        NotificationCenter.crossAccountInboxUpdated);
            }
        });
    }

    public static void setHandled(int account, long ownerId, long dialogId,
                                  int messageId, boolean handled) {
        storageQueue.postRunnable(() -> {
            JSONArray old = readArray(preferences(account, ownerId));
            JSONArray updated = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (item.optLong("dialog_id") == dialogId && item.optInt("message_id") == messageId) {
                    try {
                        item.put("handled", handled);
                        changed = true;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                updated.put(item);
            }
            if (changed) {
                preferences(account, ownerId).edit().putString(KEY_MESSAGES, updated.toString()).apply();
                // Keep the inbox UI in sync with handled-state writes as well.
                NotificationCenter.getInstance(account).postNotificationNameOnUIThread(
                        NotificationCenter.crossAccountInboxUpdated);
            }
        });
    }

    private static JSONArray readArray(SharedPreferences preferences) {
        try {
            return new JSONArray(preferences.getString(KEY_MESSAGES, "[]"));
        } catch (Exception e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }
}
