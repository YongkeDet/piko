package app.crimera.patches.newx.misc.navbar

import app.crimera.bytecode.Block
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.classDefFlatMap
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.newx.models.resolveCurrentMethod
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val GLASS = "Lapp/morphe/extension/newx/misc/FloatingGlassNavBar;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val OBJECT = "Ljava/lang/Object;"
private const val HAZE = "Ldev/chrisbanes/haze/"
private const val SHAPES = "Landroidx/compose/foundation/shape/"

private fun Method.code() = implementation?.instructions?.toList().orEmpty()
private fun MethodReference.types() = parameterTypes.map(CharSequence::toString)
private fun Instruction.call() = getReference<MethodReference>()
private fun MethodReference.isHazeWrapper() = returnType == MODIFIER &&
    types().count { it == MODIFIER } == 1 && types().count { it == COMPOSER } == 1 &&
    types().count { it.startsWith(HAZE) } == 1 && types().count { it == "I" } == 1 &&
    types().all { it == MODIFIER || it == COMPOSER || it == "I" || it.startsWith(HAZE) }

/** SizeIn also has a (FFFFZ) constructor. The padding element alone validates non-negative padding. */
internal fun resolveGlassPadding(
    candidates: Collection<Method>,
    constructor: (MethodReference) -> Method,
): Method = requireExactlyOne("NewX glass relative padding", candidates.filter { method ->
    if (method.types() != listOf(MODIFIER, "F", "F", "F", "F") || method.returnType != MODIFIER) return@filter false
    val code = method.code()
    code.withIndex().any { (index, instruction) ->
        val call = instruction.call() ?: return@any false
        call.name == "<init>" && call.types().take(5) == listOf("F", "F", "F", "F", "Z") &&
            instruction.registersUsed.getOrNull(5)?.let {
                code.resolveIntegerLiteralOnCurrentPath(index, it) == 1
            } == true && constructor(call).code().any {
                it.getReference<StringReference>()?.string == "Padding must be non-negative"
            }
    }
})

/** MOVE_RESULT and IF_NEZ both accept byte registers; no arguments need a four-bit invoke slot. */
internal fun MutableMethod.guardGlassDivider(index: Int) {
    insertHook(index, relocateBranchTargets = true) {
        val enabled = scratchRegister(RegisterLimit.BYTE)
        invokeStatic(methodReference("$GLASS->isEnabled()Z"))
        moveResult(enabled, "Z")
        ifNez(enabled, Target.AfterOriginal(1))
    }
}

context(context: BytecodePatchContext)
private fun MethodReference.mutable(): MutableMethod = requireExactlyOne(
    "NewX glass mutable method $this",
    context.mutableClassDefBy(definingClass).methods.filter {
        it.name == name && it.types() == types() && it.returnType == returnType
    },
)

context(context: BytecodePatchContext)
private fun methods(scope: String): List<Method> = classDefFlatMap { owner ->
    if (owner.type.startsWith(scope)) owner.methods.toList() else emptyList()
}

/** A private clone keeps the native Haze source/state/feature gates, changing only this bar's tint. */
context(context: BytecodePatchContext)
private fun glassHazeCopy(original: MethodReference): MutableMethod {
    val source = original.mutable()
    val owner = context.mutableClassDefBy(source.definingClass)
    val name = "pikoFloatingGlassHaze"
    if (owner.methods.any { it.name == name }) throw PatchException("Duplicate NewX glass Haze helper")
    val copy = MutableMethod(ImmutableMethod(
        source.definingClass, name, source.parameters, source.returnType, source.accessFlags,
        source.annotations, source.hiddenApiRestrictions, source.implementation,
    ))
    val alpha = requireExactlyOne("NewX native Haze color alpha copy", copy.code().withIndex().filter {
        it.value.call()?.let { call ->
            call.definingClass.startsWith("Landroidx/compose/ui/graphics/") &&
                call.types() == listOf("J", "F") && call.returnType == "J"
        } == true
    })
    val alphaRegister = alpha.value.registersUsed[2] // (J,F): two color words, then alpha.
    copy.insertHook(alpha.index, relocateBranchTargets = true) {
        invokeStatic(methodReference("$GLASS->tintAlpha()F"))
        moveResult(alphaRegister, "F")
    }
    owner.methods.add(copy)
    return copy
}

context(context: BytecodePatchContext)
private fun bridge(name: String, registers: Int, block: Block.(MutableMethod) -> Unit) {
    val owner = context.mutableClassDefBy(GLASS)
    val source = requireExactlyOne("NewX glass extension bridge $name", owner.methods.filter { it.name == name })
    val replacement = MutableMethod(ImmutableMethod(
        source.definingClass, source.name, source.parameters, source.returnType, source.accessFlags,
        source.annotations, source.hiddenApiRestrictions, MutableMethodImplementation(registers),
    ))
    replacement.insertHook(0, relocateBranchTargets = false) { block(replacement) }
    owner.methods.remove(source)
    owner.methods.add(replacement)
}

context(context: BytecodePatchContext)
private fun hazeBridge(name: String, target: MethodReference) {
    // The native wrapper's four arguments have been validated, including their order.
    if (target.types().let { it[0] != MODIFIER || !it[1].startsWith(HAZE) || it[2] != COMPOSER || it[3] != "I" }) {
        throw PatchException("Unsupported NewX glass Haze argument order: $target")
    }
    bridge(name, 8) { method ->
        val input = method.p0Register
        move(0, input, OBJECT)
        move(1, input + 1, OBJECT)
        move(2, input + 2, OBJECT)
        move(3, input + 3, "I")
        checkCast(0, MODIFIER)
        checkCast(1, target.types()[1])
        checkCast(2, COMPOSER)
        invokeStatic(target, 0, 1, 2, 3)
        moveResult(0, MODIFIER)
        returnObject(0)
    }
}

context(context: BytecodePatchContext)
private fun decorationBridge(shapeType: String) {
    val layout = methods("Landroidx/compose/foundation/layout/")
    val padding = resolveGlassPadding(layout) { it.resolveCurrentMethod("glass padding element constructor") }
    val circle = requireExactlyOne("NewX glass 50 percent rounded shape", methods(SHAPES).filter { it.name == "<clinit>" }.flatMap { method ->
        val code = method.code()
        code.withIndex().mapNotNull { (index, instruction) ->
            val factory = instruction.call() ?: return@mapNotNull null
            if (factory.types() != listOf("I") || !factory.returnType.startsWith(SHAPES)) return@mapNotNull null
            val percent = instruction.registersUsed.singleOrNull() ?: return@mapNotNull null
            if (code.resolveIntegerLiteralOnCurrentPath(index, percent) != 50) return@mapNotNull null
            val result = code.getOrNull(index + 1) as? OneRegisterInstruction ?: return@mapNotNull null
            requireExactlyOne("NewX glass circle singleton store", code.withIndex().filter { (storeIndex, store) ->
                store.opcode == Opcode.SPUT_OBJECT && store.getReference<FieldReference>()?.type == factory.returnType &&
                    code.valueReachesRegister(index + 1, result.registerA, storeIndex, (store as OneRegisterInstruction).registerA)
            }).value.getReference<FieldReference>()
        }
    })
    val draw = methods("Landroidx/compose/ui/draw/")
    val clip = requireExactlyOne("NewX glass shape clipping", draw.filter {
        it.types() == listOf(MODIFIER, shapeType) && it.returnType == MODIFIER && AccessFlags.STATIC.isSet(it.accessFlags)
    })
    val shadow = requireExactlyOne("NewX glass shadow defaults", draw.filter {
        it.types() == listOf(MODIFIER, "F", shapeType, "J", "J", "I") && it.returnType == MODIFIER
    })
    val border = requireExactlyOne("NewX glass color border", methods("Landroidx/compose/foundation/").filter {
        it.types() == listOf(MODIFIER, "F", "J", shapeType) && it.returnType == MODIFIER
    })
    val color = requireExactlyOne("NewX Compose ARGB color packing", methods("Landroidx/compose/ui/graphics/").filter {
        it.types() == listOf("I") && it.returnType == "J" && it.code().any { op -> op.opcode == Opcode.INT_TO_LONG } &&
            it.code().any { op -> op.opcode == Opcode.SHL_LONG_2ADDR }
    })
    bridge("nativeDecorate", 10) { method ->
        move(0, method.p0Register, OBJECT)
        checkCast(0, MODIFIER)
        constInt(1, 12f.toRawBits())
        constInt(2, 0)
        constInt(3, 12f.toRawBits())
        constInt(4, 8f.toRawBits())
        invokeStatic(padding, 0, 1, 2, 3, 4)
        moveResult(0, MODIFIER)
        sget(2, circle)
        constInt(1, 7f.toRawBits())
        constLong(3, 0)
        constLong(5, 0)
        // Native default bits 8/16 select its ambient/spot colors. Clip remains false here.
        constInt(7, 24)
        invokeStatic(shadow, 0, 1, 2, 3, 4, 5, 6, 7)
        moveResult(0, MODIFIER)
        invokeStatic(clip, 0, 2)
        moveResult(0, MODIFIER)
        move(4, 2, circle.type)
        invokeStatic(methodReference("$GLASS->borderArgb()I"))
        moveResult(2, "I")
        invokeStatic(color, 2)
        moveResult(2, "J")
        constInt(1, 0.75f.toRawBits())
        invokeStatic(border, 0, 1, 2, 3, 4)
        moveResult(0, MODIFIER)
        returnObject(0)
    }
}

@Suppress("unused")
val floatingGlassNewXNavBarPatch = bytecodePatch(
    name = "NewX: Floating glass navigation bar",
    description = "Adds an optional translucent, rounded floating bottom navigation bar.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    newXToggle(
        id = "newx.appearance.floating_glass_navbar",
        category = Categories.APPEARANCE,
        strings = settingStrings("piko_newx_floating_glass_navbar"),
        order = 280,
        defaultValue = false,
        rebootApp = true,
    )
    execute {
        val host = requireExactlyOne("NewX animated bottom bar host", methods("Lcom/x/ui/common/").filter {
            it.code().any { instruction -> instruction.getReference<StringReference>()?.string == "BottomBarOffsetAnimation" }
        })
        val candidates = host.code().mapNotNull { instruction ->
            if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
            val type = instruction.getReference<TypeReference>()?.type ?: return@mapNotNull null
            val owner = classDefByOrNull(type) ?: return@mapNotNull null
            if ("Lkotlin/jvm/functions/Function3;" !in owner.interfaces || owner.fields.none { it.type.startsWith(HAZE) }) return@mapNotNull null
            owner.methods.filter { method -> method.code().any { it.call()?.isHazeWrapper() == true } }
        }.flatten()
        val bar = requireExactlyOne("NewX inset-aware main bottom bar lambda", candidates).mutable()
        val code = bar.code()
        val hazeCall = requireExactlyOne("NewX bottom bar Haze call", code.withIndex().filter { it.value.call()?.isHazeWrapper() == true })
        val haze = hazeCall.value.call()!!
        val copy = glassHazeCopy(haze)
        hazeBridge("nativeHaze", haze)
        hazeBridge("nativeGlassHaze", copy)
        val background = requireExactlyOne("NewX bottom bar fallback background", code.mapNotNull { it.call() }.filter {
            it.definingClass.startsWith("Landroidx/compose/foundation/") && it.returnType == MODIFIER &&
                it.types().take(2) == listOf(MODIFIER, "J") && it.types().size == 3
        })
        decorationBridge(background.types()[2])
        val join = requireExactlyOne("NewX bottom bar inset/background join", code.withIndex().filter {
            it.value.opcode == Opcode.INVOKE_INTERFACE && it.value.call()?.let { call ->
                call.definingClass == MODIFIER && call.types() == listOf(MODIFIER) && call.returnType == MODIFIER
            } == true
        })
        val insetRegister = join.value.registersUsed[0]
        val inset = requireExactlyOne("NewX bottom bar system inset modifier", code.withIndex().filter { (index, instruction) ->
            val call = instruction.call() ?: return@filter false
            if (!call.definingClass.startsWith("Landroidx/compose/foundation/layout/") || call.returnType != MODIFIER ||
                call.types().size != 2 || call.types()[0] != MODIFIER || !call.types()[1].startsWith("Landroidx/compose/foundation/layout/")) return@filter false
            val result = code.getOrNull(index + 1) as? OneRegisterInstruction ?: return@filter false
            index < hazeCall.index && code.valueReachesRegister(index + 1, result.registerA, join.index, insetRegister)
        })
        val blurRead = requireExactlyOne("NewX bottom bar blur gate", code.withIndex().filter { (index, instruction) ->
            instruction.opcode == Opcode.IGET_BOOLEAN && instruction.getReference<FieldReference>()?.definingClass == bar.definingClass &&
                index in (inset.index + 2) until hazeCall.index
        })
        val divider = requireExactlyOne("NewX bottom bar top divider", code.withIndex().filter {
            it.value.call()?.let { call -> call.definingClass.startsWith("Landroidx/compose/material3/") &&
                call.returnType == "V" && call.types().count { type -> type == "F" } == 1 &&
                call.types().count { type -> type == "J" } == 1 && COMPOSER in call.types() && MODIFIER in call.types()
            } == true
        })
        // Mutate from high indexes downward. Only this bottom bar's divider is suppressed.
        bar.guardGlassDivider(divider.index)
        bar.insertHook(join.index, relocateBranchTargets = true) {
            invokeStatic(methodReference("$GLASS->decorate($OBJECT)$OBJECT"), insetRegister)
            moveResult(insetRegister, OBJECT)
            checkCast(insetRegister, MODIFIER)
        }
        val replacement = bar.insertHook(hazeCall.index, relocateBranchTargets = true) {
            invokeStatic(methodReference("$GLASS->haze($OBJECT$OBJECT${OBJECT}I)$OBJECT"), *hazeCall.value.registersUsed.toIntArray())
        }
        bar.implementation!!.removeInstruction(replacement.lastIndex + 1)
        // The Java bridge returns Object; restore Modifier before the native interface join.
        val hazeResult = code.getOrNull(hazeCall.index + 1) as? OneRegisterInstruction
            ?: throw PatchException("NewX bottom bar Haze has no object result")
        if (code[hazeCall.index + 1].opcode != Opcode.MOVE_RESULT_OBJECT) {
            throw PatchException("NewX bottom bar Haze result is not a modifier")
        }
        bar.insertHook(hazeCall.index + 2, relocateBranchTargets = true) {
            checkCast(hazeResult.registerA, MODIFIER)
        }
        val gateRegister = (blurRead.value as OneRegisterInstruction).registerA
        bar.insertHook(blurRead.index + 1, relocateBranchTargets = false) {
            invokeStatic(methodReference("$GLASS->useBlur(Z)Z"), gateRegister)
            moveResult(gateRegister, "Z")
        }
    }
}
