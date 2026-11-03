package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

/** Local, account-scoped history for the existing group broadcast queue. */
public final class MangoGroupBroadcastTaskStore {
    public static final int SENDING = 0;
    public static final int COMPLETED = 1;
    public static final int INTERRUPTED = 2;
    public static final int PAUSED = 3;
    public static final int CANCELLED = 4;

    private static final Object LOCK = new Object();
    private static final HashSet<String> activeTasks = new HashSet<>();
    private static final int MAX_HISTORY = 30;

    public static final class Task {
        public final long id;
        public final long createdAt;
        public final String message;
        public final int total;
        public final int processed;
        public final int queued;
        public final int failed;
        public final int state;
        public final ArrayList<String> targetNames;
        public final ArrayList<Long> targetIds;
        public final ArrayList<String> logs;
        public final int minSeconds;
        public final int maxSeconds;

        Task(long id, long createdAt, String message, int total, int processed,
                int queued, int failed, int state, ArrayList<String> targetNames, ArrayList<Long> targetIds, ArrayList<String> logs, int minSeconds, int maxSeconds) {
            this.id = id;
            this.createdAt = createdAt;
            this.message = message;
            this.total = total;
            this.processed = processed;
            this.queued = queued;
            this.failed = failed;
            this.state = state;
            this.targetNames = targetNames;
            this.targetIds = targetIds;
            this.logs = logs;
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
        }
    }

    private MangoGroupBroadcastTaskStore() {
    }

    private static String activeKey(int account, long id) {
        return account + ":" + id;
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "mango_group_broadcast_tasks_" + account, Context.MODE_PRIVATE);
    }

    public static long create(int account, String message, ArrayList<String> targetNames, ArrayList<Long> targetIds, int minSeconds, int maxSeconds) {
        synchronized (LOCK) {
            long id = System.currentTimeMillis();
            ArrayList<Task> tasks = read(account);
            while (containsTask(tasks, id)) id++;
            ArrayList<String> names = targetNames == null ? new ArrayList<>() : new ArrayList<>(targetNames);
            ArrayList<Long> ids = targetIds == null ? new ArrayList<>() : new ArrayList<>(targetIds);
            tasks.add(0, new Task(id, System.currentTimeMillis(), message, ids.size(), 0, 0, 0, SENDING, names, ids, new ArrayList<>(), minSeconds, maxSeconds));
            // Keep active tasks even when the history limit is reached. Only prune
            // the oldest inactive records; otherwise a running task could vanish
            // from storage and its runner would stop on the next tick.
            while (tasks.size() > MAX_HISTORY) {
                int pruneIndex = -1;
                for (int i = tasks.size() - 1; i >= 0; i--) {
                    Task candidate = tasks.get(i);
                    if (!activeTasks.contains(activeKey(account, candidate.id))
                            && candidate.state != SENDING) {
                        pruneIndex = i;
                        break;
                    }
                }
                if (pruneIndex < 0) break;
                tasks.remove(pruneIndex);
            }
            activeTasks.add(activeKey(account, id));
            write(account, tasks);
            return id;
        }
    }

    private static boolean containsTask(ArrayList<Task> tasks, long id) {
        for (Task task : tasks) if (task.id == id) return true;
        return false;
    }

    /** Count only after the existing SendMessagesHelper call has returned without throwing. */
    public static void markResult(int account, long taskId, long dialogId, boolean queued, String error) {
        synchronized (LOCK) {
            ArrayList<Task> tasks = read(account);
            for (int i = 0; i < tasks.size(); i++) {
                Task old = tasks.get(i);
                if (old.id != taskId) continue;
                // A result is valid only while the task is actively sending.
                // Ignore late callbacks after pause, interruption, completion or cancellation:
                // they must not advance progress or append misleading task-log entries.
                if (old.state != SENDING) return;
                int processed = Math.min(old.total, old.processed + 1);
                int queuedCount = old.queued + (queued ? 1 : 0);
                int failedCount = old.failed + (queued ? 0 : 1);
                int state = processed >= old.total ? COMPLETED : old.state;
                ArrayList<String> logs = new ArrayList<>(old.logs);
                String targetName = "群组 " + dialogId;
                int targetIndex = old.targetIds.indexOf(dialogId);
                if (targetIndex >= 0 && targetIndex < old.targetNames.size()) targetName = old.targetNames.get(targetIndex);
                String errorText = error == null ? "未知错误" : error.replace("\n", " ").replace("\r", " ");
                if (errorText.length() > 240) errorText = errorText.substring(0, 240) + "…";
                String entry = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date()) + " · " + targetName + " · "
                        + (queued ? "已提交发送队列" : "调用异常：" + errorText);
                logs.add(0, entry);
                while (logs.size() > 100) logs.remove(logs.size() - 1);
                tasks.set(i, new Task(old.id, old.createdAt, old.message, old.total,
                        processed, queuedCount, failedCount, state, old.targetNames, old.targetIds, logs, old.minSeconds, old.maxSeconds));
                if (state == COMPLETED) activeTasks.remove(activeKey(account, taskId));
                write(account, tasks);
                return;
            }
        }
    }

    /** Replace the temporary queued status with the server-acknowledged success status. */
    public static void markSendSuccess(int account, long dialogId) {
        synchronized (LOCK) {
            ArrayList<Task> tasks = read(account);
            for (int i = 0; i < tasks.size(); i++) {
                Task old = tasks.get(i);
                int targetIndex = old.targetIds.indexOf(dialogId);
                if (targetIndex < 0) continue;
                String targetName = targetIndex < old.targetNames.size()
                        ? old.targetNames.get(targetIndex) : "群组 " + dialogId;
                String marker = " · " + targetName + " · 已提交发送队列";
                ArrayList<String> logs = new ArrayList<>(old.logs);
                for (int j = 0; j < logs.size(); j++) {
                    if (logs.get(j).contains(marker)) {
                        logs.set(j, logs.get(j).replace(" · 已提交发送队列", " · 发送成功"));
                        tasks.set(i, new Task(old.id, old.createdAt, old.message, old.total,
                                old.processed, old.queued, old.failed, old.state, old.targetNames,
                                old.targetIds, logs, old.minSeconds, old.maxSeconds));
                        write(account, tasks);
                        return;
                    }
                }
            }
        }
    }

    /** Convert an asynchronously rejected send from queued to failed for its target group. */
    public static void markSendFailure(int account, long dialogId, String error) {
        synchronized (LOCK) {
            ArrayList<Task> tasks = read(account);
            for (int i = 0; i < tasks.size(); i++) {
                Task old = tasks.get(i);
                int targetIndex = old.targetIds.indexOf(dialogId);
                if (targetIndex < 0) continue;
                String targetName = targetIndex < old.targetNames.size()
                        ? old.targetNames.get(targetIndex) : "群组 " + dialogId;
                String queuedMarker = " · " + targetName + " · 已提交发送队列";
                int queuedLogIndex = -1;
                for (int j = 0; j < old.logs.size(); j++) {
                    if (old.logs.get(j).contains(queuedMarker)) {
                        queuedLogIndex = j;
                        break;
                    }
                }
                if (queuedLogIndex < 0) continue;
                ArrayList<String> logs = new ArrayList<>(old.logs);
                String safeError = error == null ? "发送失败" : error.replace("\\n", " ").replace("\\r", " ");
                if (safeError.length() > 180) safeError = safeError.substring(0, 180) + "…";
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                logs.set(queuedLogIndex, time + " · " + targetName + " · 发送失败：" + safeError);
                int queued = Math.max(0, old.queued - 1);
                int failed = old.failed + 1;
                tasks.set(i, new Task(old.id, old.createdAt, old.message, old.total,
                        old.processed, queued, failed, old.state, old.targetNames, old.targetIds,
                        logs, old.minSeconds, old.maxSeconds));
                write(account, tasks);
                return;
            }
        }
    }

    public static void setState(int account, long taskId, int state) {
        synchronized (LOCK) {
            ArrayList<Task> tasks = read(account);
            for (int i = 0; i < tasks.size(); i++) {
                Task old = tasks.get(i);
                if (old.id != taskId) continue;
                // Terminal tasks must never be resurrected by a stale pause/resume
                // action arriving after completion or cancellation.
                if (old.state == COMPLETED || old.state == CANCELLED) return;
                if (state != SENDING && state != PAUSED && state != INTERRUPTED
                        && state != CANCELLED) return;
                // A task cannot be marked as running after every target has already
                // been processed, even if an old UI action is still in flight.
                if (state == SENDING && old.processed >= old.total) return;
                tasks.set(i, new Task(old.id, old.createdAt, old.message, old.total, old.processed,
                        old.queued, old.failed, state, old.targetNames, old.targetIds, old.logs, old.minSeconds, old.maxSeconds));
                if (state == SENDING) activeTasks.add(activeKey(account, taskId));
                else activeTasks.remove(activeKey(account, taskId));
                write(account, tasks);
                return;
            }
        }
    }

    public static Task getTask(int account, long taskId) {
        synchronized (LOCK) {
            for (Task task : read(account)) if (task.id == taskId) return task;
            return null;
        }
    }

    public static ArrayList<Task> getTasks(int account) {
        synchronized (LOCK) {
            ArrayList<Task> tasks = read(account);
            boolean changed = false;
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                if (task.state == SENDING && !activeTasks.contains(activeKey(account, task.id))) {
                    tasks.set(i, new Task(task.id, task.createdAt, task.message, task.total,
                            task.processed, task.queued, task.failed, INTERRUPTED, task.targetNames, task.targetIds, task.logs, task.minSeconds, task.maxSeconds));
                    changed = true;
                }
            }
            if (changed) write(account, tasks);
            return tasks;
        }
    }

    private static ArrayList<String> readLogs(JSONArray array) {
        ArrayList<String> logs = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length() && i < 100; i++) logs.add(array.optString(i, ""));
        return logs;
    }

    private static ArrayList<Task> read(int account) {
        ArrayList<Task> tasks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences(account).getString("tasks", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                ArrayList<String> names = new ArrayList<>();
                ArrayList<Long> ids = new ArrayList<>();
                JSONArray nameArray = item.optJSONArray("targetNames");
                if (nameArray != null) {
                    for (int j = 0; j < nameArray.length(); j++) names.add(nameArray.optString(j, ""));
                }
                JSONArray idArray = item.optJSONArray("targetIds");
                if (idArray != null) for (int j = 0; j < idArray.length(); j++) ids.add(idArray.optLong(j));
                tasks.add(new Task(item.optLong("id"), item.optLong("createdAt"),
                        item.optString("message", ""), item.optInt("total"),
                        item.optInt("processed"), item.optInt("queued"), item.optInt("failed"),
                        item.optInt("state"), names, ids, readLogs(item.optJSONArray("logs")), item.optInt("minSeconds", 5), item.optInt("maxSeconds", 15)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return tasks;
    }

    private static void write(int account, ArrayList<Task> tasks) {
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", task.id);
                item.put("createdAt", task.createdAt);
                item.put("message", task.message);
                item.put("total", task.total);
                item.put("processed", task.processed);
                item.put("queued", task.queued);
                item.put("failed", task.failed);
                item.put("state", task.state);
                JSONArray names = new JSONArray();
                for (String name : task.targetNames) names.put(name);
                item.put("targetNames", names);
                JSONArray ids = new JSONArray();
                for (Long id : task.targetIds) ids.put(id);
                item.put("targetIds", ids);
                JSONArray logs = new JSONArray();
                for (String log : task.logs) logs.put(log);
                item.put("logs", logs);
                item.put("minSeconds", task.minSeconds);
                item.put("maxSeconds", task.maxSeconds);
                array.put(item);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        preferences(account).edit().putString("tasks", array.toString()).apply();
    }
}
