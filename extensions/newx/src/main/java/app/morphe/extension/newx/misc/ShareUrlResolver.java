package app.morphe.extension.newx.misc;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.crimera.settings.SettingsRegistry;

/**
 * Rewrites the host of shared post links to the user-configured domain.
 *
 * The configured value is used as-is: no default extension is appended and no path is stripped.
 * A scheme prefix is tolerated so "https://fxtwitter.com" behaves like "fxtwitter.com". Input the
 * URL parser rejects leaves the link unchanged; a wrong domain is the caller's responsibility.
 */
public final class ShareUrlResolver {
    private static final String CUSTOM_DOMAIN_SETTING = "newx.content.custom_sharing_domain";
    private static final String VALIDATION_PATH = "/i/status/0";

    private ShareUrlResolver() {
    }

    public static boolean isValidCustomDomain(String input) {
        if (input == null) return false;

        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) return true;

        String normalizedDomain = normalizeCustomDomain(trimmedInput);
        if (normalizedDomain.isEmpty()) return false;

        return isValidNormalizedDomain(normalizedDomain);
    }

    public static String changeDomain(String urlString) {
        try {
            if (SettingsRegistry.getBooleanOrDefault("newx.content.clear_tracking_params", true)) {
                urlString = removeTrackingParameters(urlString);
            }
            String customDomain = normalizeCustomDomain(
                    SettingsRegistry.getStringOrDefault(CUSTOM_DOMAIN_SETTING, "")
            );
            if (customDomain.isEmpty()) return urlString;
            if (!isValidNormalizedDomain(customDomain)) return urlString;

            URL url = new URL(urlString);
            String host = url.getHost();
            if (host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com")) {
                return new URL(url.getProtocol(), customDomain, url.getPort(), url.getFile()).toString();
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to rewrite NewX sharing domain", exception);
            return urlString;
        }
        return urlString;
    }

    /** Only X post-sharing URLs are rewritten; raw path, retained query and fragment stay intact. */
    static String removeTrackingParameters(String input) {
        if (input == null) return null;
        try {
            URI uri = new URI(input);
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com") ||
                    host.equalsIgnoreCase("www.x.com") || host.equalsIgnoreCase("www.twitter.com") ||
                    host.equalsIgnoreCase("mobile.x.com") || host.equalsIgnoreCase("mobile.twitter.com"))) return input;
            String path = uri.getPath();
            if (path == null || !path.matches("/(?:[^/]+/status|i/status)/[0-9]+(?:/.*)?")) return input;
            String query = uri.getRawQuery();
            if (query == null) return input;
            List<String> retained = new ArrayList<>();
            boolean changed = false;
            for (String part : query.split("&", -1)) {
                int equals = part.indexOf('=');
                String key = URLDecoder.decode(equals < 0 ? part : part.substring(0, equals), "UTF-8").toLowerCase(Locale.ROOT);
                if (key.equals("s") || key.equals("t") || key.equals("ref_src") || key.equals("ref_url") || key.startsWith("utm_")) {
                    changed = true;
                } else retained.add(part);
            }
            if (!changed) return input;
            int start = input.indexOf('?');
            int fragment = input.indexOf('#', start);
            return input.substring(0, start) + (retained.isEmpty() ? "" : "?" + String.join("&", retained)) +
                    (fragment < 0 ? "" : input.substring(fragment));
        } catch (Exception ignored) {
            return input;
        }
    }

    private static String normalizeCustomDomain(String input) {
        String normalized = input.trim();
        if (normalized.startsWith("https://")) {
            return normalized.substring("https://".length());
        }
        if (normalized.startsWith("http://")) {
            return normalized.substring("http://".length());
        }
        return normalized;
    }

    private static boolean isValidNormalizedDomain(String domain) {
        try {
            URI candidate = new URI("https://" + domain + VALIDATION_PATH);
            return domain.equalsIgnoreCase(candidate.getHost());
        } catch (Exception exception) {
            return false;
        }
    }
}
