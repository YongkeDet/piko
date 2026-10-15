/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.interaction.downloads.changedirectory

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val DOWNLOAD_FOLDER = "Lapp/morphe/extension/twitter/downloads/DownloadFolder;"

internal object DownloadPathFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            string("parse(...)"),
            opcode(Opcode.MOVE_RESULT_OBJECT),
        ),
    strings = listOf("guessFileName(...)", "setNotificationVisibility(...)"),
)

@Suppress("unused")
val changeDownloadDirPatch =
    bytecodePatch(
        name = "Custom download folder",
        description = "Choose a custom download folder using the system folder picker",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val GETFOLDER_DESCRIPTOR =
                "$PREF_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"

            val PUBLICFOLDER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->getPublicFolder()Ljava/lang/String;"

            DownloadPathFingerprint.method.apply {
                val moveResObjIndex = DownloadPathFingerprint.instructionMatches[1].index
                val fileNameReg = getInstruction<OneRegisterInstruction>(moveResObjIndex).registerA

                val insertAt =
                    instructions
                        .first { it.opcode == Opcode.INVOKE_VIRTUAL }
                        .location.index

                val publicFolderRegister = getInstruction<OneRegisterInstruction>(insertAt - 1).registerA
                addInstructions(
                    insertAt,
                    """
                    $PUBLICFOLDER_DESCRIPTOR
                    move-result-object v$publicFolderRegister
                    
                    invoke-static {v$fileNameReg}, $GETFOLDER_DESCRIPTOR
                    move-result-object v$fileNameReg
                    """.trimIndent(),
                )

                // Keep X's request headers and download ID while redirecting its destination.
                listOf(
                    Triple(
                        "Landroid/app/DownloadManager\$Request;",
                        "setDestinationInExternalPublicDir",
                        "configureDestination(Landroid/app/DownloadManager\$Request;Ljava/lang/String;Ljava/lang/String;)Landroid/app/DownloadManager\$Request;",
                    ),
                    Triple(
                        "Landroid/app/DownloadManager;",
                        "enqueue",
                        "enqueue(Landroid/app/DownloadManager;Landroid/app/DownloadManager\$Request;)J",
                    ),
                ).forEach { (owner, name, hook) ->
                    val matches = instructions.withIndex().filter { (_, instruction) ->
                        val reference = instruction.getReference<MethodReference>()
                        instruction.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                            reference?.definingClass == owner && reference.name == name
                    }
                    val match = matches.singleOrNull()
                        ?: throw PatchException("Could not uniquely resolve download call: $name")
                    val registers = match.value.registersUsed
                    val invocation = if (match.value.opcode == Opcode.INVOKE_VIRTUAL_RANGE) {
                        "invoke-static/range {v${registers.first()} .. v${registers.last()}}"
                    } else {
                        "invoke-static {${registers.joinToString(", ") { "v$it" }}}"
                    }
                    replaceInstruction(match.index, "$invocation, $DOWNLOAD_FOLDER->$hook")
                }
                enableSettings("enableDownloadFolder")
            }
        }
    }
