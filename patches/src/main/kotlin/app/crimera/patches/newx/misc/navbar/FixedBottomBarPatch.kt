package app.crimera.patches.newx.misc.navbar

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.classDefFlatMap
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val VISIBILITY = "Lapp/morphe/extension/newx/misc/BottomBarVisibility;"
private fun Method.barCode() = implementation?.instructions?.toList().orEmpty()

internal data class BottomBarScrollRead(val index: Int, val register: Int)

/** Trace the state used by the scaffold's public isVisible contract back into its host. */
internal fun resolveBottomBarScrollRead(host: Method, constructor: Method, getter: Method): BottomBarScrollRead {
    val field = requireExactlyOne("NewX bottom bar visibility state field", getter.barCode().mapNotNull {
        if (it.opcode == Opcode.IGET_OBJECT) it.getReference<FieldReference>() else null
    })
    val assignment = requireExactlyOne("NewX bottom bar visibility constructor assignment", constructor.barCode().filter {
        it.opcode == Opcode.IPUT_OBJECT && it.getReference<FieldReference>() == field
    }) as TwoRegisterInstruction
    require(assignment.registerB == constructor.p0Register) { "NewX bottom bar state is not stored on its receiver" }
    val parameterSlot = assignment.registerA - constructor.p0Register
    require(parameterSlot > 0) { "NewX bottom bar state is not a constructor parameter" }
    val code = host.barCode()
    val creation = requireExactlyOne("NewX bottom bar visibility state creation", code.withIndex().filter {
        it.value.getReference<MethodReference>()?.let { call ->
            call.definingClass == constructor.definingClass && call.name == "<init>" &&
                call.parameterTypes == constructor.parameterTypes
        } == true
    })
    val stateRegister = creation.value.registersUsed[parameterSlot]
    val stateGet = requireExactlyOne("NewX bottom bar visibility state getValue", getter.barCode().mapNotNull {
        it.getReference<MethodReference>()?.takeIf { call -> call.name == "getValue" && call.returnType == "Ljava/lang/Object;" }
    })
    return requireExactlyOne("NewX bottom bar scroll visibility read", code.withIndex().mapNotNull { (index, instruction) ->
        if (instruction.getReference<MethodReference>() != stateGet ||
            instruction.registersUsed.size != 1 ||
            !code.valueReachesRegister(index, instruction.registersUsed.single(), creation.index, stateRegister)) return@mapNotNull null
        val result = code.getOrNull(index + 1) as? OneRegisterInstruction ?: return@mapNotNull null
        if (code[index + 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
            code.getOrNull(index + 2)?.getReference<TypeReference>()?.type != "Ljava/lang/Boolean;" ||
            (code[index + 2] as? OneRegisterInstruction)?.registerA != result.registerA) return@mapNotNull null
        val unbox = code.getOrNull(index + 3) ?: return@mapNotNull null
        if (unbox.getReference<MethodReference>()?.toString() != "Ljava/lang/Boolean;->booleanValue()Z" ||
            unbox.registersUsed != listOf(result.registerA)) return@mapNotNull null
        val value = code.getOrNull(index + 4) as? OneRegisterInstruction ?: return@mapNotNull null
        if (code[index + 4].opcode != Opcode.MOVE_RESULT) return@mapNotNull null
        BottomBarScrollRead(index + 4, value.registerA)
    })
}

internal fun MutableMethod.pinBottomBarScrollRead(read: BottomBarScrollRead) {
    // Fall through from unboxing; leave incoming labels on the following native branch untouched.
    // Outer route/fullscreen guards therefore continue to skip this scroll-state read entirely.
    insertHook(read.index + 1, relocateBranchTargets = false) {
        invokeStatic(methodReference("$VISIBILITY->keepVisible(Z)Z"), read.register)
        moveResult(read.register, "Z")
    }
}

@Suppress("unused")
val fixedBottomBarPatch = bytecodePatch(
    name = "NewX: Fixed bottom navigation bar",
    description = "Keeps the main bottom bar visible while scrolling, including the floating glass style.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    newXToggle(
        id = "newx.navigation.fixed_bottom_bar",
        category = Categories.NAVIGATION,
        strings = settingStrings("piko_newx_fixed_bottom_bar"),
        order = 120,
        defaultValue = true,
        rebootApp = true,
    )
    execute {
        val host = requireExactlyOne("NewX fixed bottom bar host", classDefFlatMap { owner ->
            if (!owner.type.startsWith("Lcom/x/ui/common/")) emptyList() else owner.methods.filter { method ->
                method.barCode().any { it.getReference<StringReference>()?.string == "BottomBarOffsetAnimation" }
            }
        })
        val state = requireExactlyOne("NewX bottom bar scaffold visibility contract", host.barCode().mapNotNull {
            if (it.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
            val owner = it.getReference<TypeReference>()?.type?.let { type -> classDefByOrNull(type) } ?: return@mapNotNull null
            owner.takeIf { type -> type.methods.any { method -> method.name == "isVisible" && method.parameterTypes.isEmpty() && method.returnType == "Z" } &&
                type.methods.any { method -> method.name == "setVisible" && method.parameterTypes.map(CharSequence::toString) == listOf("Z") && method.returnType == "V" } &&
                type.methods.any { method -> method.name == "getSize" && method.parameterTypes.isEmpty() && method.returnType == "I" } }
        }.distinctBy { it.type })
        val constructor = requireExactlyOne("NewX bottom bar scaffold constructor", state.methods.filter { it.name == "<init>" })
        val getter = requireExactlyOne("NewX bottom bar scaffold isVisible", state.methods.filter { it.name == "isVisible" && it.parameterTypes.isEmpty() && it.returnType == "Z" })
        val read = resolveBottomBarScrollRead(host, constructor, getter)
        val mutable = requireExactlyOne("NewX fixed bottom bar mutable host", mutableClassDefBy(host.definingClass).methods.filter {
            it.name == host.name && it.parameterTypes == host.parameterTypes && it.returnType == host.returnType
        })
        mutable.pinBottomBarScrollRead(read)
    }
}
