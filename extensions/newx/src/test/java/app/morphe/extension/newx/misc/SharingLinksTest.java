package app.morphe.extension.newx.misc;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

// Raw encoding, functional parameters and unrelated hosts must survive link cleanup/routing.
public final class SharingLinksTest {
    @Test public void trackingCleanupPreservesFunctionalParametersAndRawEncoding() {
        assertEquals("https://x.com/user/status/123?lang=zh%2DCN#photo", ShareUrlResolver.removeTrackingParameters(
                "https://x.com/user/status/123?s=20&lang=zh%2DCN&t=token&utm_source=share#photo"));
        assertEquals("https://twitter.com/i/status/123", ShareUrlResolver.removeTrackingParameters("https://twitter.com/i/status/123?%74=abc&s=20"));
        for (String input : new String[]{"https://example.com/user/status/123?t=token", "https://x.com/search?q=a&t=video", "https://x.com/user/status/123?lang=zh", "bad url", null})
            assertEquals(input, ShareUrlResolver.removeTrackingParameters(input));
    }

    @Test public void mirrorRoutingChecksHostAndKeepsEncodedPathQueryAndFragment() {
        var hosts = Collections.singleton("fxtwitter.com");
        assertEquals("https://x.com/u/status/123?lang=zh%2DCN#photo", CustomLinkRouter.rewrite("http://www.fxtwitter.com/u/status/123?lang=zh%2DCN#photo", hosts));
        for (String input : new String[]{"https://fxtwitter.com.evil.test/u/status/1", "https://evilfxtwitter.com/u/status/1", "javascript:alert(1)", "https://x.com/u/status/1", "bad url", null})
            assertNull(CustomLinkRouter.rewrite(input, hosts));
    }
}
