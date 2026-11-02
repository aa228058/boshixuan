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
        public final int date;
        public final long senderId;
        public final String groupTitle;
        public final String senderName;
        public final String preview;
        public final boolean handled;

        private Entry(int account, long ownerId, long dialogId, int messageId,
                      int replyToMessageId, int date, long senderId,
                      String groupTitle, String senderName, String preview, boolean handled) {
            this.account = account;
            this.ownerId = ownerId;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.replyToMessageId = replyToMessageId;
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
        if (message == null || message.messageOwner == null || message.messageOwner.out
                || !CrossAccountInboxReplyMatcher.isReplyToOwnGroupMessage(account, dialogId, message)) {
            return;
        }

        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat chat = controller.getChat(-dialogId);
        if (chat == null) {
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
        if (preview.length() > 500) {
            preview = preview.substring(0, 500);
        }

        final long ownerId = config.getClientUserId();
        final int messageId = message.getId();
        final int replyToMessageId = reply.reply_to_msg_id;
        final int date = message.messageOwner.date;
        final String savedGroupTitle = chat.title == null ? "" : chat.title;
        final String savedSenderName = senderName;
        final String savedPreview = preview;
        storageQueue.postRunnable(() -> persistReply(account, ownerId, dialogId, messageId,
                replyToMessageId, date, senderId, savedGroupTitle, savedSenderName, savedPreview));
    }

    private static void persistReply(int account, long ownerId, long dialogId, int messageId,
                                     int replyToMessageId, int date, long senderId,
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
                        item.optInt("reply_to_message_id"), item.optInt("date"),
                        item.optLong("sender_id"), item.optString("group_title"),
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
                NotificationCenter.getInstance(account).postNotificationName(
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
