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

        Task(long id, long createdAt, String message, int total, int processed,
                int queued, int failed, int state, ArrayList<String> targetNames, ArrayList<Long> targetIds) {
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

    public static long create(int account, String message, ArrayList<String> targetNames, ArrayList<Long> targetIds) {
        synchronized (LOCK) {
            long id = System.currentTimeMillis();
            ArrayList<Task> tasks = read(account);
            while (containsTask(tasks, id)) id++;
            ArrayList<String> names = targetNames == null ? new ArrayList<>() : new ArrayList<>(targetNames);
            ArrayList<Long> ids = targetIds == null ? new ArrayList<>() : new ArrayList<>(targetIds);
            tasks.add(0, new Task(id, System.currentTimeMillis(), message, names.size(), 0, 0, 0, SENDING, names, ids));
            while (tasks.size() > MAX_HISTORY) tasks.remove(tasks.size() - 1);
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
    public static void markResult(int account, long taskId, boolean queued) {
        synchronized (LOCK) {
            ArrayList<Task> tasks = read(account);
            for (int i = 0; i < tasks.size(); i++) {
                Task old = tasks.get(i);
                if (old.id != taskId) continue;
                int processed = Math.min(old.total, old.processed + 1);
                int queuedCount = old.queued + (queued ? 1 : 0);
                int failedCount = old.failed + (queued ? 0 : 1);
                int state = processed >= old.total ? COMPLETED : SENDING;
                tasks.set(i, new Task(old.id, old.createdAt, old.message, old.total,
                        processed, queuedCount, failedCount, state, old.targetNames, old.targetIds));
                if (state == COMPLETED) activeTasks.remove(activeKey(account, taskId));
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
                tasks.set(i, new Task(old.id, old.createdAt, old.message, old.total, old.processed,
                        old.queued, old.failed, state, old.targetNames, old.targetIds));
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
                            task.processed, task.queued, task.failed, INTERRUPTED, task.targetNames, task.targetIds));
                    changed = true;
                }
            }
            if (changed) write(account, tasks);
            return tasks;
        }
    }

    private static ArrayList<Task> read(int account) {
        ArrayList<Task> tasks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences(account).getString("tasks", "[]"));
            for (int i = 0; i < array.length() && i < MAX_HISTORY; i++) {
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
                        item.optInt("state"), names, ids));
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
                array.put(item);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        preferences(account).edit().putString("tasks", array.toString()).apply();
    }
}
