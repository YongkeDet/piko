/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * Copyright (C) 2026 YongkeDet
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.crimera.patches.twitter.interaction.downloads.modern

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.SupportedAbi
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private const val HOOKS = "Lapp/morphe/extension/modern/DownloadFolderHooks;"
private const val ACTIVITY = "app.morphe.extension.modern.DownloadFolderActivity"
private const val RESOLVER = "Landroid/content/ContentResolver;"

private object ModernApplicationFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/XApplication;",
    name = "onCreate",
    parameters = emptyList(),
    returnType = "V",
)

private object PublicMediaSaveFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("Failed to create MediaStore entry for "),
    custom = { _, classDef -> classDef.type.startsWith("Lcom/x/android/utils/") },
)

private object DirectMessageSaveFingerprint : Fingerprint(
    name = "invokeSuspend",
    strings = listOf("MediaStore refused to remove pending gallery entry "),
    custom = { _, classDef -> classDef.type.startsWith("Lcom/x/dm/attachmentviewer/") },
)

private object ExportedVideoSaveFingerprint : Fingerprint(
    name = "invokeSuspend",
    filters = listOf(methodCall(definingClass = RESOLVER, name = "insert")),
    custom = { _, classDef -> classDef.type.startsWith("Lcom/x/media/transcode/") },
)

private val modernFolderResources = resourcePatch {
    dependsOn(addResourcesPatch)
    execute {
        addAppResources("modern")
        document("AndroidManifest.xml").use { editor ->
            val application = editor.getElementsByTagName("application").item(0) as Element
            application.appendChild(editor.createElement("activity").apply {
                setAttribute("android:name", ACTIVITY)
                setAttribute("android:exported", "true")
                setAttribute("android:label", "@string/piko_modern_download_folder")
                setAttribute("android:theme", "@android:style/Theme.DeviceDefault")
            })
        }
        document("res/xml/shortcuts.xml").use { editor ->
            val shortcut = editor.createElement("shortcut").apply {
                setAttribute("android:shortcutId", "piko_download_folder")
                setAttribute("android:enabled", "true")
                setAttribute("android:icon", "@android:drawable/ic_menu_save")
                setAttribute("android:shortcutShortLabel", "@string/piko_modern_download_folder")
                setAttribute("android:shortcutLongLabel", "@string/piko_modern_download_folder")
                appendChild(editor.createElement("intent").apply {
                    setAttribute("android:action", "android.intent.action.VIEW")
                    setAttribute("android:targetPackage", "com.twitter.android")
                    setAttribute("android:targetClass", ACTIVITY)
                })
            }
            editor.documentElement.insertBefore(shortcut, editor.documentElement.firstChild)
        }
    }
}

/** The rewritten X app has no legacy Piko settings screen or legacy media model classes. */
@Suppress("unused")
val modernDownloadFolderPatch = bytecodePatch(
    name = "Custom download folder (modern X)",
    description = "Choose where X saves photos and videos. Long-press the X launcher icon and choose Download folder.",
) {
    compatibleWith(Compatibility(
        name = "Twitter",
        packageName = "com.twitter.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000,
        targets = listOf(AppTarget(
            version = "12.34.0-prod.01",
            versionCodes = mapOf(SupportedAbi.ARM64_V8A to 312340001),
            minSdk = 29,
        )),
    ))
    extendWith("extensions/modern.mpe")
    dependsOn(modernFolderResources)

    execute {
        classDefBy(HOOKS)
        ModernApplicationFingerprint.method.addInstruction(
            0, "invoke-static/range {p0 .. p0}, $HOOKS->initialize(Landroid/content/Context;)V",
        )
        val hooks = mapOf(
            "insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;" to
                "insert(Landroid/content/ContentResolver;Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;",
            "update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I" to
                "update(Landroid/content/ContentResolver;Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I",
            "delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I" to
                "delete(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I",
        )
        listOf(PublicMediaSaveFingerprint, DirectMessageSaveFingerprint, ExportedVideoSaveFingerprint).forEach { fingerprint ->
            fingerprint.method.apply {
                hooks.forEach { (original, hook) ->
                    val matches = instructions.withIndex().filter { (_, instruction) ->
                        instruction.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                            instruction.getReference<MethodReference>()?.toString() == "$RESOLVER->$original"
                    }
                    val match = matches.singleOrNull()
                        ?: throw PatchException("Expected one $original in ${fingerprint.javaClass.simpleName}; found ${matches.size}")
                    val registers = match.value.registersUsed
                    val invocation = if (match.value.opcode == Opcode.INVOKE_VIRTUAL_RANGE) {
                        "invoke-static/range {v${registers.first()} .. v${registers.last()}}"
                    } else {
                        "invoke-static {${registers.joinToString(", ") { "v$it" }}}"
                    }
                    replaceInstruction(match.index, "$invocation, $HOOKS->$hook")
                }
            }
        }
    }
}
