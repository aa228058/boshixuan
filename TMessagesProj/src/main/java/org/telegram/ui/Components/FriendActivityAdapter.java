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

public class FriendActivityAdapter extends BaseAdapter {

    private final int currentAccount;
    private final ArrayList<TLRPC.Message> messages;
    private final BaseFragment fragment;

    public FriendActivityAdapter(int currentAccount, ArrayList<TLRPC.Message> messages, BaseFragment fragment) {
        this.currentAccount = currentAccount;
        this.messages = messages;
        this.fragment = fragment;
    }

    public void setMessages(ArrayList<TLRPC.Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return messages.size(); }

    @Override
    public Object getItem(int position) { return messages.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        android.content.Context context = parent.getContext();
        FrameLayout wrapper;
        
        if (convertView == null) {
            wrapper = new FrameLayout(context);
        } else {
            wrapper = (FrameLayout) convertView;
        }
        wrapper.removeAllViews();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TLRPC.Message msg = messages.get(position);
        MessageObject mo = new MessageObject(currentAccount, msg, false, false);
        
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(24));
        AvatarDrawable drawable = new AvatarDrawable();
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-msg.dialog_id);
        if (chat != null) drawable.setInfo(chat);
        avatar.setImageDrawable(drawable);
        
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48));
        avatarLp.rightMargin = AndroidUtilities.dp(12);
        layout.addView(avatar, avatarLp);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView titleView = new TextView(context);
        titleView.setText(chat != null ? chat.title : "未知群组");
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        topRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        
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
        timeView.setTextSize(13);
        topRow.addView(timeView);
        contentLayout.addView(topRow);

        TextView msgView = new TextView(context);
        String text = (mo.messageText != null && mo.messageText.length() > 0) ? mo.messageText.toString() : getActivityMediaText(msg);
        msgView.setText(text);
        msgView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        msgView.setTextSize(15);
        msgView.setMaxLines(3);
        msgView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        msgView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        contentLayout.addView(msgView);
        layout.addView(contentLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        
        mo.generateThumbs(false);
        if (mo.photoThumbs != null && !mo.photoThumbs.isEmpty() && mo.photoThumbsObject != null) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(mo.photoThumbs, 100);
            if (thumb != null) {
                ImageLocation imageLocation = ImageLocation.getForObject(thumb, mo.photoThumbsObject);
                if (imageLocation != null) {
                    BackupImageView mediaThumb = new BackupImageView(context);
                    mediaThumb.setRoundRadius(AndroidUtilities.dp(6));
                    mediaThumb.setImage(imageLocation, "72_72", (android.graphics.drawable.Drawable) null, mo);
                    LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(72), AndroidUtilities.dp(72));
                    thumbParams.leftMargin = AndroidUtilities.dp(8);
                    layout.addView(mediaThumb, thumbParams);
                }
            }
        }
        
        View line = new View(context);
        line.setBackgroundColor(Theme.getColor(Theme.key_divider));
        
        wrapper.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        wrapper.addView(line, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 72, 0, 0, 0));

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