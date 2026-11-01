package org.telegram.ui;

import org.telegram.messenger.R;
import java.util.ArrayList;

/** Searchable child settings; destinations reuse the same handlers as their parent entries. */
final class MangoSettingsSearchIndex {
    static final class Entry {
        final int destination, title, pageTitle;
        final String keywords;
        Entry(int destination, int title, int pageTitle, String keywords) {
            this.destination = destination; this.title = title; this.pageTitle = pageTitle; this.keywords = keywords;
        }
    }
    static ArrayList<Entry> entries() {
        ArrayList<Entry> entries = new ArrayList<>();
        entries.add(new Entry(92, R.string.MangoBroadcastTitle, R.string.MangoBroadcastTitle, "私聊 群发 用户 批量 三秒 3秒 broadcast private users"));
        entries.add(new Entry(91, R.string.MangoAutoReadReplyAddGroup, R.string.MangoAutoReadReplyGroups, "已读乱回 自动已读 随机回复 群组 私聊 random reply groups private"));
        entries.add(new Entry(85, R.string.MangoUnifiedBottomTab, R.string.MangoUnifiedInbox, "跨账号 多账号 汇总 统一 消息 inbox accounts"));
        entries.add(new Entry(86, R.string.MangoUnifiedInbox, R.string.MangoUnifiedInbox, "跨账号 多账号 汇总 统一 消息 inbox accounts"));
        entries.add(new Entry(87, R.string.MangoRestrictionTitleSetting, R.string.AppIdentitySettings, "双向 限制 名称 标题 博士轩客户端"));
        entries.add(new Entry(88, R.string.DownloadRetentionTitle, R.string.DownloadRetentionTitle, "文件 保留 一天 一周 永久 到期 清理 retention download"));
        entries.add(new Entry(49, R.string.MessageDoubleTapAction, R.string.ExtensionMessageActions, "double tap menu order 双击菜单排序"));
        entries.add(new Entry(49, R.string.MessageDoubleTapReaction, R.string.ExtensionMessageActions, "double tap menu order 双击菜单排序"));
        entries.add(new Entry(49, R.string.MessageDoubleTapNone, R.string.ExtensionMessageActions, "double tap menu order 双击菜单排序"));
        entries.add(new Entry(49, R.string.MessageMenuOrder, R.string.ExtensionMessageActions, "double tap menu order 双击菜单排序"));
        entries.add(new Entry(49, R.string.MessageMenuMove, R.string.ExtensionMessageActions, "double tap menu order 双击菜单排序"));
        entries.add(new Entry(49, R.string.MessageMenuReset, R.string.ExtensionMessageActions, "double tap menu order 双击菜单排序"));
        entries.add(new Entry(48, R.string.MessageTagImportant, R.string.MessageManagementTitle, "消息标签 待处理 待回复 收藏 标签管理"));
        entries.add(new Entry(48, R.string.MessageTagTodo, R.string.MessageManagementTitle, "消息标签 待处理 待回复 收藏 标签管理"));
        entries.add(new Entry(48, R.string.MessageTagReply, R.string.MessageManagementTitle, "消息标签 待处理 待回复 收藏 标签管理"));
        entries.add(new Entry(48, R.string.MessageTagFavorite, R.string.MessageManagementTitle, "消息标签 待处理 待回复 收藏 标签管理"));
        entries.add(new Entry(53, R.string.V4ScamProtectionEnabled, R.string.V4ScamEntry, "反诈 诈骗 风险 词库 两小时 2小时 scam fraud"));
        entries.add(new Entry(53, R.string.V4ScamKeywords, R.string.V4ScamEntry, "反诈 诈骗 风险 词库 两小时 2小时 scam fraud"));
        entries.add(new Entry(53, R.string.V4ScamKeywordsUpdateNow, R.string.V4ScamEntry, "反诈 诈骗 风险 词库 两小时 2小时 scam fraud"));
        entries.add(new Entry(53, R.string.V4RestoreScamKeywords, R.string.V4ScamEntry, "反诈 诈骗 风险 词库 两小时 2小时 scam fraud"));
        entries.add(new Entry(45, R.string.DailyMessageTasksAdd, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksAutomatic, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksSendNow, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksText, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksTime, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksMode, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksRandomMode, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksFixedMode, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksWindowStart, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(45, R.string.DailyMessageTasksWindowEnd, R.string.ExtensionDailyMessages, "定时 签到 自动 手动 时间 随机 每日 schedule"));
        entries.add(new Entry(46, R.string.PresenceSimulationMode, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationOnline, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationOffline, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationTarget, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationAction, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationDuration, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationTyping, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationRecording, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(46, R.string.PresenceSimulationSticker, R.string.ExtensionPresence, "伪在线 伪离线 伪输入 状态 时间 presence typing"));
        entries.add(new Entry(10, R.string.AutoReplyEnable, R.string.AutoReplySettings, "自动回复 关键词 预设 话术 auto reply"));
        entries.add(new Entry(10, R.string.AutoReplyAnyMessageEnable, R.string.AutoReplySettings, "自动回复 关键词 预设 话术 auto reply"));
        entries.add(new Entry(10, R.string.AutoReplyAnyMessageMessages, R.string.AutoReplySettings, "自动回复 关键词 预设 话术 auto reply"));
        entries.add(new Entry(10, R.string.AutoReplyAddRule, R.string.AutoReplySettings, "自动回复 关键词 预设 话术 auto reply"));
        entries.add(new Entry(10, R.string.AutoReplyRules, R.string.AutoReplySettings, "自动回复 关键词 预设 话术 auto reply"));
        entries.add(new Entry(8, R.string.KeywordFilterEnable, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterRemoteUpdates, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.BlockSponsoredMessagesFull, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterAddKeyword, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterKeywords, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterWhitelist, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterAddWhitelist, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterLogs, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(8, R.string.KeywordFilterClearLogs, R.string.KeywordFilterSettings, "过滤 屏蔽 白名单 广告 keyword filter"));
        entries.add(new Entry(56, R.string.DuplicateFilesScan, R.string.V4DuplicateEntry, "重复文件 扫描 清理 文件去重"));
        entries.add(new Entry(56, R.string.DuplicateFilesSmartSelect, R.string.V4DuplicateEntry, "重复文件 扫描 清理 文件去重"));
        entries.add(new Entry(56, R.string.DuplicateFilesClean, R.string.V4DuplicateEntry, "重复文件 扫描 清理 文件去重"));
        entries.add(new Entry(57, R.string.ChannelBatchLeaveScope, R.string.V4BatchLeaveEntry, "退群 频道 批量退出"));
        entries.add(new Entry(57, R.string.ChannelBatchLeaveRefresh, R.string.V4BatchLeaveEntry, "退群 频道 批量退出"));
        entries.add(new Entry(57, R.string.ChannelBatchLeaveSelectVisible, R.string.V4BatchLeaveEntry, "退群 频道 批量退出"));
        entries.add(new Entry(57, R.string.ChannelBatchLeaveStop, R.string.V4BatchLeaveEntry, "退群 频道 批量退出"));
        entries.add(new Entry(55, R.string.V4MemorialCreate, R.string.V4MemorialEntry, "快照 纪念册 保存聊天"));
        entries.add(new Entry(44, R.string.SelectFromGallery, R.string.ExtensionPersonalWallpapers, "自定义壁纸 上传图片 背景 wallpaper"));
        entries.add(new Entry(51, R.string.V4RecalledArchive, R.string.V4RecalledArchive, "撤回 归档 防撤回 archive"));
        entries.add(new Entry(82, R.string.MangoNotificationRules, R.string.MangoNotificationRules, "提醒 @我 mentions 关键词 独立 通知"));
        entries.add(new Entry(83, R.string.MangoScamUsers, R.string.MangoScamUsers, "诈骗名单 用户名 永久ID 直链 拉黑 举报 scam user"));
        entries.add(new Entry(84, R.string.MangoBuiltinThemes, R.string.MangoBuiltinThemes, "内置 主题 外观 配色 theme appearance"));
        entries.add(new Entry(89, R.string.MangoPerformanceTitle, R.string.MangoPerformanceTitle, "性能 内存 电池 耗电 缓存 后台任务 monitor performance memory battery"));
        entries.add(new Entry(90, R.string.MangoDownloadHudTitle, R.string.MangoDownloadHudTitle, "下载速度 浮窗 任务数 download speed floating"));
        return entries;
    }
}
