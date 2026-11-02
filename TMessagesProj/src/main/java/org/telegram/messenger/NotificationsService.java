package org.telegram.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LaunchActivity;

public class NotificationsService extends Service {

    private static final String CHANNEL_ID = "mango_push";
    private static final int NOTIFICATION_ID = 9999;

    private static PowerManager.WakeLock cpuWakeLock;
    private static WifiManager.WifiLock wifiLock;
    private static long lastRestartTime = 0;
    private static long lastConnectionCheckTime = 0;
    private static final long RESTART_COOLDOWN_MS = 60_000; // 广播防抖：60秒内不重复拉起
    private static final long CONNECTION_CHECK_COOLDOWN_MS = 30_000; // 防止服务短时间重启时反复遍历账号

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();

        // 1. 申请 CPU 和 WiFi 唤醒锁（防止锁屏休眠断网）
        acquireLocks();
        
        // 2. 创建通知渠道
        createChannel();

        // 3. 无条件前台化（防止 8.0+ 后台启动 5秒 ANR 崩溃）
        Notification notification = buildNotification();
        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Throwable e) {
            FileLog.e(e);
            stopSelf(); // 如果前台化失败，直接停止服务，避免无意义地挂起
        }
    }

    private void acquireLocks() {
        if (cpuWakeLock == null) {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mango:PushCpuLock");
                    cpuWakeLock.setReferenceCounted(false);
                    cpuWakeLock.acquire();
                }
            } catch (Throwable e) { FileLog.e(e); }
        }
        
        if (wifiLock == null) {
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    // A normal full Wi-Fi lock is sufficient to keep the radio awake
                    // for the background connection. HIGH_PERF adds sustained radio
                    // performance/power pressure without improving message delivery.
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "Mango:PushWifiLock");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                }
            } catch (Throwable e) { FileLog.e(e); }
        }
    }

    private void releaseLocks() {
        try {
            if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
                cpuWakeLock.release();
            }
        } catch (Throwable ignore) {
        } finally {
            // Do not retain a released static lock: the next service instance must
            // create and acquire a fresh lock in acquireLocks().
            cpuWakeLock = null;
        }

        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (Throwable ignore) {
        } finally {
            wifiLock = null;
        }
    }

    private void checkConnectionAndWakeup() {
        long now = System.currentTimeMillis();
        if (now - lastConnectionCheckTime < CONNECTION_CHECK_COOLDOWN_MS) {
            return;
        }
        lastConnectionCheckTime = now;
        try {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).isClientActivated()) {
                    // 唤醒底层网络并检查连接
                    ConnectionsManager.getInstance(i).checkConnection();
                    ConnectionsManager.getInstance(i).resumeNetworkMaybe();
                }
            }
        } catch (Throwable ignore) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                    CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("后台消息服务")
                    .setDescription("保持后台连接以接收消息")
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .setSound(null, null)
                    .setShowBadge(false)
                    .build();
            NotificationManagerCompat.from(this).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent startIntent = new Intent(this, LaunchActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, startIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification) // 使用 Mango 自带的图标
                .setShowWhen(false)
                .setOngoing(true) // 常驻，不可划掉
                .setContentTitle("Mango")
                .setContentText("后台消息服务运行中")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 每次被闹钟或系统拉起时，主动检查一次连接
        checkConnectionAndWakeup();
        return START_STICKY; // 被杀后尝试自动重建
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseLocks(); // 释放锁，防止耗电
        
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (preferences.getBoolean("pushService", true)) {
            long now = System.currentTimeMillis();
            if (now - lastRestartTime > RESTART_COOLDOWN_MS) {
                lastRestartTime = now;
                // 发送广播拉起自身
                Intent intent = new Intent("org.telegram.start");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }
        }
    }
}