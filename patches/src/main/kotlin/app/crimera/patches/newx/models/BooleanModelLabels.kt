package app.crimera.patches.newx.models

import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val BUILDER = "Ljava/lang/StringBuilder;"
private data class ModelLabel(val text: String)
private class LabelBuilder(var label: String? = null)

/** Follows append order through R8's small, straight-line StringBuilder/boolean helpers. */
internal fun booleanModelFields(
    modelToString: Method,
    resolveHelper: (MethodReference) -> Method?,
): Map<String, FieldReference> {
    val found = linkedMapOf<String, FieldReference>()

    fun walk(method: Method, values: MutableMap<Int, Any>, depth: Int): Any? {
        if (depth > 3) throw PatchException("Nested model-label helper is too deep: $method")
        var result: Any? = null
        val instructions = method.implementation?.instructions?.toList().orEmpty()
        if (instructions.any { it is OffsetInstruction }) {
            throw PatchException("Boolean model-label helper has conditional control flow: $method")
        }
        for (instruction in instructions) {
            val one = instruction as? OneRegisterInstruction
            val two = instruction as? TwoRegisterInstruction
            when (instruction.opcode) {
                Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO -> {
                    values[one!!.registerA] = ModelLabel(instruction.getReference<StringReference>()!!.string)
                }
                Opcode.NEW_INSTANCE -> if (instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.TypeReference>()?.type == BUILDER) {
                    values[one!!.registerA] = LabelBuilder()
                }
                Opcode.IGET_BOOLEAN -> {
                    val field = instruction.getReference<FieldReference>()!!
                    if (field.definingClass == modelToString.definingClass) values[two!!.registerA] = field
                    else values.remove(two!!.registerA)
                }
                Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
                Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16 -> {
                    val value = values[two!!.registerB]
                    if (value == null) values.remove(two.registerA) else values[two.registerA] = value
                }
                Opcode.MOVE_RESULT_OBJECT -> {
                    if (result == null) values.remove(one!!.registerA) else values[one!!.registerA] = result
                }
                Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE,
                Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE,
                Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE -> {
                    val reference = instruction.getReference<MethodReference>() ?: continue
                    val registers = instruction.registersUsed
                    val arguments = registers.map(values::get)
                    result = null
                    if (reference.definingClass == BUILDER && reference.name in setOf("append", "<init>")) {
                        val builder = arguments.getOrNull(0) as? LabelBuilder ?: continue
                        when (val value = arguments.getOrNull(1)) {
                            is ModelLabel -> builder.label = value.text
                            is FieldReference -> {
                                builder.label?.let { label ->
                                    val previous = found.put(label, value)
                                    if (previous != null && previous.toString() != value.toString()) {
                                        throw PatchException("Ambiguous boolean model label '$label': $previous, $value")
                                    }
                                }
                                builder.label = null
                            }
                        }
                        if (reference.returnType == BUILDER) result = builder
                    } else if (BUILDER in reference.parameterTypes.map(CharSequence::toString) && arguments.any { it is LabelBuilder }) {
                        val helper = resolveHelper(reference)
                            ?: throw PatchException("Cannot inspect boolean model-label helper: $reference")
                        if (!AccessFlags.STATIC.isSet(helper.accessFlags)) throw PatchException("Model-label helper is not static: $helper")
                        val widths = helper.parameterTypes.map { if (it == "J" || it == "D") 2 else 1 }
                        var register = helper.implementation!!.registerCount - widths.sum()
                        var argument = 0
                        val bindings = mutableMapOf<Int, Any>()
                        widths.forEach { width ->
                            arguments.getOrNull(argument)?.let { bindings[register] = it }
                            register += width
                            argument += width
                        }
                        result = walk(helper, bindings, depth + 1)
                    }
                }
                Opcode.RETURN_OBJECT -> return values[one!!.registerA]
                else -> if (one != null && instruction.opcode.setsRegister()) values.remove(one.registerA)
            }
        }
        return null
    }

    walk(modelToString, mutableMapOf(), 0)
    return found
}
