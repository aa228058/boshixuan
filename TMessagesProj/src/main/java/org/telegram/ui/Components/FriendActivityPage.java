package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class FriendActivityPage {
    public interface Callback {
        void onResult(ArrayList<TLRPC.Message> messages, boolean hasMore);
        void onError(String error);
    }

    private final int currentAccount;
    private final long targetUserId;
    private final Callback callback;
    
    private final ArrayList<Long> chatIds = new ArrayList<>();
    private final HashMap<Long, Integer> chatOffsets = new HashMap<>();
    private final HashSet<Long> finishedChats = new HashSet<>();
    private final HashSet<String> loadedMessageKeys = new HashSet<>();
    private final ArrayList<TLRPC.Message> allMessages = new ArrayList<>();
    
    private boolean isLoading = false;
    private boolean hasCommonChats = false;
    private boolean commonChatsEndReached = false;
    private long commonChatsMaxId = 0;

    public FriendActivityPage(int currentAccount, long userId, Callback callback) {
        this.currentAccount = currentAccount;
        this.targetUserId = userId;
        this.callback = callback;
    }

    public void loadFirstPage() {
        if (isLoading) return;
        isLoading = true;

        MessagesController controller = MessagesController.getInstance(currentAccount);
        TLRPC.InputUser inputUser = controller.getInputUser(targetUserId);
        if (inputUser == null) {
            onError("目标用户无效");
            return;
        }
        loadCommonChatsPage(inputUser, controller);
    }

    private void loadCommonChatsPage(TLRPC.InputUser inputUser, MessagesController controller) {
        TLRPC.TL_messages_getCommonChats req = new TLRPC.TL_messages_getCommonChats();
        req.user_id = inputUser;
        req.max_id = commonChatsMaxId;
        req.limit = 100;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null || !(response instanceof TLRPC.messages_Chats)) {
                onError(error != null ? error.text : "获取群组失败");
                return;
            }

            TLRPC.messages_Chats result = (TLRPC.messages_Chats) response;
            if (result.chats == null || result.chats.isEmpty()) {
                commonChatsEndReached = true;
                hasCommonChats = !chatIds.isEmpty();
                if (chatIds.isEmpty()) {
                    isLoading = false;
                    callback.onResult(new ArrayList<>(allMessages), false);
                } else {
                    fetchMessages();
                }
                return;
            }

            controller.putChats(result.chats, false);
            long nextMaxId = Long.MAX_VALUE;
            int addedChats = 0;
            for (TLRPC.Chat chat : result.chats) {
                if (chat == null || chat.id == 0) continue;
                nextMaxId = Math.min(nextMaxId, chat.id);
                if (!chatIds.contains(chat.id)) {
                    chatIds.add(chat.id);
                    chatOffsets.put(chat.id, 0);
                    addedChats++;
                }
            }

            if (result.chats.size() < 100 || addedChats == 0 || nextMaxId == Long.MAX_VALUE) {
                commonChatsEndReached = true;
            } else {
                // Telegram's max_id pagination returns chats with IDs below this boundary.
                commonChatsMaxId = nextMaxId;
            }
            hasCommonChats = !chatIds.isEmpty();
            if (!hasCommonChats) {
                isLoading = false;
                callback.onResult(new ArrayList<>(allMessages), false);
            } else {
                fetchMessages();
            }
        }));
    }
    public void loadMore() {
        if (isLoading) return;

        boolean anyNotFinished = false;
        for (Long chatId : chatIds) {
            if (!finishedChats.contains(chatId)) {
                anyNotFinished = true;
                break;
            }
        }

        isLoading = true;
        if (anyNotFinished) {
            fetchMessages();
            return;
        }

        if (!commonChatsEndReached) {
            MessagesController controller = MessagesController.getInstance(currentAccount);
            TLRPC.InputUser inputUser = controller.getInputUser(targetUserId);
            if (inputUser == null) {
                onError("目标用户无效");
                return;
            }
            loadCommonChatsPage(inputUser, controller);
            return;
        }

        isLoading = false;
        callback.onResult(new ArrayList<>(allMessages), false);
    }

    // Limit simultaneous per-chat searches to avoid flooding the connection manager.
    private static final int MAX_CONCURRENT_CHAT_REQUESTS = 5;

    private void fetchMessages() {
        ArrayList<Long> pendingChats = new ArrayList<>();
        for (Long chatId : chatIds) {
            if (!finishedChats.contains(chatId)) pendingChats.add(chatId);
        }

        if (pendingChats.isEmpty()) {
            finishPage(false);
            return;
        }

        final int[] nextIndex = {0};
        final int[] activeCount = {0};
        final int[] completedCount = {0};
        final boolean[] addedNew = {false};
        final boolean[] partialResultSent = {false};
        dispatchChatSearches(pendingChats, nextIndex, activeCount, completedCount, addedNew, partialResultSent);
    }

    // All callbacks are marshalled onto the UI thread, so these counters are serialized.
    private void dispatchChatSearches(ArrayList<Long> pendingChats, int[] nextIndex,
                                      int[] activeCount, int[] completedCount, boolean[] addedNew,
                                      boolean[] partialResultSent) {
        while (activeCount[0] < MAX_CONCURRENT_CHAT_REQUESTS && nextIndex[0] < pendingChats.size()) {
            long chatId = pendingChats.get(nextIndex[0]++);
            activeCount[0]++;

            MessagesController controller = MessagesController.getInstance(currentAccount);
            TLRPC.InputPeer inputPeer = controller.getInputPeer(-chatId);
            TLRPC.InputPeer userPeer = controller.getInputPeer(targetUserId);

            if (inputPeer == null || userPeer == null) {
                finishedChats.add(chatId);
                activeCount[0]--;
                completedCount[0]++;
                continue;
            }

            TLRPC.TL_messages_search searchReq = new TLRPC.TL_messages_search();
            searchReq.peer = inputPeer;
            searchReq.q = "";
            searchReq.from_id = userPeer;
            searchReq.flags |= 1; // Enable sender filtering.
            searchReq.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            searchReq.limit = 20;
            searchReq.offset_id = chatOffsets.get(chatId);

            ConnectionsManager.getInstance(currentAccount).sendRequest(searchReq, (res, err) ->
                    AndroidUtilities.runOnUIThread(() -> {
                        if (err != null) {
                            // Retry transient network/server/rate-limit failures on the next
                            // loadMore() call. Permanent RPC errors (for example invalid or
                            // inaccessible peers) should not keep the activity list alive forever.
                            if (!isRetryableChatError(err)) {
                                finishedChats.add(chatId);
                            }
                        } else if (res instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) res;
                            if (msgs.messages == null || msgs.messages.isEmpty()) {
                                finishedChats.add(chatId);
                            } else {
                                controller.putUsers(msgs.users, false);
                                controller.putChats(msgs.chats, false);

                                int oldestId = Integer.MAX_VALUE;
                                for (TLRPC.Message message : msgs.messages) {
                                    if (message == null) continue;
                                    // Pagination must advance across every returned result, even
                                    // if a server-side filter unexpectedly includes a non-target sender.
                                    oldestId = Math.min(oldestId, message.id);
                                    if (!(message.from_id instanceof TLRPC.TL_peerUser)
                                            || message.from_id.user_id != targetUserId) continue;

                                    String key = chatId + "_" + message.id;
                                    if (loadedMessageKeys.add(key)) {
                                        message.dialog_id = -chatId;
                                        allMessages.add(message);
                                        addedNew[0] = true;
                                    }
                                }
                                if (oldestId != Integer.MAX_VALUE) {
                                    chatOffsets.put(chatId, oldestId);
                                }
                                if (msgs.messages.size() < 20) {
                                    finishedChats.add(chatId);
                                }
                            }
                        }

                        // Show the first useful batch as soon as one group returns.
                        // Other group searches continue in the background; the final callback
                        // will refresh the list with the complete, sorted result set.
                        if (addedNew[0] && !partialResultSent[0]) {
                            partialResultSent[0] = true;
                            sortMessages();
                            callback.onResult(new ArrayList<>(allMessages), true);
                        }

                        activeCount[0]--;
                        completedCount[0]++;
                        if (completedCount[0] >= pendingChats.size()) {
                            finishPage(addedNew[0]);
                        } else {
                            dispatchChatSearches(pendingChats, nextIndex, activeCount, completedCount, addedNew, partialResultSent);
                        }
                    }));
        }

        if (completedCount[0] >= pendingChats.size()) {
            finishPage(addedNew[0]);
        }
    }

    private boolean isRetryableChatError(TLRPC.TL_error error) {
        if (error == null) {
            return false;
        }
        // Telegram commonly uses 420 for FLOOD_WAIT, 5xx for server failures,
        // and negative codes for transport/network failures.
        if (error.code == 420 || error.code >= 500 || error.code < 0) {
            return true;
        }
        // A few temporary conditions can be reported with a 4xx RPC code.
        String text = error.text != null ? error.text.toUpperCase() : "";
        return text.contains("FLOOD_WAIT")
                || text.contains("NETWORK")
                || text.contains("TIMEOUT")
                || text.contains("CONNECTION")
                || text.contains("SERVER_ERROR")
                || text.contains("MSG_WAIT");
    }

    private void sortMessages() {
        Collections.sort(allMessages, (m1, m2) -> {
            TLRPC.Chat chat1 = MessagesController.getInstance(currentAccount).getChat(-m1.dialog_id);
            TLRPC.Chat chat2 = MessagesController.getInstance(currentAccount).getChat(-m2.dialog_id);
            String title1 = chat1 != null && chat1.title != null ? chat1.title : "";
            String title2 = chat2 != null && chat2.title != null ? chat2.title : "";
            int groupCompare = title1.compareToIgnoreCase(title2);
            if (groupCompare != 0) {
                return groupCompare;
            }
            // Titles are not unique; use the dialog id as a stable group key
            // so messages from different groups never interleave.
            int dialogCompare = Long.compare(m1.dialog_id, m2.dialog_id);
            if (dialogCompare != 0) {
                return dialogCompare;
            }
            int dateCompare = Integer.compare(m2.date, m1.date);
            return dateCompare != 0 ? dateCompare : Integer.compare(m2.id, m1.id);
        });
    }

    private void finishPage(boolean addedNew) {
        if (addedNew) {
            sortMessages();
        }
        boolean hasMore = !commonChatsEndReached;
        if (!hasMore) {
            for (Long chatId : chatIds) {
                if (!finishedChats.contains(chatId)) {
                    hasMore = true;
                    break;
                }
            }
        }
        isLoading = false;
        callback.onResult(new ArrayList<>(allMessages), hasMore);
    }

    private void onError(String error) {
        isLoading = false;
        if (callback != null) callback.onError(error);
    }
}