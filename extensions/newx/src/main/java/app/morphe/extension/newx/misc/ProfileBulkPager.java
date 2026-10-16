package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.utils.ToStringParser;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Consumes native pages, including Empty; cached failures are not results of our next request. */
final class ProfileBulkPager {
    enum Step { WAIT, PROCESS, NEXT, RETRY, REFRESH, DONE, FAILED }
    enum Failure { NONE, NETWORK, TIMEOUT, CURSOR_LOOP, ACCESS, RATE_LIMIT }
    enum Kind { NONE, FETCHING, SUCCESS, FAILURE }
    static final class Network {
        static final Network NONE = new Network(null, Kind.NONE, 0);
        final Object token;
        final Kind kind;
        final int status;
        Network(Object token, Kind kind, int status) {
            this.token = token;
            this.kind = kind;
            this.status = status;
        }
        static Network read(Object event) {
            if (event == null) return NONE;
            String value = event.toString();
            Kind kind = value.startsWith("Failure(requestType=") ? Kind.FAILURE
                    : value.startsWith("Success(requestType=") ? Kind.SUCCESS
                    : value.startsWith("Fetching(requestType=") ? Kind.FETCHING : Kind.NONE;
            int status = 0;
            if (kind == Kind.FAILURE) {
                String code = ToStringParser.fieldValue(value, "httpStatus");
                if (code != null) try { status = Integer.parseInt(code); }
                catch (NumberFormatException ignored) { }
            }
            return new Network(event, kind, status);
        }
    }

    private static final long TIMEOUT_MS = 45_000;
    private static final long BUSY_TIMEOUT_MS = 120_000;
    private static final long BOOTSTRAP_MS = 10_000;
    private static final long SETTLE_MS = 1_000;
    private static final int MAX_ATTEMPTS = 3;
    private final Set<String> requested = new HashSet<>();
    private Object processedItems;
    private String processedCursor;
    private boolean processed;
    private boolean awaiting;
    private boolean refreshing;
    private String requestCursor;
    private Object requestEvent;
    private int attempts;
    private long lastProgress;
    private long retryAt = -1;
    private long stableSince = -1;
    private String stableCursor;
    private Object stableItems;
    private boolean stableTerminated;
    private Failure failure = Failure.NONE;
    private int failureStatus;
    private boolean observed;
    private Object initialEvent;

    ProfileBulkPager(long now) { lastProgress = now; }
    Failure failure() { return failure; }
    int failureStatus() { return failureStatus; }
    int attempts() { return attempts; }

    Step step(long now, Object items, boolean canPage, boolean terminated, boolean loading,
              String cursor, Network network, boolean backingOff) {
        long elapsed = now - lastProgress;
        if (!observed) {
            observed = true;
            initialEvent = network.token;
        }
        boolean failed = network.kind == Kind.FAILURE;
        boolean freshFailure = failed && network.token != initialEvent;
        if (freshFailure && (network.status == 401 || network.status == 403 || network.status == 404)) {
            return fail(Failure.ACCESS, network.status);
        }
        if (freshFailure && network.status == 429) return fail(Failure.RATE_LIMIT, 429);
        if (loading || network.kind == Kind.FETCHING || backingOff) {
            stableSince = -1;
            return elapsed >= BUSY_TIMEOUT_MS ? fail(Failure.TIMEOUT, 0) : Step.WAIT;
        }
        // A successful NO_CURSOR is an end marker; an initial/cached blank state is not.
        terminated = terminated || network.kind == Kind.SUCCESS && cursor == null && !canPage;
        if (awaiting) {
            boolean freshEvent = network.token != requestEvent;
            if (failed && freshEvent) return recover(now, cursor, Failure.NETWORK, network.status);
            boolean advanced = !failed && (!Objects.equals(cursor, requestCursor) || terminated);
            if (refreshing) advanced = freshEvent && network.kind == Kind.SUCCESS;
            if (!advanced) {
                // A repeated successful cursor is not proof of completion. Do consume a late
                // new list, then fail closed if its next cursor is still one already requested.
                if (freshEvent && network.kind == Kind.SUCCESS && items != null && items != processedItems) {
                    advanced = true;
                } else if (elapsed >= TIMEOUT_MS) {
                    return network.kind == Kind.SUCCESS && freshEvent
                            ? fail(Failure.CURSOR_LOOP, 0) : recover(now, cursor, Failure.TIMEOUT, 0);
                } else return Step.WAIT;
            }
        }
        if (items == null) {
            stableSince = -1;
            return failed ? recover(now, cursor, Failure.NETWORK, network.status)
                    : elapsed >= BOOTSTRAP_MS ? recover(now, cursor, Failure.TIMEOUT, 0) : Step.WAIT;
        }
        if (stableSince < 0 || !Objects.equals(stableCursor, cursor) ||
                stableItems != items || stableTerminated != terminated) {
            stableSince = now;
            stableCursor = cursor;
            stableItems = items;
            stableTerminated = terminated;
            return elapsed >= BUSY_TIMEOUT_MS ? fail(Failure.TIMEOUT, 0) : Step.WAIT;
        }
        if (now - stableSince < SETTLE_MS) return Step.WAIT;
        if (awaiting) {
            awaiting = false;
            refreshing = false;
            attempts = 0;
            retryAt = -1;
            lastProgress = now;
        }
        if (!processed || processedItems != items || !Objects.equals(processedCursor, cursor)) {
            return Step.PROCESS;
        }
        // A cached failure can coexist with usable cached media/cursor. Save those media first,
        // then retry through the native dispatcher even though it currently says !canPage.
        if (failed) return recover(now, cursor, Failure.NETWORK, network.status);
        if (terminated) return Step.DONE;
        if (!canPage || cursor == null) {
            return elapsed >= BOOTSTRAP_MS ? recover(now, cursor, Failure.TIMEOUT, 0) : Step.WAIT;
        }
        return requested.contains(cursor) ? fail(Failure.CURSOR_LOOP, 0) : Step.NEXT;
    }

    private Step recover(long now, String cursor, Failure reason, int status) {
        if (attempts >= MAX_ATTEMPTS) {
            Failure result = status == 429 ? Failure.RATE_LIMIT
                    : status == 401 || status == 403 || status == 404 ? Failure.ACCESS : reason;
            return fail(result, status);
        }
        if (retryAt < 0) retryAt = now + (attempts + 1L) * 1_500;
        if (now < retryAt) return Step.WAIT;
        return cursor == null ? Step.REFRESH : Step.RETRY;
    }

    private Step fail(Failure reason, int status) {
        failure = reason;
        failureStatus = status;
        return Step.FAILED;
    }

    void processed(Object items, String cursor, long now) {
        processed = true;
        processedItems = items;
        processedCursor = cursor;
        lastProgress = now;
    }

    void requested(String cursor, long now, Network network) {
        attempts = awaiting && !refreshing && Objects.equals(cursor, requestCursor) ? attempts + 1 : 1;
        requested.add(cursor);
        awaiting = true;
        refreshing = false;
        requestCursor = cursor;
        requestEvent = network.token;
        lastProgress = now;
        retryAt = -1;
        stableSince = -1;
    }

    void refreshed(long now, String cursor, Network network) {
        attempts = awaiting && refreshing ? attempts + 1 : 1;
        // Native refresh starts from the top. Session URL deduplication keeps files unique.
        requested.clear();
        processed = false;
        awaiting = true;
        refreshing = true;
        requestCursor = cursor;
        requestEvent = network.token;
        lastProgress = now;
        retryAt = -1;
        stableSince = -1;
    }
}
