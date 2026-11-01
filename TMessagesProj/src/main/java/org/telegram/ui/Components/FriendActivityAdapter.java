package org.telegram.ui.Components;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class FriendActivityAdapter extends BaseAdapter {

    private final ArrayList<TLRPC.Message> messages;
    private final int currentAccount = UserConfig.selectedAccount;

    public FriendActivityAdapter(ArrayList<TLRPC.Message> messages) {
        this.messages = messages;
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
        
        // 1. 头像
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(24));
        AvatarDrawable drawable = new AvatarDrawable();
        
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-msg.dialog_id);
if (chat != null) {
    drawable.setInfo(chat);
}
// 直接使用纯色底色，不加载真实图片，绝对不报错
avatar.setImageDrawable(drawable);

LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48));
avatarLp.rightMargin = AndroidUtilities.dp(12);
layout.addView(avatar, avatarLp);
        
        // 2. 内容
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
// 使用原生的 DateFormat，完美解决私有变量报错
timeView.setText(android.text.format.DateFormat.format("MM-dd HH:mm", (long) msg.date * 1000));
timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
timeView.setTextSize(13);
topRow.addView(timeView);
contentLayout.addView(topRow);

        TextView msgView = new TextView(context);
        String text = mo.messageText != null ? mo.messageText.toString() : "[媒体消息]";
        msgView.setText(text);
        msgView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        msgView.setTextSize(15);
        msgView.setMaxLines(3);
        msgView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        msgView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        contentLayout.addView(msgView);

        layout.addView(contentLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        
        View line = new View(context);
        line.setBackgroundColor(Theme.getColor(Theme.key_divider));
        
        wrapper.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        wrapper.addView(line, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 72, 0, 0, 0));

        return wrapper;
    }
}