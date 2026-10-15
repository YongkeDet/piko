package app.morphe.extension.newx.misc;

/** Patch-time bridge: the concrete Compose/insets contracts are resolved from the target APK. */
public final class ReplyBarInsets {
    private ReplyBarInsets() {}

    public static Object apply(Object modifier, Object composer) {
        return modifier;
    }
}
