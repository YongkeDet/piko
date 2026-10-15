package app.crimera.patches.newx.misc.customlinks

import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXAction
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringsOption
import org.w3c.dom.Element
import java.util.Locale

private fun Element.children(tag: String): List<Element> = (0 until childNodes.length)
    .mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

@Suppress("unused")
val newXCustomLinksPatch = resourcePatch(
    name = "NewX: Handle custom links",
    description = "Opens registered mirror-domain links in X. Enable the supported links in Android app settings.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXExtensionPatch)
    val customLinkHosts by stringsOption(
        key = "customLinkHosts", title = "自定义链接域名",
        description = "这些域名的链接可在系统的默认打开方式中交给 X 处理。",
        default = listOf("vxtwitter.com", "fixvx.com", "fxtwitter.com", "fixupx.com", "twittpr.com", "xcancel.com"),
        required = true,
    )
    execute {
        val hosts = customLinkHosts.orEmpty().map { it.trim().lowercase(Locale.ROOT) }.distinct()
        val domain = Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9-]{2,63}")
        if (hosts.isEmpty() || hosts.any { !domain.matches(it) || it == "x.com" || it == "twitter.com" ||
                    it.endsWith(".x.com") || it.endsWith(".twitter.com") }) {
            throw PatchException("Custom link hosts must be valid mirror domain names")
        }
        var nativeTarget = ""
        document("AndroidManifest.xml").use { doc ->
            val application = requireExactlyOne("NewX manifest application", doc.documentElement.children("application"))
            val candidates = application.children("activity").filter { activity ->
                activity.getAttribute("android:exported") == "true" && activity.children("intent-filter").any { filter ->
                    filter.children("action").any { it.getAttribute("android:name") == "android.intent.action.VIEW" } &&
                        filter.children("category").any { it.getAttribute("android:name") == "android.intent.category.BROWSABLE" } &&
                        filter.children("data").any { it.getAttribute("android:host") == "x.com" }
                }
            }
            nativeTarget = requireExactlyOne("native X web-link activity", candidates).getAttribute("android:name")
            if (nativeTarget.startsWith(".")) nativeTarget = doc.documentElement.getAttribute("package") + nativeTarget
            val bridge = doc.createElement("activity").apply {
                setAttribute("android:name", "app.morphe.extension.newx.misc.CustomLinkActivity")
                setAttribute("android:exported", "true")
                setAttribute("android:excludeFromRecents", "true")
                setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
            }
            val filter = doc.createElement("intent-filter")
            fun add(tag: String, key: String, value: String) = filter.appendChild(doc.createElement(tag).apply { setAttribute(key, value) })
            add("action", "android:name", "android.intent.action.VIEW")
            add("category", "android:name", "android.intent.category.DEFAULT")
            add("category", "android:name", "android.intent.category.BROWSABLE")
            add("data", "android:scheme", "https")
            add("data", "android:scheme", "http")
            hosts.forEach { host -> add("data", "android:host", host); add("data", "android:host", "*.$host") }
            bridge.appendChild(filter)
            application.appendChild(bridge)
        }
        document("res/values/strings.xml").use { doc ->
            doc.documentElement.appendChild(doc.createElement("string").apply {
                setAttribute("name", "piko_newx_custom_link_target")
                setAttribute("translatable", "false")
                textContent = nativeTarget
            })
        }
        document("res/values/arrays.xml").use { doc ->
            doc.documentElement.appendChild(doc.createElement("string-array").apply {
                setAttribute("name", "piko_newx_custom_link_hosts")
                hosts.forEach { host -> appendChild(doc.createElement("item").apply { textContent = host }) }
            })
        }
    }
}

@Suppress("unused")
val newXChangelogPatch = bytecodePatch(
    name = "NewX: Show changelog",
    description = "Adds a link to this fork's release notes in Piko settings.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXExtensionPatch)
    newXAction(id = "newx.advanced.changelog", category = Categories.ADVANCED,
        strings = settingStrings("piko_newx_changelog"), order = 900,
        handlerClassDescriptor = "Lapp/morphe/extension/newx/settings/ChangelogAction;")
}
