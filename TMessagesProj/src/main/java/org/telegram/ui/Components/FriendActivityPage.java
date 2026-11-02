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

        TLRPC.TL_messages_getCommonChats req = new TLRPC.TL_messages_getCommonChats();
        req.user_id = inputUser;
        req.limit = 20;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null || !(response instanceof TLRPC.messages_Chats)) {
                onError(error != null ? error.text : "获取群组失败");
                return;
            }
            TLRPC.messages_Chats result = (TLRPC.messages_Chats) response;
            if (result.chats == null || result.chats.isEmpty()) {
                callback.onResult(allMessages, false);
                isLoading = false;
                return;
            }
            controller.putChats(result.chats, false);
            chatIds.clear();
            for (TLRPC.Chat chat : result.chats) {
                if (chat != null && chat.id != 0 && !chatIds.contains(chat.id)) {
                    chatIds.add(chat.id);
                    chatOffsets.put(chat.id, 0);
                }
            }
            hasCommonChats = true;
            fetchMessages();
        }));
    }

    public void loadMore() {
        if (isLoading || !hasCommonChats) return;
        
        boolean anyNotFinished = false;
        for (Long chatId : chatIds) {
            if (!finishedChats.contains(chatId)) { anyNotFinished = true; break; }
        }
        
        if (!anyNotFinished) {
            callback.onResult(allMessages, false);
            return;
        }
        isLoading = true;
        fetchMessages();
    }

    private void fetchMessages() {
        ArrayList<Long> pendingChats = new ArrayList<>();
        for (Long chatId : chatIds) {
            if (!finishedChats.contains(chatId)) pendingChats.add(chatId);
        }

        if (pendingChats.isEmpty()) { finishPage(false); return; }

        final int[] pendingCount = {pendingChats.size()};
        final boolean[] addedNew = {false};

        for (long chatId : pendingChats) {
            MessagesController controller = MessagesController.getInstance(currentAccount);
            TLRPC.InputPeer inputPeer = controller.getInputPeer(-chatId);
            TLRPC.InputPeer userPeer = controller.getInputPeer(targetUserId);

            if (inputPeer == null || userPeer == null) {
                finishedChats.add(chatId);
                pendingCount[0]--;
                if (pendingCount[0] == 0) finishPage(addedNew[0]);
                continue;
            }

            TLRPC.TL_messages_search searchReq = new TLRPC.TL_messages_search();
            searchReq.peer = inputPeer;
            searchReq.q = "";
            searchReq.from_id = userPeer;
            searchReq.flags |= 1; // 强制开启发送者过滤
            searchReq.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            searchReq.limit = 20;
            searchReq.offset_id = chatOffsets.get(chatId); 

            ConnectionsManager.getInstance(currentAccount).sendRequest(searchReq, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (err != null) {
                    if (err.text == null || !err.text.contains("FLOOD")) {
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
                        for (TLRPC.Message m : msgs.messages) {
                            if (m == null) continue;
                            if (!(m.from_id instanceof TLRPC.TL_peerUser) || m.from_id.user_id != targetUserId) continue;
                            
                            String key = chatId + "_" + m.id;
                            if (loadedMessageKeys.add(key)) {
                                m.dialog_id = -chatId;
                                allMessages.add(m);
                                addedNew[0] = true;
                            }
                            oldestId = Math.min(oldestId, m.id);
                        }
                        if (oldestId != Integer.MAX_VALUE) chatOffsets.put(chatId, oldestId);
                        if (msgs.messages.size() < 20) finishedChats.add(chatId);
                    }
                }
                pendingCount[0]--;
                if (pendingCount[0] == 0) finishPage(addedNew[0]);
            }));
        }
    }

    private void finishPage(boolean addedNew) {
        if (addedNew) {
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
        boolean hasMore = false;
        for (Long chatId : chatIds) {
            if (!finishedChats.contains(chatId)) { hasMore = true; break; }
        }
        isLoading = false;
        callback.onResult(allMessages, hasMore);
    }

    private void onError(String error) {
        isLoading = false;
        if (callback != null) callback.onError(error);
    }
}