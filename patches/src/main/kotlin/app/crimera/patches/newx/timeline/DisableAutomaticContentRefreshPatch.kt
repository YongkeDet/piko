package app.crimera.patches.newx.timeline

import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val GATE = "Lapp/morphe/extension/newx/timeline/TimelineRefreshGate;"
private const val ENUM = "Ljava/lang/Enum;"
private const val LIST = "Ljava/util/List;"

/** Complements the existing startup/foreground gate with 12.34's cursor-aware background path. */
@Suppress("unused")
val disableAutomaticContentRefreshPatch = bytecodePatch(
    name = "NewX: Disable automatic content refresh",
    description = "Also blocks viewport-aware automatic updates using the Disable automatic refresh setting. Manual refresh and pagination remain available.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(disableTimelineRefreshPatch)
    execute {
        val event = requireExactlyOne("NewX background refresh event", NewXUrtAutoRefreshEventFingerprint.scopedMatchAll()).method
        val request = requireExactlyOne("NewX background refresh repository request", event.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.INVOKE_INTERFACE) return@mapNotNull null
            instruction.getReference<MethodReference>()?.takeIf {
                it.definingClass.startsWith("Lcom/x/repositories/urt/") && it.returnType == "V" &&
                    it.parameterTypes.size == 2 && it.parameterTypes.all { type -> type.startsWith("L") }
            }
        })
        val repository = requireExactlyOne("NewX background refresh implementation", newXUrtRepositoryRequestFingerprint(request).scopedMatchAll()).method
        val requestType = classDefByOrNull(request.parameterTypes[0].toString())
            ?: throw PatchException("Missing NewX automatic refresh request enum")
        require(requestType.superclass == ENUM && requestType.fields.map { it.name }.containsAll(
            listOf("AUTO_REFRESH", "VIEWPORT_AWARE_AUTO_REFRESH", "PULL_TO_REFRESH", "OLDER_THAN", "NEWER_THAN", "GAP"),
        )) { "Unsupported NewX automatic refresh request enum: ${requestType.type}" }
        // These are the exact native accessors already resolved and injected by our dependency.
        // Reuse its validated contract rather than guessing another obfuscated owner or field.
        val accessors = repository.instructions.mapNotNull { it.getReference<MethodReference>() }
            .filter { it.definingClass == repository.definingClass && it.parameterTypes.isEmpty() }.distinct()
        val timeline = requireExactlyOne("NewX background refresh timeline accessor", accessors.filter {
            classDefByOrNull(it.returnType)?.let { type ->
                type.superclass == ENUM && type.fields.any { field -> field.name == "FOR_YOU" }
            } == true
        })
        val data = requireExactlyOne("NewX background refresh data accessor", accessors.filter {
            it.returnType.startsWith("Lkotlinx/coroutines/flow/")
        })
        val pages = requireExactlyOne("NewX background refresh page accessor", repository.instructions.mapNotNull {
            it.getReference<MethodReference>()?.takeIf { call ->
                call.definingClass == data.returnType && call.returnType == LIST && call.parameterTypes.isEmpty()
            }
        }.distinct())
        repository.insertHook(0, relocateBranchTargets = false) {
            val check = scratchRegister()
            val type = scratchRegister()
            val content = scratchRegister()
            val receiver = repository.p0Register
            val requested = receiver + 1
            invokeStatic(methodReference("$GATE->isAutomaticRefresh($ENUM)Z"), requested)
            moveResult(check, "Z")
            ifEqz(check, Target.Original)
            invokeVirtual(timeline, receiver)
            moveResult(type, timeline.returnType)
            invokeVirtual(data, receiver)
            moveResult(content, data.returnType)
            invokeInterface(pages, content)
            moveResult(content, LIST)
            invokeStatic(methodReference("$GATE->shouldBlockAutomaticRefresh($ENUM$ENUM$LIST)Z"), requested, type, content)
            moveResult(check, "Z")
            ifEqz(check, Target.Original)
            returnVoid()
        }
    }
}
