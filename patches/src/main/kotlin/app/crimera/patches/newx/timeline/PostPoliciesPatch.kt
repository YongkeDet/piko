package app.crimera.patches.newx.timeline

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private data class PostFieldPolicy(val label: String, val type: String?, val replacement: Int)

/**
 * Both the normal and serialization constructors assign the final model fields. Apply the policy
 * at their normal exits, after all mask/default branches have converged. This leaves constructor
 * arguments, shared scratch values and unrelated fields intact. A setting disabled at startup
 * therefore preserves exactly the model X constructed.
 */
context(context: BytecodePatchContext)
private fun applyPostPolicy(settingId: String, policies: List<PostFieldPolicy>) {
    val models = resolvedNewXPostModels()
    val owner = context.mutableClassDefBy(models.canonicalPostDescriptor)
    val toStringMethod = requireExactlyOne(
        "canonical-post policy labels",
        owner.methods.filter { it.name == "toString" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;" },
    )
    val replacements = policies.map { policy ->
        val field = toStringMethod.fieldForToStringLabel(policy.label)
        if (policy.type?.let { field.type != it } ?: !field.type.startsWith("L")) {
            throw PatchException("Unsupported NewX post policy field for ${policy.label}: $field")
        }
        field to policy.replacement
    }
    val fieldKeys = replacements.map { it.first.toString() }.toSet()
    val constructors = owner.methods.filter { method ->
        method.name == "<init>" && method.instructions.any { instruction ->
            instruction.opcode in setOf(Opcode.IPUT_OBJECT, Opcode.IPUT_BOOLEAN) &&
                instruction.getReference<FieldReference>()?.toString() in fieldKeys
        }
    }
    if (constructors.size != 2) {
        throw PatchException("Expected the normal and serialization post constructors; found ${constructors.size}: $constructors")
    }
    constructors.forEach { method ->
        val writes = method.instructions.mapNotNull { instruction ->
            instruction.getReference<FieldReference>()?.toString()?.takeIf {
                instruction.opcode in setOf(Opcode.IPUT_OBJECT, Opcode.IPUT_BOOLEAN) && it in fieldKeys
            }
        }.toSet()
        if (writes != fieldKeys) throw PatchException("Incomplete NewX post policy constructor: $method ($writes)")
        injectPostPolicyExit(method, settingId, replacements)
    }
}

internal fun injectPostPolicyExit(
    method: MutableMethod,
    settingId: String,
    replacements: List<Pair<FieldReference, Int>>,
) {
    val exit = requireExactlyOne(
        "normal NewX post constructor exit in $method",
        method.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index },
    )
    method.insertHook(exit, excludedRegisters = listOf(method.p0Register), relocateBranchTargets = true) {
        val receiver = scratchRegister(RegisterLimit.FOUR_BIT)
        val value = scratchRegister(RegisterLimit.FOUR_BIT)
        constString(value, settingId)
        invokeStatic(methodReference("$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;)Z"), value)
        moveResult(value, "Z")
        ifEqz(value, Target.Original)
        move(receiver, method.p0Register, method.definingClass)
        replacements.forEach { (field, replacement) ->
            constInt(value, replacement)
            iput(value, receiver, field)
        }
    }
}

@Suppress("unused")
val newXHideCommunityNotesPatch = bytecodePatch(
    name = "NewX: Hide Community Notes",
    description = "Hides community notes on NewX posts, including loaded tombstone notes.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXPostModelResolutionPatch)
    val setting = newXToggle(
        id = "newx.content.hide_community_notes",
        category = Categories.CONTENT,
        strings = settingStrings("piko_newx_hide_community_notes"),
        order = 360,
        defaultValue = false,
        rebootApp = true,
    )
    execute {
        applyPostPolicy(setting.id, listOf(
            PostFieldPolicy(", communityNote=", null, 0),
            PostFieldPolicy(", tombstoneCommunityNote=", null, 0),
            PostFieldPolicy(", loadedTombstoneCommunityNote=", "Z", 1),
        ))
    }
}

@Suppress("unused")
val newXForceTranslatePatch = bytecodePatch(
    name = "NewX: Force translate option",
    description = "Makes the native translation option available on NewX posts.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXPostModelResolutionPatch)
    val setting = newXToggle(
        id = "newx.content.force_translate",
        category = Categories.CONTENT,
        strings = settingStrings("piko_newx_force_translate"),
        order = 370,
        defaultValue = false,
        rebootApp = true,
    )
    execute { applyPostPolicy(setting.id, listOf(PostFieldPolicy(", isTranslatable=", "Z", 1))) }
}

@Suppress("unused")
val newXHidePromoteButtonPatch = bytecodePatch(
    name = "NewX: Hide promote button",
    description = "Hides the quick-promote and boost affordances on NewX posts.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXPostModelResolutionPatch)
    val setting = newXToggle(
        id = "newx.content.hide_promote",
        category = Categories.CONTENT,
        strings = settingStrings("piko_newx_hide_promote"),
        order = 380,
        defaultValue = false,
        rebootApp = true,
    )
    execute {
        applyPostPolicy(setting.id, listOf(
            PostFieldPolicy(", quickPromoteEligibility=", null, 0),
            PostFieldPolicy(", boostProgress=", null, 0),
        ))
    }
}
