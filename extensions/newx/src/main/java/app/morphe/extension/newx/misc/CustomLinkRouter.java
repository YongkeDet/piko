package app.morphe.extension.newx.misc;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Converts only explicitly registered mirror hosts to a first-party X URL. */
public final class CustomLinkRouter {
    private CustomLinkRouter() {}

    public static String rewrite(String input, Set<String> hosts) {
        if (input == null || hosts == null) return null;
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
            host = host.toLowerCase(Locale.ROOT);
            boolean allowed = false;
            for (String candidate : hosts) {
                if (host.equals(candidate) || host.endsWith("." + candidate)) { allowed = true; break; }
            }
            if (!allowed) return null;
            String path = uri.getRawPath();
            return "https://x.com" + (path == null || path.isEmpty() ? "/" : path) +
                    (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()) +
                    (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (Exception ignored) {
            return null;
        }
    }
}
