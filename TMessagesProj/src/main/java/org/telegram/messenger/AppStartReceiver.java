package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AppStartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        String action = intent.getAction();
        
        // 同时监听开机广播和咱们服务被杀后发出的自救广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "org.telegram.start".equals(action)) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedConfig.loadConfig();
                // 如果设置了密码锁，开机后先锁定应用
                if (SharedConfig.passcodeHash.length() > 0) {
                    SharedConfig.appLocked = true;
                    SharedConfig.saveConfig();
                }
                // 拉起保活服务
                ApplicationLoader.startPushService();
            });
        }
    }
}