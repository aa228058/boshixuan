package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * Strict reply matching for the cross-account group inbox.
 *
 * A message is eligible only when Telegram supplied an actual reply header,
 * the reply points into the same group, and the referenced original message
 * is present in this account's cache and is marked outgoing by that account.
 *
 * Missing reply targets are deliberately rejected. We must not infer a reply
 * from message order, timestamps, or the latest message in a dialog.
 */
public final class CrossAccountInboxReplyMatcher {

    private CrossAccountInboxReplyMatcher() {
    }

    public static boolean isReplyToOwnGroupMessage(int account, long dialogId, MessageObject message) {
        return isReplyToOwnGroupMessage(account, dialogId, message, null);
    }

    public static boolean isReplyToOwnGroupMessage(int account, long dialogId, MessageObject message,
                                                   java.util.ArrayList<MessageObject> candidates) {
        if (message == null || message.messageOwner == null || message.messageOwner.out
                || message.deleted || message.getDialogId() != dialogId) {
            return false;
        }

        // Private incoming messages belong in the unified inbox as well. Exclude
        // Saved Messages and encrypted-chat dialog ids; this store currently
        // keeps ordinary user-to-user conversations only.
        if (dialogId > 0) {
            UserConfig config = UserConfig.getInstance(account);
            return !DialogObject.isEncryptedDialog(dialogId)
                    && dialogId != config.getClientUserId();
        }
        if (dialogId == 0) {
            return false;
        }

        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat chat = controller.getChat(-dialogId);
        if (chat == null || ChatObject.isChannel(chat) && !chat.megagroup && !chat.gigagroup) {
            // Include basic groups and megagroups, but not broadcast channels.
            return false;
        }

        TLRPC.MessageReplyHeader reply = message.messageOwner.reply_to;
        if (reply == null || reply.reply_to_msg_id <= 0) {
            return false;
        }

        // reply_to_peer_id is optional: when absent, Telegram means this dialog.
        // When present, reject replies that point to a different peer/dialog.
        if (reply.reply_to_peer_id != null) {
            long replyDialogId = getDialogId(reply.reply_to_peer_id);
            if (replyDialogId == 0 || replyDialogId != dialogId) {
                return false;
            }
        }

        // The normal reply loader may have resolved the exact target asynchronously
        // before this retry. Prefer that object, then fall back to account-local caches.
        MessageObject target = message.replyMessageObject;
        if (target == null || target.messageOwner == null || target.deleted
                || target.getId() != reply.reply_to_msg_id || target.getDialogId() != dialogId) {
            target = controller.dialogMessagesByIds.get(reply.reply_to_msg_id);
        }
        if (target == null || target.messageOwner == null || target.deleted
                || target.getId() != reply.reply_to_msg_id || target.getDialogId() != dialogId) {
            target = findCachedMessage(controller, dialogId, reply.reply_to_msg_id);
        }
        if (target == null) {
            target = findCandidateMessage(candidates, dialogId, reply.reply_to_msg_id);
        }

        // Never classify a message unless its exact referenced target is known
        // and that target was sent by the currently signed-in account.
        return target != null && target.messageOwner != null && !target.deleted
                && target.getId() == reply.reply_to_msg_id
                && target.getDialogId() == dialogId
                && target.messageOwner.out;
    }

    private static MessageObject findCandidateMessage(java.util.ArrayList<MessageObject> candidates,
                                                      long dialogId, int messageId) {
        if (candidates == null) {
            return null;
        }
        for (int i = 0; i < candidates.size(); i++) {
            MessageObject candidate = candidates.get(i);
            if (candidate != null && candidate.messageOwner != null && !candidate.deleted
                    && candidate.getId() == messageId && candidate.getDialogId() == dialogId) {
                return candidate;
            }
        }
        return null;
    }

    private static MessageObject findCachedMessage(MessagesController controller, long dialogId, int messageId) {
        java.util.ArrayList<MessageObject> messages = controller.dialogMessage.get(dialogId);
        if (messages == null) {
            return null;
        }
        for (int i = 0; i < messages.size(); i++) {
            MessageObject candidate = messages.get(i);
            if (candidate != null && candidate.messageOwner != null && !candidate.deleted
                    && candidate.getId() == messageId && candidate.getDialogId() == dialogId) {
                return candidate;
            }
        }
        return null;
    }

    private static long getDialogId(TLRPC.Peer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer.user_id != 0) {
            return peer.user_id;
        }
        if (peer.chat_id != 0) {
            return -peer.chat_id;
        }
        if (peer.channel_id != 0) {
            return -peer.channel_id;
        }
        return 0;
    }
}
