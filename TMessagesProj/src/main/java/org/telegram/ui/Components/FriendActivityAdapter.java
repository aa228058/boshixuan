package org.telegram.ui.Components;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class FriendActivityAdapter extends BaseAdapter {

    private static final int VIEW_TYPE_GROUP = 0;
    private static final int VIEW_TYPE_MESSAGE = 1;

    private final int currentAccount;
    private final ArrayList<TLRPC.Message> messages;
    private final ArrayList<ActivityRow> rows = new ArrayList<>();
    private final BaseFragment fragment;

    private static class ActivityRow {
        final boolean groupHeader;
        final long dialogId;
        final TLRPC.Message message;
        final int messageCount;

        private ActivityRow(boolean groupHeader, long dialogId, TLRPC.Message message, int messageCount) {
            this.groupHeader = groupHeader;
            this.dialogId = dialogId;
            this.message = message;
            this.messageCount = messageCount;
        }

        static ActivityRow group(long dialogId, int count) {
            return new ActivityRow(true, dialogId, null, count);
        }

        static ActivityRow message(TLRPC.Message message) {
            return new ActivityRow(false, message.dialog_id, message, 0);
        }
    }

    public FriendActivityAdapter(int currentAccount, ArrayList<TLRPC.Message> messages, BaseFragment fragment) {
        this.currentAccount = currentAccount;
        this.messages = new ArrayList<>();
        this.fragment = fragment;
        setMessages(messages);
    }

    public void setMessages(ArrayList<TLRPC.Message> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        rows.clear();
        // FriendActivityPage already sorts by group and then newest message first.
        // Keep that order while creating exactly one header for each consecutive group.
        LinkedHashMap<Long, ArrayList<TLRPC.Message>> grouped = new LinkedHashMap<>();
        for (TLRPC.Message message : messages) {
            if (message == null || message.dialog_id == 0) continue;
            ArrayList<TLRPC.Message> group = grouped.get(message.dialog_id);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(message.dialog_id, group);
            }
            group.add(message);
        }

        for (Map.Entry<Long, ArrayList<TLRPC.Message>> entry : grouped.entrySet()) {
            rows.add(ActivityRow.group(entry.getKey(), entry.getValue().size()));
            for (TLRPC.Message message : entry.getValue()) {
                rows.add(ActivityRow.message(message));
            }
        }
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        ActivityRow row = rows.get(position);
        return row.groupHeader ? row.dialogId : (((long) row.message.id) << 32) ^ row.dialogId;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).groupHeader ? VIEW_TYPE_GROUP : VIEW_TYPE_MESSAGE;
    }

    @Override
    public boolean isEnabled(int position) {
        return !rows.get(position).groupHeader;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ActivityRow row = rows.get(position);
        if (row.groupHeader) {
            return getGroupHeaderView(row, convertView, parent);
        }
        return getMessageView(row.message, convertView, parent);
    }

    private View getGroupHeaderView(ActivityRow row, View convertView, ViewGroup parent) {
        android.content.Context context = parent.getContext();
        LinearLayout header;
        if (convertView instanceof LinearLayout) {
            header = (LinearLayout) convertView;
        } else {
            header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(8));

            BackupImageView avatar = new BackupImageView(context);
            avatar.setTag("activity_group_avatar");
            avatar.setRoundRadius(AndroidUtilities.dp(22));
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44));
            avatarParams.rightMargin = AndroidUtilities.dp(12);
            header.addView(avatar, avatarParams);

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setTag("activity_group_title");
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(15);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine(true);
            textColumn.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView count = new TextView(context);
            count.setTag("activity_group_count");
            count.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            count.setTextSize(12);
            textColumn.addView(count, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            header.addView(textColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            View divider = new View(context);
            divider.setTag("activity_group_divider");
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            header.addView(divider, LayoutHelper.createLinear(AndroidUtilities.dp(1), AndroidUtilities.dp(32)));
        }

        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-row.dialogId);
        BackupImageView avatar = (BackupImageView) header.findViewWithTag("activity_group_avatar");
        AvatarDrawable drawable = new AvatarDrawable();
        if (chat != null) drawable.setInfo(chat);
        avatar.setImageDrawable(drawable);
        if (chat != null && chat.photo != null) {
            ImageLocation location = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL);
            if (location == null) location = ImageLocation.getForChat(chat, ImageLocation.TYPE_BIG);
            if (location == null) location = ImageLocation.getForChat(chat, ImageLocation.TYPE_STRIPPED);
            if (location != null) {
                avatar.setImage(location, "50_50", drawable, chat);
            }
        }

        TextView title = (TextView) header.findViewWithTag("activity_group_title");
        title.setText(chat != null && chat.title != null ? chat.title : "未知群组");
        TextView count = (TextView) header.findViewWithTag("activity_group_count");
        count.setText(row.messageCount + " 条活动消息");
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return header;
    }

    private View getMessageView(TLRPC.Message msg, View convertView, ViewGroup parent) {
        android.content.Context context = parent.getContext();
        FrameLayout wrapper;
        if (convertView instanceof FrameLayout) {
            wrapper = (FrameLayout) convertView;
        } else {
            wrapper = new FrameLayout(context);
        }
        wrapper.removeAllViews();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        // Indent message content beneath the group title; the group avatar/name is shown once.
        layout.setPadding(AndroidUtilities.dp(68), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(9));
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        MessageObject messageObject = new MessageObject(currentAccount, msg, false, false);
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView timeView = new TextView(context);
        long messageTime = (long) msg.date * 1000;
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
        java.util.Calendar messageCalendar = java.util.Calendar.getInstance();
        messageCalendar.setTimeInMillis(messageTime);
        java.util.Calendar today = java.util.Calendar.getInstance();
        if (messageCalendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                && messageCalendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) {
            timeView.setText(timeFormat.format(messageTime));
        } else {
            timeView.setText(dateFormat.format(messageTime));
        }
        timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        timeView.setTextSize(12);
        topRow.addView(timeView);
        contentLayout.addView(topRow);

        TextView messageView = new TextView(context);
        String text = (messageObject.messageText != null && messageObject.messageText.length() > 0)
                ? messageObject.messageText.toString() : getActivityMediaText(msg);
        messageView.setText(text);
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageView.setTextSize(14);
        messageView.setMaxLines(3);
        messageView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        messageView.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        contentLayout.addView(messageView);
        layout.addView(contentLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        messageObject.generateThumbs(false);
        if (messageObject.photoThumbs != null && !messageObject.photoThumbs.isEmpty()
                && messageObject.photoThumbsObject != null) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
            if (thumb != null) {
                ImageLocation imageLocation = ImageLocation.getForObject(thumb, messageObject.photoThumbsObject);
                if (imageLocation != null) {
                    BackupImageView mediaThumb = new BackupImageView(context);
                    mediaThumb.setRoundRadius(AndroidUtilities.dp(6));
                    mediaThumb.setImage(imageLocation, "72_72", (android.graphics.drawable.Drawable) null, messageObject);
                    LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64));
                    thumbParams.leftMargin = AndroidUtilities.dp(8);
                    layout.addView(mediaThumb, thumbParams);
                }
            }
        }

        wrapper.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        wrapper.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 68, 0, 0, 0));

        wrapper.setOnClickListener(v -> {
            if (fragment == null) return;
            long dialogId = msg.dialog_id;
            int messageId = msg.id;
            if (dialogId == 0 || messageId == 0) return;
            fragment.presentFragment(ChatActivity.of(dialogId, messageId));
        });
        return wrapper;
    }

    private String getActivityMediaType(TLRPC.Message message) {
        if (message == null || message.media == null) return "TEXT";
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) return "PHOTO";
        if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.Document document = message.media.document;
            if (document == null) return "FILE";
            for (TLRPC.DocumentAttribute attribute : document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) return "VIDEO";
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) return "AUDIO";
                if (attribute instanceof TLRPC.TL_documentAttributeAnimated) return "GIF";
            }
            return "FILE";
        }
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) return "LINK";
        return "OTHER";
    }

    private String getActivityMediaText(TLRPC.Message message) {
        String type = getActivityMediaType(message);
        switch (type) {
            case "PHOTO": return "📷 图片消息";
            case "VIDEO": return "🎬 视频消息";
            case "AUDIO": return "🎵 音频消息";
            case "GIF": return "🎞 动图消息";
            case "FILE": return "📎 文件消息";
            case "LINK": return "🔗 网页链接";
            default: return "[媒体消息]";
        }
    }
}