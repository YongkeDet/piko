package app.morphe.extension.newx.misc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Active transfers only. UI and byte events share snapshots without retaining completed files. */
final class DownloadProgressState {
    private final Map<Integer, Transfer> active = new LinkedHashMap<>();

    synchronized void queued(int id, String name, boolean batch, long now) {
        active.put(id, new Transfer(name, batch, now));
    }

    synchronized void waiting(int id, boolean waiting) {
        Transfer transfer = active.get(id);
        if (transfer != null) transfer.waiting = waiting;
    }

    synchronized void progress(int id, long done, long total, long now) {
        Transfer transfer = active.get(id);
        if (transfer == null) return; // A late event must not resurrect a finished transfer.
        done = Math.max(0, done);
        if (done < transfer.done) { // Retry or a server that restarted instead of resuming.
            transfer.sampleBytes = done;
            transfer.sampleAt = now;
            transfer.speed = 0;
        }
        if (now - transfer.sampleAt >= 250) {
            transfer.speed = (long) ((done - transfer.sampleBytes) * 1000.0 / (now - transfer.sampleAt));
            transfer.sampleAt = now;
            transfer.sampleBytes = done;
        }
        transfer.done = done;
        transfer.total = total > 0 ? Math.max(total, done) : -1;
        transfer.updatedAt = now;
        transfer.waiting = false;
    }

    synchronized void finished(int id) { active.remove(id); }

    synchronized Snapshot snapshot(boolean batch, long now) {
        int count = 0, waiting = 0;
        long done = 0, total = 0, speed = 0;
        boolean known = true;
        String name = "";
        for (Transfer transfer : active.values()) {
            if (transfer.batch != batch) continue;
            count++;
            if (name.isEmpty()) name = transfer.name;
            if (transfer.waiting) waiting++;
            done = sum(done, transfer.done);
            if (transfer.total <= 0) known = false;
            else total = sum(total, transfer.total);
            if (!transfer.waiting && now - transfer.updatedAt <= 3000) speed = sum(speed, transfer.speed);
        }
        int percent = known && total > 0 ? Math.min(99, (int) (done * 100.0 / total)) : -1;
        return new Snapshot(count, waiting, done, known ? total : -1, speed, percent, name);
    }

    synchronized List<TaskSnapshot> tasks(long now) {
        List<TaskSnapshot> result = new ArrayList<>();
        for (Map.Entry<Integer, Transfer> entry : active.entrySet()) {
            Transfer t = entry.getValue();
            if (t.batch) continue;
            int percent = t.total > 0 ? Math.min(99, (int) (t.done * 100.0 / t.total)) : -1;
            result.add(new TaskSnapshot(entry.getKey(), new Snapshot(1, t.waiting ? 1 : 0,
                    t.done, t.total, !t.waiting && now - t.updatedAt <= 3000 ? t.speed : 0, percent, t.name)));
        }
        return result;
    }

    static final class TaskSnapshot {
        final int id;
        final Snapshot snapshot;
        TaskSnapshot(int id, Snapshot snapshot) { this.id = id; this.snapshot = snapshot; }
    }

    private static long sum(long a, long b) { return b > Long.MAX_VALUE - a ? Long.MAX_VALUE : a + b; }

    static final class Snapshot {
        final int count, waiting, percent;
        final long done, total, speed;
        final String name;
        Snapshot(int count, int waiting, long done, long total, long speed, int percent, String name) {
            this.count = count; this.waiting = waiting; this.done = done; this.total = total;
            this.speed = speed; this.percent = percent; this.name = name;
        }
    }

    private static final class Transfer {
        final String name;
        final boolean batch;
        long done, total = -1, speed, sampleBytes, sampleAt, updatedAt;
        boolean waiting;
        Transfer(String name, boolean batch, long now) {
            this.name = name == null ? "" : name; this.batch = batch;
            this.sampleAt = now; this.updatedAt = now;
        }
    }
}
