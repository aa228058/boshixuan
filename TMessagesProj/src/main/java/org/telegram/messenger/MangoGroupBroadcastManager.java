package org.telegram.messenger;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;

/** Sequential, controllable scheduler for group broadcast tasks. */
public final class MangoGroupBroadcastManager {
    private static final HashMap<String, Runner> RUNNERS = new HashMap<>();

    private MangoGroupBroadcastManager() {
    }

    private static String key(int account, long taskId) {
        return account + ":" + taskId;
    }

    public static long start(int account, String text, ArrayList<Long> targetIds,
            ArrayList<String> targetNames, int minSeconds, int maxSeconds) {
        if (TextUtils.isEmpty(text) || targetIds == null || targetIds.isEmpty()) return 0;
        long taskId = MangoGroupBroadcastTaskStore.create(account, text, targetNames, targetIds, minSeconds, maxSeconds);
        Runner runner = new Runner(account, taskId, Math.max(1, minSeconds), Math.max(minSeconds + 1, maxSeconds), 0);
        RUNNERS.put(key(account, taskId), runner);
        runner.scheduleNext();
        return taskId;
    }

    public static boolean pause(int account, long taskId) {
        Runner runner = RUNNERS.remove(key(account, taskId));
        if (runner == null) return false;
        runner.stopPending();
        MangoGroupBroadcastTaskStore.setState(account, taskId, MangoGroupBroadcastTaskStore.PAUSED);
        return true;
    }

    public static boolean resume(int account, long taskId) {
        String key = key(account, taskId);
        if (RUNNERS.containsKey(key)) return false;
        MangoGroupBroadcastTaskStore.Task task = MangoGroupBroadcastTaskStore.getTask(account, taskId);
        if (task == null || (task.state != MangoGroupBroadcastTaskStore.PAUSED
                && task.state != MangoGroupBroadcastTaskStore.INTERRUPTED)
                || task.processed >= task.total || task.targetIds.size() != task.total) return false;
        MangoGroupBroadcastTaskStore.setState(account, taskId, MangoGroupBroadcastTaskStore.SENDING);
        Runner runner = new Runner(account, taskId, Math.max(1, task.minSeconds),
                Math.max(task.minSeconds + 1, task.maxSeconds), task.processed);
        RUNNERS.put(key, runner);
        runner.scheduleNext();
        return true;
    }

    public static boolean cancel(int account, long taskId) {
        Runner runner = RUNNERS.remove(key(account, taskId));
        if (runner != null) runner.stopPending();
        MangoGroupBroadcastTaskStore.Task task = MangoGroupBroadcastTaskStore.getTask(account, taskId);
        if (task == null || task.state == MangoGroupBroadcastTaskStore.COMPLETED
                || task.state == MangoGroupBroadcastTaskStore.CANCELLED) return false;
        MangoGroupBroadcastTaskStore.setState(account, taskId, MangoGroupBroadcastTaskStore.CANCELLED);
        return true;
    }

    private static final class Runner {
        final int account;
        final long taskId;
        final int minSeconds;
        final int maxSeconds;
        int index;
        Runnable pending;

        Runner(int account, long taskId, int minSeconds, int maxSeconds, int index) {
            this.account = account;
            this.taskId = taskId;
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
            this.index = index;
        }

        void stopPending() {
            if (pending != null) AndroidUtilities.cancelRunOnUIThread(pending);
            pending = null;
        }

        void scheduleNext() {
            MangoGroupBroadcastTaskStore.Task task = MangoGroupBroadcastTaskStore.getTask(account, taskId);
            if (task == null || task.state != MangoGroupBroadcastTaskStore.SENDING || index >= task.total) {
                RUNNERS.remove(key(account, taskId));
                return;
            }
            int range = Math.max(1, maxSeconds - minSeconds);
            long delay = (minSeconds + Utilities.random.nextInt(range)) * 1000L;
            pending = () -> {
                pending = null;
                if (RUNNERS.get(key(account, taskId)) != this) return;
                MangoGroupBroadcastTaskStore.Task current = MangoGroupBroadcastTaskStore.getTask(account, taskId);
                if (current == null || current.state != MangoGroupBroadcastTaskStore.SENDING
                        || index >= current.targetIds.size()) {
                    RUNNERS.remove(key(account, taskId));
                    return;
                }
                long dialogId = current.targetIds.get(index);
                try {
                    SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                            current.message, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false));
                    MangoGroupBroadcastTaskStore.markResult(account, taskId, true);
                } catch (Throwable error) {
                    FileLog.e(error);
                    MangoGroupBroadcastTaskStore.markResult(account, taskId, false);
                }
                index++;
                MangoGroupBroadcastTaskStore.Task updated = MangoGroupBroadcastTaskStore.getTask(account, taskId);
                if (updated == null || updated.state == MangoGroupBroadcastTaskStore.COMPLETED
                        || index >= updated.total) {
                    RUNNERS.remove(key(account, taskId));
                } else {
                    scheduleNext();
                }
            };
            AndroidUtilities.runOnUIThread(pending, delay);
        }
    }
}
