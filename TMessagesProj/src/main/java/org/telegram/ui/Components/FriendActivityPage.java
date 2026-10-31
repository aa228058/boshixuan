package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.Collections;

public class FriendActivityPage {
    public interface Callback {
        void onResult(ArrayList<TLRPC.Message> messages);
        void onError(String error);
    }

    public static void fetch(int currentAccount, long userId, Callback callback) {
        TLRPC.TL_messages_getCommonChats req = new TLRPC.TL_messages_getCommonChats();
        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(userId);
        req.limit = 20; // 拉取最近20个共同群组

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null || response == null) {
                callback.onError(error != null ? error.text : "网络请求失败");
                return;
            }
            TLRPC.messages_Chats res = (TLRPC.messages_Chats) response;
            if (res.chats.isEmpty()) {
                callback.onResult(new ArrayList<>());
                return;
            }
            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
            
            ArrayList<Long> chatIds = new ArrayList<>();
            for (TLRPC.Chat c : res.chats) chatIds.add(c.id);

            ArrayList<TLRPC.Message> allMessages = new ArrayList<>();
            final int[] pendingCount = {chatIds.size()};

            for (long chatId : chatIds) {
                TLRPC.TL_messages_search searchReq = new TLRPC.TL_messages_search();
                searchReq.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
                searchReq.q = ""; 
                searchReq.from_id = MessagesController.getInstance(currentAccount).getInputUser(userId);
                searchReq.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                searchReq.limit = 10; // 每个群拉取10条
                
                ConnectionsManager.getInstance(currentAccount).sendRequest(searchReq, (searchResp, searchErr) -> AndroidUtilities.runOnUIThread(() -> {
                    if (searchResp instanceof TLRPC.messages_Messages) {
                        TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) searchResp;
                        if (msgs.messages != null) {
                            MessagesController.getInstance(currentAccount).putUsers(msgs.users, false);
                            MessagesController.getInstance(currentAccount).putChats(msgs.chats, false);
                            for (TLRPC.Message m : msgs.messages) {
                                m.dialog_id = -chatId;
                                allMessages.add(m);
                            }
                        }
                    }
                    pendingCount[0]--;
                    if (pendingCount[0] <= 0) {
                        Collections.sort(allMessages, (m1, m2) -> Integer.compare(m2.date, m1.date));
                        callback.onResult(allMessages);
                    }
                }));
            }
        }));
    }
}