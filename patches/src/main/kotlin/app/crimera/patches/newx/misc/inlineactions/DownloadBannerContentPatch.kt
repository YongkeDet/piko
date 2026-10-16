package app.crimera.patches.newx.misc.inlineactions

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val B = "Lapp/morphe/extension/newx/misc/NewXInAppNotification;"
private const val OBJ = "Ljava/lang/Object;"
private const val MOD = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION = "Lkotlin/jvm/functions/Function0;"
private fun Method.body() = implementation?.instructions?.toList().orEmpty()
private fun MethodReference.parameters() = parameterTypes.map(CharSequence::toString)
private fun String.width() = if (this == "J" || this == "D") 2 else 1

/** Parameter origins survive moves and casts, but never arbitrary writes or R8 parameter reuse. */
internal fun Method.bannerParameterOrigins(index: Int): Map<Int, Int> {
    if (!AccessFlags.STATIC.isSet(accessFlags)) throw PatchException("Native banner renderer is no longer static: $this")
    val values = mutableMapOf<Int, Int>()
    val parameters = parameterTypes.map(CharSequence::toString)
    var register = implementation!!.registerCount - parameters.sumOf { it.width() }
    parameters.forEachIndexed { parameter, type -> repeat(type.width()) { values[register++] = parameter } }
    body().take(index).forEach { instruction ->
        val one = instruction as? OneRegisterInstruction
        if (instruction.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
                Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16, Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16)) {
            val move = instruction as TwoRegisterInstruction
            val source = values[move.registerB]
            values.remove(move.registerA)
            if (instruction.opcode.setsWideRegister()) values.remove(move.registerA + 1)
            if (source != null) {
                values[move.registerA] = source
                if (instruction.opcode.setsWideRegister()) values[move.registerA + 1] = source
            }
        } else if (instruction.opcode != Opcode.CHECK_CAST && instruction.opcode.setsRegister() && one != null) {
            values.remove(one.registerA)
            if (instruction.opcode.setsWideRegister()) values.remove(one.registerA + 1)
        }
    }
    return values
}

internal data class BannerTextCall(val method: Method, val index: Int, val target: MethodReference,
    val model: Int, val composer: Int, val overflow: Int, val wrap: Int, val max: Int)

/** Follow both real API renderers, including the AnnotatedString headline and fallback body. */
internal fun bannerTextCalls(method: Method, api: String): List<BannerTextCall> {
    val parameters = method.parameterTypes.map(CharSequence::toString)
    if (COMPOSER !in parameters || api !in parameters) return emptyList()
    val modelParameter = requireExactlyOne("Native banner model parameter", parameters.indices.filter { parameters[it] == api })
    val code = method.body()
    return code.withIndex().mapNotNull { (index, instruction) ->
        val call = instruction.getReference<MethodReference>() ?: return@mapNotNull null
        val types = call.parameters()
        if (!call.definingClass.startsWith("Landroidx/compose/material3/") || call.returnType != "V" ||
            COMPOSER !in types || MOD !in types || types.none { it == "Ljava/lang/String;" || it.startsWith("Landroidx/compose/ui/text/") }) return@mapNotNull null
        val groups = types.indices.filter { types.drop(it).take(4) == listOf("I", "Z", "I", "I") }
        if (groups.isEmpty()) return@mapNotNull null
        val controls = requireExactlyOne("Native banner overflow/wrap/max/min tuple", groups)
        val composerParameter = requireExactlyOne("Native banner text Composer", types.indices.filter { types[it] == COMPOSER })
        val origins = method.bannerParameterOrigins(index)
        val receivers = code.mapNotNull { item ->
            item.getReference<FieldReference>()?.takeIf { it.definingClass == api && item.opcode == Opcode.IGET_OBJECT }
                ?.let { (item as TwoRegisterInstruction).registerB }
        }.distinct().filter { origins[it] == modelParameter && it <= 15 }
        val receiver = requireExactlyOne("Live low-register notification model at text call", receivers)
        val words = instruction.registersUsed
        fun argument(parameter: Int) = words[types.take(parameter).sumOf { it.width() }]
        BannerTextCall(method, index, call, receiver, argument(composerParameter), argument(controls), argument(controls + 1), argument(controls + 2))
    }
}

/** Defaults already equal Clip/true/unlimited; supplying those values also covers explicit limits. */
internal fun MutableMethod.expandBannerText(site: BannerTextCall) {
    insertHook(site.index, relocateBranchTargets = false) {
        val owned = scratchRegister(RegisterLimit.BYTE)
        invokeStatic(methodReference("$B->fullContent($OBJ)Z"), site.model)
        moveResult(owned, "Z"); ifEqz(owned, Target.Original)
        constInt(site.overflow, 1)
        constInt(site.wrap, 1)
        constInt(site.max, Int.MAX_VALUE)
    }
}

context(context: BytecodePatchContext)
internal fun patchFullDownloadBanner(runtime: ResolvedNotificationRuntime, classes: List<ClassDef>, model: ClassDef) {
    val sites = classes.flatMap { type -> type.methods.flatMap { bannerTextCalls(it, model.type) } }
    val headline = requireExactlyOne("Native rich notification headline", sites.filter { it.target.parameters().none { p -> p == "Ljava/lang/String;" } })
    val detail = requireExactlyOne("Native notification detail", sites.filter { it.method.toString() == headline.method.toString() && it != headline })
    val fallback = requireExactlyOne("Native fallback notification body", sites.filter { it.method.toString() != headline.method.toString() })
    listOf(headline, detail, fallback).forEach { site ->
        val target = requireExactlyOne("Native text implementation", context.classDefByOrNull(site.target.definingClass)!!.methods.filter { it.toString() == site.target.toString() })
        if (!AccessFlags.STATIC.isSet(target.accessFlags) || target.body().none { (it as? NarrowLiteralInstruction)?.narrowLiteral == Int.MAX_VALUE }) {
            throw PatchException("Native text no longer defaults to unlimited lines: $target")
        }
    }
    patchProgressIndicator(classes, headline.target)
    listOf(headline, detail, fallback).groupBy { it.method.definingClass }.forEach { (owner, byOwner) ->
        byOwner.groupBy { it.method.toString() }.forEach { (signature, calls) ->
            val method = requireExactlyOne("Mutable full notification renderer", context.mutableClassDefBy(owner).methods.filter { it.toString() == signature })
            calls.sortedByDescending { it.index }.forEach { site ->
                if (site != headline) method.insertHook(site.index + 1, relocateBranchTargets = false) {
                    // The void text call leaves both caller registers intact. Its old argument
                    // registers are dead here, giving typed invoke staging bounded scratch space.
                    val composer = scratchRegister(RegisterLimit.FOUR_BIT)
                    move(composer, site.composer, OBJ)
                    invokeStatic(methodReference("$B->drawProgress($OBJ$OBJ)V"), site.model, composer)
                }
                // Argument preparation falls through this call on both validated renderers.
                method.expandBannerText(site)
            }
        }
    }
}

context(context: BytecodePatchContext)
private fun patchProgressIndicator(classes: List<ClassDef>, text: MethodReference) {
    val methods = classes.flatMap { it.methods }
    val linear = requireExactlyOne("Native determinate linear progress indicator", methods.filter { method ->
        val parameters = method.parameters()
        method.definingClass.startsWith("Landroidx/compose/material3/") && method.returnType == "V" &&
            COMPOSER in parameters && MOD in parameters && FUNCTION in parameters &&
            "Lkotlin/jvm/functions/Function1;" in parameters && parameters.count { it == "J" } == 2 &&
            parameters.count { it == "F" } == 1 && AccessFlags.STATIC.isSet(method.accessFlags)
    })
    val indeterminate = requireExactlyOne("Native indeterminate linear progress indicator", methods.filter { method ->
        val parameters = method.parameters()
        method.definingClass == linear.definingClass && method.returnType == "V" && COMPOSER in parameters && MOD in parameters &&
            FUNCTION !in parameters && parameters.count { it == "J" } == 2 && parameters.count { it == "F" } == 1 &&
            method.body().none { it.getReference<MethodReference>()?.toString() == linear.toString() } &&
            AccessFlags.STATIC.isSet(method.accessFlags)
    })
    val color = requireExactlyOne("Native progress ARGB packing", methods.filter {
        it.definingClass.startsWith("Landroidx/compose/ui/graphics/") && it.parameters() == listOf("I") && it.returnType == "J" &&
            it.body().any { op -> op.opcode == Opcode.INT_TO_LONG } && it.body().any { op -> op.opcode == Opcode.SHL_LONG_2ADDR }
    })
    val fill = requireExactlyOne("Native progress fill width", methods.filter {
        it.definingClass.startsWith("Landroidx/compose/foundation/layout/") && it.parameters() == listOf(MOD, "F") && it.returnType == MOD &&
            it.body().any { op -> op.getReference<FieldReference>()?.name == "Horizontal" }
    })
    val textClass = context.classDefByOrNull(text.definingClass) ?: throw PatchException("Missing native text class")
    val empty = requireExactlyOne("Native empty Modifier", textClass.methods.flatMap { it.body().mapNotNull { op -> op.getReference<FieldReference>() } }.filter { field ->
        field.definingClass.startsWith("Landroidx/compose/ui/") && context.classDefByOrNull(field.type)?.interfaces?.contains(MOD) == true && field.definingClass == field.type
    }.distinctBy { it.toString() })
    val provider = context.mutableClassDefBy("${B.dropLast(1)}\$ProgressProvider;")
    if (provider.methods.none { it.name == "invoke" && it.parameters().isEmpty() && it.returnType == OBJ }) throw PatchException("Missing progress provider invoke")
    provider.interfaces.add(FUNCTION)
    val callback = bridgeMethod("drawNativeProgress", listOf(OBJ, OBJ, "Z"), 15)
    callback.emitNativeProgress(linear, indeterminate, color, fill, empty)
}

/** Dirty/default mask parameters are discovered from actual bit tests, including reordered ABI. */
private fun progressMask(method: Method, bit: Int): Int {
    val code = method.body()
    return requireExactlyOne("Native progress mask bit $bit", code.withIndex().mapNotNull { (index, instruction) ->
        if (instruction.opcode !in setOf(Opcode.AND_INT_LIT8, Opcode.AND_INT_LIT16) || (instruction as NarrowLiteralInstruction).narrowLiteral != bit) return@mapNotNull null
        val source = (instruction as TwoRegisterInstruction).registerB
        method.bannerParameterOrigins(index)[source]?.takeIf { method.parameters()[it] == "I" }
    }.distinct())
}

internal fun MutableMethod.emitNativeProgress(linear: Method, indeterminate: Method, color: MethodReference,
    fill: MethodReference, empty: FieldReference) {
    val defaults = progressMask(linear, 16)
    val changed = progressMask(indeterminate, 6)
    val indeterminateCap = requireExactlyOne("Native indeterminate stroke cap", indeterminate.parameters().indices.filter { indeterminate.parameters()[it] == "I" && it != changed })
    insertHook(0, relocateBranchTargets = false) {
        move(14, p0Register + 2, "Z"); ifNez(14, Target.Local("unknown"))
        fun draw(target: Method, unknown: Boolean) {
            val types = target.parameters()
            val count = types.sumOf { it.width() }
            if (count > 12) throw PatchException("Native progress indicator exceeds bounded argument frame: $target")
            val slots = types.indices.associateWith { types.take(it).sumOf { type -> type.width() } }
            (0 until count).forEach { constInt(it, 0) }
            types.forEachIndexed { index, type ->
                val register = slots.getValue(index)
                when (type) {
                    MOD -> {
                        sget(register, empty); constInt(12, 1f.toRawBits())
                        invokeStatic(fill, register, 12); moveResult(register, MOD)
                    }
                    COMPOSER -> { move(register, p0Register + 1, OBJ); checkCast(register, COMPOSER) }
                    FUNCTION -> { move(register, p0Register, OBJ); checkCast(register, FUNCTION) }
                }
            }
            val colors = types.indices.filter { types[it] == "J" }
            colors.forEachIndexed { colorIndex, parameter ->
                invokeStatic(methodReference("$B->${if (colorIndex == 0) "progressAccent" else "progressTrack"}()I")); moveResult(12, "I")
                invokeStatic(color, 12); moveResult(slots.getValue(parameter), "J")
            }
            if (unknown) constInt(slots.getValue(indeterminateCap), 1)
            else constInt(slots.getValue(defaults), 16 or 32 or 64)
            invokeStatic(target, *IntArray(count) { it })
            returnVoid()
        }
        draw(linear, false)
        label("unknown")
        draw(indeterminate, true)
    }
}
