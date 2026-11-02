package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public class FriendActivityAdapter extends RecyclerListView.SelectionAdapter {

    private final int currentAccount;
    private final ArrayList<TLRPC.Message> messages = new ArrayList<>();
    private final ArrayList<GroupItem> groups = new ArrayList<>();
    private final ArrayList<RowItem> rows = new ArrayList<>();
    private final HashSet<Long> collapsedGroups = new HashSet<>();
    private final BaseFragment fragment;
    private final java.text.SimpleDateFormat activityTimeFormat =
            new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
    private final java.text.SimpleDateFormat activityDateFormat =
            new java.text.SimpleDateFormat("M月d日", Locale.getDefault());
    private long activityDayStartMillis;
    private long activityDayEndMillis;

    private static final int VIEW_TYPE_GROUP = 0;
    private static final int VIEW_TYPE_MESSAGE = 1;

    private static class GroupItem {
        final long dialogId;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();

        GroupItem(long dialogId) {
            this.dialogId = dialogId;
        }
    }

    private static class RowItem {
        final GroupItem group;
        final TLRPC.Message message;

        RowItem(GroupItem group, TLRPC.Message message) {
            this.group = group;
            this.message = message;
        }

        boolean isGroupHeader() {
            return message == null;
        }
    }

    public FriendActivityAdapter(int currentAccount, ArrayList<TLRPC.Message> messages, BaseFragment fragment) {
        this.currentAccount = currentAccount;
        this.fragment = fragment;
        setMessages(messages);
    }

    public void setMessages(ArrayList<TLRPC.Message> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        rebuildGroups();
        // RecyclerView now owns individual message rows, so a data refresh
        // only rebinds the visible window instead of constructing every row.
        notifyDataSetChanged();
    }

    private void rebuildGroups() {
        groups.clear();
        LinkedHashMap<Long, GroupItem> grouped = new LinkedHashMap<>();
        for (TLRPC.Message message : messages) {
            if (message == null || message.dialog_id == 0) continue;
            GroupItem group = grouped.get(message.dialog_id);
            if (group == null) {
                group = new GroupItem(message.dialog_id);
                grouped.put(message.dialog_id, group);
            }
            group.messages.add(message);
        }
        groups.addAll(grouped.values());

        // Keep each group's messages newest-first, then order groups by their
        // latest activity so the most recently active group appears at the top.
        for (GroupItem group : groups) {
            java.util.Collections.sort(group.messages, (first, second) -> {
                int dateCompare = Integer.compare(second.date, first.date);
                return dateCompare != 0 ? dateCompare : Integer.compare(second.id, first.id);
            });
        }
        java.util.Collections.sort(groups, (first, second) -> {
            int firstDate = first.messages.isEmpty() ? 0 : first.messages.get(0).date;
            int secondDate = second.messages.isEmpty() ? 0 : second.messages.get(0).date;
            int dateCompare = Integer.compare(secondDate, firstDate);
            return dateCompare != 0 ? dateCompare : Long.compare(first.dialogId, second.dialogId);
        });

        // Remove stale expansion state without disturbing groups that remain visible.
        HashSet<Long> existing = new HashSet<>();
        for (GroupItem group : groups) existing.add(group.dialogId);
        collapsedGroups.retainAll(existing);

        rebuildRows();
    }

    public long getGroupDialogIdAtPosition(int position) {
        return position >= 0 && position < rows.size() ? rows.get(position).group.dialogId : 0;
    }

    public int findGroupPosition(long dialogId) {
        if (dialogId == 0) return RecyclerView.NO_POSITION;
        for (int i = 0; i < rows.size(); i++) {
            RowItem row = rows.get(i);
            if (row.group.dialogId == dialogId && row.isGroupHeader()) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isGroupHeader() ? VIEW_TYPE_GROUP : VIEW_TYPE_MESSAGE;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new RecyclerListView.Holder(new FrameLayout(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        RowItem item = rows.get(position);
        FrameLayout container = (FrameLayout) holder.itemView;
        container.removeAllViews();
        View rowView = item.isGroupHeader()
                ? createGroupHeader(item.group, container.getContext())
                : createMessageRow(container.getContext(), item.message);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(item.isGroupHeader() ? 4 : 0),
                AndroidUtilities.dp(8), AndroidUtilities.dp(0));
        container.addView(rowView, params);
    }

    private View createGroupHeader(GroupItem group, android.content.Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        header.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_windowBackgroundWhite)));

        BackupImageView groupAvatar = new BackupImageView(context);
        groupAvatar.setRoundRadius(AndroidUtilities.dp(21));
        AvatarDrawable groupDrawable = new AvatarDrawable();
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-group.dialogId);
        if (chat != null) groupDrawable.setInfo(chat);
        groupAvatar.setImageDrawable(groupDrawable);
        if (chat != null && chat.photo != null) {
            ImageLocation location = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL);
            if (location != null) groupAvatar.setImage(location, "50_50", groupDrawable, chat);
        }
        header.addView(groupAvatar, LayoutHelper.createLinear(42, 42));

        TextView title = new TextView(context);
        title.setText(chat != null && chat.title != null ? chat.title : "未知群组");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(15);
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        header.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(context);
        arrow.setText(collapsedGroups.contains(group.dialogId) ? "⌄" : "⌃");
        arrow.setTextSize(20);
        arrow.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, LayoutHelper.createLinear(28, 38));
        header.setOnClickListener(v -> toggleGroup(group));
        return header;
    }

    private void toggleGroup(GroupItem group) {
        if (collapsedGroups.contains(group.dialogId)) {
            collapsedGroups.remove(group.dialogId);
        } else {
            collapsedGroups.add(group.dialogId);
        }
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        rows.clear();
        for (GroupItem group : groups) {
            rows.add(new RowItem(group, null));
            if (!collapsedGroups.contains(group.dialogId)) {
                for (TLRPC.Message message : group.messages) {
                    rows.add(new RowItem(group, message));
                }
            }
        }
    }

    private View createMessageRow(android.content.Context context, TLRPC.Message message) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(3), AndroidUtilities.dp(10), AndroidUtilities.dp(3));
        row.setMinimumHeight(AndroidUtilities.dp(46));
        row.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.getColor(Theme.key_windowBackgroundWhite)));

        BackupImageView senderAvatar = new BackupImageView(context);
        senderAvatar.setRoundRadius(AndroidUtilities.dp(16));
        TLRPC.User sender = null;
        if (message.from_id instanceof TLRPC.TL_peerUser) {
            sender = MessagesController.getInstance(currentAccount).getUser(message.from_id.user_id);
        }
        AvatarDrawable senderDrawable = new AvatarDrawable();
        if (sender != null) senderDrawable.setInfo(sender);
        senderAvatar.setImageDrawable(senderDrawable);
        if (sender != null && sender.photo != null) {
            ImageLocation location = ImageLocation.getForUser(sender, ImageLocation.TYPE_SMALL);
            if (location != null) senderAvatar.setImage(location, "32_32", senderDrawable, sender);
        }
        row.addView(senderAvatar, LayoutHelper.createLinear(32, 32));

        MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
        CharSequence preview = messageObject.messageText;
        String text = preview != null && preview.length() > 0
                ? preview.toString() : getActivityMediaText(message);
        TextView messageView = new TextView(context);
        messageView.setText(text);
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageView.setTextSize(14);
        messageView.setSingleLine(true);
        messageView.setEllipsize(TextUtils.TruncateAt.END);
        messageView.setGravity(Gravity.CENTER_VERTICAL);
        messageView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(6), 0);
        row.addView(messageView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        TextView timeView = new TextView(context);
        timeView.setText(formatMessageTime(context, message.date));
        timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        timeView.setTextSize(12);
        timeView.setSingleLine(true);
        timeView.setMaxWidth(AndroidUtilities.dp(92));
        timeView.setEllipsize(TextUtils.TruncateAt.END);
        timeView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(timeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        row.setOnClickListener(v -> {
            if (fragment == null || message.dialog_id == 0 || message.id == 0) return;
            fragment.presentFragment(ChatActivity.of(message.dialog_id, message.id));
        });
        return row;
    }

    private void updateActivityDayBounds() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        activityDayStartMillis = today.getTimeInMillis();
        today.add(Calendar.DAY_OF_YEAR, 1);
        activityDayEndMillis = today.getTimeInMillis();
    }

    private String formatMessageTime(android.content.Context context, int date) {
        long time = (long) date * 1000L;
        long now = System.currentTimeMillis();
        if (activityDayStartMillis == 0 || now < activityDayStartMillis || now >= activityDayEndMillis) {
            updateActivityDayBounds();
        }
        if (time >= activityDayStartMillis && time < activityDayEndMillis) {
            return activityTimeFormat.format(time);
        }
        return activityDateFormat.format(time) + "  " + activityTimeFormat.format(time);
    }

    private String getActivityMediaText(TLRPC.Message message) {
        if (message == null || message.media == null) return "";
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) return "📷 图片消息";
        if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.Document document = message.media.document;
            if (document == null) return "📎 文件消息";
            if (document.attributes != null) {
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) return "🎬 视频消息";
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) return "🎵 音频消息";
                    if (attribute instanceof TLRPC.TL_documentAttributeAnimated) return "🎞 动图消息";
                }
            }
            return "📎 文件消息";
        }
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) return "🔗 网页链接";
        return "媒体消息";
    }
}