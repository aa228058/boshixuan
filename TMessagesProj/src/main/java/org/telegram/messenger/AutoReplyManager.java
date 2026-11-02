package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class AutoReplyManager {

    private static final String PREF_ENABLED = "auto_reply_enabled";
    private static final String PREF_ANY_MESSAGE_ENABLED = "auto_reply_any_message_enabled";
    private static final String PREF_RULES = "auto_reply_rules";
    private static final String PREF_NEXT_RULE_ID = "auto_reply_next_rule_id";

    private static final int LOCAL_PRESET_TOPIC_ID_OFFSET = 1000000000;
    private static final int ANY_MESSAGE_RULE_ID = -1;
    private static final int ANY_MESSAGE_PRESET_TOPIC_ID = LOCAL_PRESET_TOPIC_ID_OFFSET - 1;
    private static final String ANY_MESSAGE_SHORTCUT = "auto_reply_any_message";
    private static final int MAX_PROCESSED_MESSAGES = 300;
    private static final int MAX_REPLY_MESSAGE_AGE_SECONDS = 10 * 60;

    private static final AutoReplyManager[] instances = new AutoReplyManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static AutoReplyManager getInstance(int account) {
        synchronized (instances) {
            AutoReplyManager instance = instances[account];
            if (instance == null) {
                instance = new AutoReplyManager(account);
                instances[account] = instance;
            }
            return instance;
        }
    }

    public static class Rule {
        public final int id;
        public final String keyword;
        public final String shortcut;
        public final ArrayList<String> legacyReplies;

        public Rule(int id, String keyword, String shortcut, ArrayList<String> legacyReplies) {
            this.id = id;
            this.keyword = keyword == null ? "" : keyword.trim();
            this.shortcut = TextUtils.isEmpty(shortcut) ? buildShortcutName(id) : shortcut.trim();
            this.legacyReplies = legacyReplies == null ? new ArrayList<>() : new ArrayList<>(legacyReplies);
        }
    }

    private final int currentAccount;
    private final SharedPreferences preferences;

    private boolean loaded;
    private boolean enabled;
    private boolean anyMessageEnabled;
    private int nextRuleId = 1;
    private final ArrayList<Rule> rules = new ArrayList<>();
    private final ArrayList<String> processedMessageKeys = new ArrayList<>();
    private final HashMap<Integer, Integer> presetMessageCounts = new HashMap<>();
    private int anyMessagePresetMessagesCount;

    private AutoReplyManager(int account) {
        currentAccount = account;
        preferences = MessagesController.getMainSettings(account);
    }

    public synchronized boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public synchronized void setEnabled(boolean value) {
        ensureLoaded();
        if (enabled == value) {
            return;
        }
        enabled = value;
        saveLocked();
    }

    public synchronized boolean isAnyMessageEnabled() {
        ensureLoaded();
        return anyMessageEnabled;
    }

    public synchronized void setAnyMessageEnabled(boolean value) {
        ensureLoaded();
        if (anyMessageEnabled == value) {
            return;
        }
        anyMessageEnabled = value;
        saveLocked();
    }

    public synchronized int getRulesCount() {
        ensureLoaded();
        return rules.size();
    }

    public synchronized int getReplyMessagesCount() {
        ensureLoaded();
        int count = 0;
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            count += Math.max(getPresetMessagesCountLocked(rule.id), rule.legacyReplies.size());
        }
        count += anyMessagePresetMessagesCount;
        return count;
    }

    public synchronized int getPresetMessagesCount(int ruleId) {
        ensureLoaded();
        return getPresetMessagesCountLocked(ruleId);
    }

    public synchronized int getAnyMessagePresetMessagesCount() {
        ensureLoaded();
        return anyMessagePresetMessagesCount;
    }

    public void loadPresetMessageCounts(Runnable whenDone) {
        ArrayList<Integer> ruleIds;
        synchronized (this) {
            ensureLoaded();
            ruleIds = new ArrayList<>(rules.size());
            for (int i = 0; i < rules.size(); i++) {
                ruleIds.add(rules.get(i).id);
            }
        }
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            HashMap<Integer, Integer> counts = new HashMap<>();
            int anyMessageCount = 0;
            ArrayList<Integer> topicIds = new ArrayList<>(ruleIds.size() + 1);
            topicIds.add(ANY_MESSAGE_PRESET_TOPIC_ID);
            for (int i = 0; i < ruleIds.size(); i++) {
                topicIds.add(getLocalPresetTopicId(ruleIds.get(i)));
            }
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT topic_id, COUNT(mid) FROM quick_replies_messages WHERE topic_id IN(%s) GROUP BY topic_id", TextUtils.join(",", topicIds)));
                while (cursor.next()) {
                    int topicId = cursor.intValue(0);
                    if (topicId == ANY_MESSAGE_PRESET_TOPIC_ID) {
                        anyMessageCount = cursor.intValue(1);
                        continue;
                    }
                    int ruleId = getRuleIdFromLocalPresetTopicId(topicId);
                    if (ruleId > 0) {
                        counts.put(ruleId, cursor.intValue(1));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            int finalAnyMessageCount = anyMessageCount;
            AndroidUtilities.runOnUIThread(() -> {
                synchronized (this) {
                    presetMessageCounts.clear();
                    for (int i = 0; i < ruleIds.size(); i++) {
                        int ruleId = ruleIds.get(i);
                        Integer count = counts.get(ruleId);
                        presetMessageCounts.put(ruleId, count == null ? 0 : count);
                    }
                    anyMessagePresetMessagesCount = finalAnyMessageCount;
                }
                if (whenDone != null) {
                    whenDone.run();
                }
            });
        });
    }

    public synchronized ArrayList<Rule> getRules() {
        ensureLoaded();
        return cloneRulesLocked();
    }

    public synchronized Rule addRule(String keyword) {
        ensureLoaded();
        String normalizedKeyword = normalizeKeyword(keyword);
        if (TextUtils.isEmpty(normalizedKeyword) || findRuleIndexByKeywordLocked(normalizedKeyword) >= 0) {
            return null;
        }
        int ruleId = nextRuleId++;
        Rule rule = new Rule(ruleId, normalizedKeyword, buildShortcutName(normalizedKeyword, ruleId), null);
        rules.add(rule);
        saveLocked();
        return rule;
    }

    public synchronized boolean updateRuleKeyword(int id, String keyword) {
        ensureLoaded();
        int index = findRuleIndexByIdLocked(id);
        if (index < 0) {
            return false;
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        if (TextUtils.isEmpty(normalizedKeyword)) {
            return false;
        }
        int duplicateIndex = findRuleIndexByKeywordLocked(normalizedKeyword);
        if (duplicateIndex >= 0 && duplicateIndex != index) {
            return false;
        }
        Rule oldRule = rules.get(index);
        rules.set(index, new Rule(id, normalizedKeyword, oldRule.shortcut, oldRule.legacyReplies));
        saveLocked();
        return true;
    }

    public synchronized boolean removeRule(int id) {
        ensureLoaded();
        int index = findRuleIndexByIdLocked(id);
        if (index < 0) {
            return false;
        }
        rules.remove(index);
        presetMessageCounts.remove(id);
        saveLocked();
        deleteLocalPresetMessages(id);
        return true;
    }

    public synchronized boolean isAutoReplyPreset(String shortcut, int topicId) {
        return getAutoReplyTopicId(shortcut, topicId) != 0;
    }

    public synchronized int getAutoReplyTopicId(String shortcut, int topicId) {
        ensureLoaded();
        if (topicId == ANY_MESSAGE_PRESET_TOPIC_ID || TextUtils.equals(shortcut, ANY_MESSAGE_SHORTCUT)) {
            return ANY_MESSAGE_PRESET_TOPIC_ID;
        }
        int ruleId = getRuleIdFromLocalPresetTopicId(topicId);
        if (ruleId > 0 && findRuleIndexByIdLocked(ruleId) >= 0) {
            return topicId;
        }
        int shortcutIndex = findRuleIndexByShortcutLocked(shortcut);
        if (shortcutIndex >= 0) {
            return getLocalPresetTopicId(rules.get(shortcutIndex).id);
        }
        return 0;
    }

    public synchronized void onLocalPresetMessagesChanged(int topicId) {
        if (topicId == ANY_MESSAGE_PRESET_TOPIC_ID) {
            anyMessagePresetMessagesCount = 0;
            return;
        }
        int ruleId = getRuleIdFromLocalPresetTopicId(topicId);
        if (ruleId > 0) {
            presetMessageCounts.remove(ruleId);
        }
    }

    public void processNewMessages(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        ArrayList<Rule> rulesSnapshot;
        boolean anyMessageEnabledSnapshot;
        synchronized (this) {
            ensureLoaded();
            if (!enabled || rules.isEmpty() && !anyMessageEnabled) {
                return;
            }
            anyMessageEnabledSnapshot = anyMessageEnabled;
            rulesSnapshot = cloneRulesLocked();
        }
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            MatchedReply matchedReply = getMatchedReply(messageObject, rulesSnapshot, anyMessageEnabledSnapshot);
            if (matchedReply == null || !markMessageProcessed(messageObject.getDialogId(), messageObject.getId())) {
                continue;
            }
            sendReplies(messageObject.getDialogId(), matchedReply);
        }
    }

    private MatchedReply getMatchedReply(MessageObject messageObject, ArrayList<Rule> rulesSnapshot, boolean anyMessageEnabledSnapshot) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.getId() <= 0 || messageObject.isOut() || messageObject.isSponsored()) {
            return null;
        }
        long dialogId = messageObject.getDialogId();
        if (!DialogObject.isUserDialog(dialogId) || dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return null;
        }
        int messageDate = messageObject.messageOwner.date;
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (messageDate > 0 && currentTime - messageDate > MAX_REPLY_MESSAGE_AGE_SECONDS) {
            return null;
        }
        String sourceText = buildSourceText(messageObject);
        if (!TextUtils.isEmpty(sourceText)) {
            String normalizedSource = sourceText.toLowerCase(Locale.ROOT);
            for (int i = 0; i < rulesSnapshot.size(); i++) {
                Rule rule = rulesSnapshot.get(i);
                if (!TextUtils.isEmpty(rule.keyword) && normalizedSource.contains(rule.keyword.toLowerCase(Locale.ROOT))) {
                    return new MatchedReply(rule.id, rule.shortcut, rule.legacyReplies);
                }
            }
        }
        if (anyMessageEnabledSnapshot) {
            return new MatchedReply(ANY_MESSAGE_RULE_ID, ANY_MESSAGE_SHORTCUT, null);
        }
        return null;
    }

    private void sendReplies(long dialogId, MatchedReply matchedReply) {
        if (matchedReply == null) {
            return;
        }
        if (matchedReply.ruleId > 0) {
            sendLocalPresetMessages(dialogId, matchedReply.ruleId, matchedReply.legacyReplies);
            return;
        }
        sendLegacyReplies(dialogId, matchedReply.legacyReplies);
    }

    private void sendLocalPresetMessages(long dialogId, int ruleId, ArrayList<String> legacyReplies) {
        int topicId = ruleId == ANY_MESSAGE_RULE_ID ? ANY_MESSAGE_PRESET_TOPIC_ID : getLocalPresetTopicId(ruleId);
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.Message> presetMessages = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized("SELECT data, send_state, mid, date, ttl FROM quick_replies_messages WHERE topic_id = ? ORDER BY mid DESC", topicId);
                long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.send_state = cursor.intValue(1);
                        message.id = cursor.intValue(2);
                        message.readAttachPath(data, selfId);
                        message.date = cursor.intValue(3);
                        if (message.ttl == 0) {
                            message.ttl = cursor.intValue(4);
                        }
                        message.dialog_id = selfId;
                        clearQuickReply(message);
                        presetMessages.add(message);
                    }
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (presetMessages.isEmpty()) {
                    sendLegacyReplies(dialogId, legacyReplies);
                    return;
                }
                for (int i = 0; i < presetMessages.size(); i++) {
                    TLRPC.Message preset = presetMessages.get(i);
                    MangoSendGuard.runConfirmed(() -> sendPresetMessage(dialogId, preset));
                }
            });
        });
    }

    private void sendPresetMessage(long dialogId, TLRPC.Message message) {
        if (message == null) {
            return;
        }
        TLRPC.MessageMedia media = message.media;
        String text = message.message == null ? "" : message.message;
        ArrayList<TLRPC.MessageEntity> entities = cloneEntities(message.entities);
        SendMessagesHelper sendMessagesHelper = SendMessagesHelper.getInstance(currentAccount);
        MessageObject parentObject = new MessageObject(currentAccount, message, false, true);

        if (media instanceof TLRPC.TL_messageMediaWebPage) {
            sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId, null, null, media.webpage, true, entities, null, null, true, 0, 0, null, false));
            return;
        } else if (media != null && !(media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (media.photo instanceof TLRPC.TL_photo) {
                sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of((TLRPC.TL_photo) media.photo, null, dialogId, null, null, text, entities, null, null, true, 0, 0, media.ttl_seconds, parentObject, false));
                return;
            } else if (media.document instanceof TLRPC.TL_document) {
                sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of((TLRPC.TL_document) media.document, null, message.attachPath, dialogId, null, null, text, entities, null, null, true, 0, 0, media.ttl_seconds, parentObject, null, false));
                return;
            } else if (media.geo != null && (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaGeoLive || media instanceof TLRPC.TL_messageMediaVenue)) {
                sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of(media, dialogId, null, null, null, null, true, 0, 0));
                return;
            } else if (!TextUtils.isEmpty(media.phone_number)) {
                TLRPC.User user = new TLRPC.TL_userContact_old2();
                user.phone = media.phone_number;
                user.first_name = media.first_name;
                user.last_name = media.last_name;
                user.id = media.user_id;
                sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of(user, dialogId, null, null, null, null, true, 0, 0));
                return;
            }
        }

        if (!TextUtils.isEmpty(text)) {
            sendMessagesHelper.sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false));
        }
    }

    private void sendLegacyReplies(long dialogId, ArrayList<String> legacyReplies) {
        if (legacyReplies == null || legacyReplies.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (int i = 0; i < legacyReplies.size(); i++) {
                String reply = legacyReplies.get(i);
                if (!TextUtils.isEmpty(reply)) {
                    MangoSendGuard.runConfirmed(() -> SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(reply, dialogId)));
                }
            }
        });
    }

    private void deleteLocalPresetMessages(int ruleId) {
        int topicId = getLocalPresetTopicId(ruleId);
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            try {
                storage.getDatabase().executeFast(String.format(Locale.US, "DELETE FROM quick_replies_messages WHERE topic_id = %d", topicId)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated));
        });
    }

    private synchronized boolean markMessageProcessed(long dialogId, int messageId) {
        String key = dialogId + "_" + messageId;
        if (processedMessageKeys.contains(key)) {
            return false;
        }
        processedMessageKeys.add(0, key);
        while (processedMessageKeys.size() > MAX_PROCESSED_MESSAGES) {
            processedMessageKeys.remove(processedMessageKeys.size() - 1);
        }
        return true;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        enabled = preferences.getBoolean(PREF_ENABLED, false);
        anyMessageEnabled = preferences.getBoolean(PREF_ANY_MESSAGE_ENABLED, false);
        nextRuleId = Math.max(1, preferences.getInt(PREF_NEXT_RULE_ID, 1));
        loadRulesLocked();
    }

    private void loadRulesLocked() {
        rules.clear();
        int maxId = 0;
        try {
            JSONArray array = new JSONArray(preferences.getString(PREF_RULES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                int id = object.optInt("id", i + 1);
                String keyword = normalizeKeyword(object.optString("keyword", null));
                String shortcut = object.optString("shortcut", null);
                ArrayList<String> legacyReplies = new ArrayList<>();
                JSONArray repliesArray = object.optJSONArray("replies");
                if (repliesArray != null) {
                    for (int j = 0; j < repliesArray.length(); j++) {
                        String reply = normalizeReply(repliesArray.optString(j, null));
                        if (!TextUtils.isEmpty(reply)) {
                            legacyReplies.add(reply);
                        }
                    }
                }
                if (id > 0 && !TextUtils.isEmpty(keyword) && findRuleIndexByKeywordLocked(keyword) < 0) {
                    rules.add(new Rule(id, keyword, shortcut, legacyReplies));
                    maxId = Math.max(maxId, id);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        nextRuleId = Math.max(nextRuleId, maxId + 1);
    }

    private void saveLocked() {
        try {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PREF_ENABLED, enabled);
            editor.putBoolean(PREF_ANY_MESSAGE_ENABLED, anyMessageEnabled);
            editor.putInt(PREF_NEXT_RULE_ID, nextRuleId);
            editor.putString(PREF_RULES, serializeRulesLocked().toString());
            editor.apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private JSONArray serializeRulesLocked() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
                JSONObject object = new JSONObject();
                try {
                    object.put("id", rule.id);
                    object.put("keyword", rule.keyword);
                    object.put("shortcut", rule.shortcut);
                    JSONArray repliesArray = new JSONArray();
                    for (int j = 0; j < rule.legacyReplies.size(); j++) {
                        repliesArray.put(rule.legacyReplies.get(j));
                    }
                    object.put("replies", repliesArray);
                    array.put(object);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return array;
    }

    private ArrayList<Rule> cloneRulesLocked() {
        ArrayList<Rule> result = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            result.add(new Rule(rule.id, rule.keyword, rule.shortcut, rule.legacyReplies));
        }
        return result;
    }

    private int findRuleIndexByIdLocked(int id) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    private int findRuleIndexByKeywordLocked(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (TextUtils.isEmpty(normalized)) {
            return -1;
        }
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).keyword.equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private int findRuleIndexByShortcutLocked(String shortcut) {
        if (TextUtils.isEmpty(shortcut)) {
            return -1;
        }
        for (int i = 0; i < rules.size(); i++) {
            if (TextUtils.equals(rules.get(i).shortcut, shortcut)) {
                return i;
            }
        }
        return -1;
    }

    private int getPresetMessagesCountLocked(int ruleId) {
        Integer count = presetMessageCounts.get(ruleId);
        return count == null ? 0 : count;
    }

    private static String normalizeKeyword(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeReply(String value) {
        return value == null ? "" : value.trim();
    }

    private static String buildShortcutName(int id) {
        return "auto_reply_" + Math.max(1, id);
    }

    private String buildShortcutName(String keyword, int id) {
        return buildShortcutName(id);
    }

    public static int getLocalPresetTopicId(int ruleId) {
        return LOCAL_PRESET_TOPIC_ID_OFFSET + Math.max(1, ruleId);
    }

    public static int getAnyMessagePresetTopicId() {
        return ANY_MESSAGE_PRESET_TOPIC_ID;
    }

    public static String getAnyMessageShortcut() {
        return ANY_MESSAGE_SHORTCUT;
    }

    private static int getRuleIdFromLocalPresetTopicId(int topicId) {
        if (topicId == ANY_MESSAGE_PRESET_TOPIC_ID) {
            return ANY_MESSAGE_RULE_ID;
        }
        int ruleId = topicId - LOCAL_PRESET_TOPIC_ID_OFFSET;
        return ruleId > 0 ? ruleId : 0;
    }

    private static void clearQuickReply(TLRPC.Message message) {
        if (message == null) {
            return;
        }
        message.flags &= ~1073741824;
        message.quick_reply_shortcut_id = 0;
        message.quick_reply_shortcut = null;
    }

    private static ArrayList<TLRPC.MessageEntity> cloneEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        return new ArrayList<>(entities);
    }

    public static ArrayList<String> parseReplies(String value) {
        ArrayList<String> replies = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return replies;
        }
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String reply = normalizeReply(lines[i]);
            if (!TextUtils.isEmpty(reply)) {
                replies.add(reply);
            }
        }
        return replies;
    }

    private static ArrayList<String> normalizeReplies(ArrayList<String> replies) {
        ArrayList<String> normalizedReplies = new ArrayList<>();
        if (replies == null) {
            return normalizedReplies;
        }
        for (int i = 0; i < replies.size(); i++) {
            String reply = normalizeReply(replies.get(i));
            if (!TextUtils.isEmpty(reply)) {
                normalizedReplies.add(reply);
            }
        }
        return normalizedReplies;
    }

    public static String joinReplies(ArrayList<String> replies) {
        if (replies == null || replies.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < replies.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(replies.get(i));
        }
        return builder.toString();
    }

    private static String buildSourceText(MessageObject messageObject) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, messageObject.messageOwner.message);
        appendPart(builder, messageObject.caption);
        appendPart(builder, messageObject.messageText);
        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            TLRPC.WebPage webpage = ((TLRPC.TL_messageMediaWebPage) messageObject.messageOwner.media).webpage;
            if (webpage != null) {
                appendPart(builder, webpage.title);
                appendPart(builder, webpage.description);
                appendPart(builder, webpage.site_name);
                appendPart(builder, webpage.author);
            }
        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
            if (poll != null && poll.question != null) {
                appendPart(builder, poll.question.text);
            }
        }
        appendPart(builder, messageObject.getFileName());
        return builder.toString().trim();
    }

    private static void appendPart(StringBuilder builder, CharSequence value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private static class MatchedReply {
        private final int ruleId;
        private final String shortcut;
        private final ArrayList<String> legacyReplies;

        private MatchedReply(int ruleId, String shortcut, ArrayList<String> legacyReplies) {
            this.ruleId = ruleId;
            this.shortcut = shortcut;
            this.legacyReplies = legacyReplies == null ? new ArrayList<>() : new ArrayList<>(legacyReplies);
        }
    }
}
