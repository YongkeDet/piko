package app.crimera.patches.newx.misc.mediaquality

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.newx.models.modelFieldsByAppendOrder
import app.crimera.patches.newx.models.requireSingle
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.settings.ToggleSettingDefinition
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// The native selector minimizes abs(format.height - preferredHeight). Half MAX_VALUE stays
// above valid video dimensions without overflowing that distance for Format.NO_VALUE (-1).
private const val UNBOUNDED_PREFERRED_HEIGHT = Int.MAX_VALUE / 2

/** Current X consumes a fixed-height rendition policy instead of the removed audio callback. */
context(context: BytecodePatchContext)
internal fun patchPreferredVideoResolution(setting: ToggleSettingDefinition) {
    fun fields(method: Method) = modelFieldsByAppendOrder(method) { reference ->
        context.classDefByOrNull(reference.definingClass)?.methods?.let { methods ->
            requireAtMostOne("video-policy append helper $reference", methods.filter { candidate ->
                candidate.name == reference.name && candidate.returnType == reference.returnType &&
                    candidate.parameterTypes.map(CharSequence::toString) == reference.parameterTypes.map(CharSequence::toString)
            })
        }
    }
    val preferred = Fingerprint(
        definingClass = "Lcom/x/media/playback/", name = "toString", parameters = emptyList(),
        returnType = "Ljava/lang/String;", filters = listOf(string("PreferredHeight(heightPx=")),
    ).requireSingle("preferred video-height policy")
    val configuration = Fingerprint(
        definingClass = "Lcom/x/media/playback/", name = "toString", parameters = emptyList(),
        returnType = "Ljava/lang/String;", filters = listOf(
            string("PlayerConfiguration(videoScale="), string(", renditionSizing="),
        ),
    ).requireSingle("video player configuration")
    val policyField = fields(configuration.originalMethod)[", renditionSizing="]
        ?: throw PatchException("Missing player rendition-sizing field")
    if (policyField.type !in preferred.originalClassDef.interfaces) {
        throw PatchException("Preferred video height is not a player rendition policy: $policyField")
    }
    val preferredConstructor = requireExactlyOne("preferred video-height constructor", preferred.originalClassDef.methods.filter {
        it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf("I")
    })
    val heightField = fields(preferred.originalMethod)["PreferredHeight(heightPx="]
        ?: throw PatchException("Missing preferred-height value field")
    if (heightField.type != "I") throw PatchException("Preferred video height is not an int: $heightField")
    val owner = configuration.classDef
    val writers = owner.methods.filter { method -> method.instructions.any { instruction ->
        instruction.opcode == Opcode.IPUT_OBJECT && instruction.getReference<FieldReference>()?.toString() == policyField.toString()
    } }
    if (writers.isEmpty() || writers.any { it.name != "<init>" }) {
        throw PatchException("NewX player rendition policy is not constructor-owned: $writers")
    }
    writers.forEach { original ->
        val method = original.cloneMutable(additionalRegisters = original.numberOfParameterRegisters + 3)
        owner.methods.remove(original)
        owner.methods.add(method)
        val exits = method.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
        if (exits.isEmpty()) throw PatchException("Player configuration has no normal constructor exit")
        exits.sortedDescending().forEach { index ->
            method.insertHook(index, excludedRegisters = listOf(method.p0Register), relocateBranchTargets = true) {
                val value = scratchRegister(RegisterLimit.FOUR_BIT)
                val height = scratchRegister(RegisterLimit.FOUR_BIT)
                val self = scratchRegister(RegisterLimit.FOUR_BIT)
                constString(value, setting.id)
                invokeStatic(methodReference("$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;)Z"), value)
                moveResult(value, "Z")
                ifEqz(value, Target.Original)
                newInstance(value, preferred.originalClassDef.type)
                constInt(height, UNBOUNDED_PREFERRED_HEIGHT)
                invokeDirect(preferredConstructor, value, height)
                move(self, method.p0Register, owner.type)
                iput(value, self, policyField)
            }
        }
    }

    // X otherwise permits adaptive playback down to half the chosen height. Keep the minimum
    // equal to the selected maximum so "force HD" cannot fall back to a lower resolution.
    val appliers = Fingerprint(
        name = "invokeSuspend", parameters = listOf("Ljava/lang/Object;"), returnType = "Ljava/lang/Object;",
        filters = listOf(fieldAccess(opcode = Opcode.IGET, definingClass = preferred.originalClassDef.type,
            name = heightField.name, type = "I")),
    ).matchAllOrNull().orEmpty()
    val applier = requireExactlyOne("native preferred-height application", appliers)
    val original = applier.method
    val method = original.cloneMutable(additionalRegisters = original.numberOfParameterRegisters + 2)
    applier.classDef.methods.remove(original)
    applier.classDef.methods.add(method)
    preserveSelectedVideoHeight(method, setting.id, original.implementation!!.registerCount)
}

/** Keep the chosen height across both the direct setter and R8's shared-setter merge shape. */
internal fun preserveSelectedVideoHeight(method: MutableMethod, settingId: String, reservedRegister: Int) {
    if (reservedRegister !in 0..RegisterLimit.BYTE.maximum || reservedRegister + 2 != method.p0Register) {
        throw PatchException("NewX video-height guard has no reserved byte-sized register")
    }
    val body = method.instructions.toList()
    fun setterAfter(index: Int): com.android.tools.smali.dexlib2.iface.instruction.Instruction? {
        var next = index + 1
        val visited = mutableSetOf<Int>()
        while (next in body.indices && visited.add(next)) {
            val instruction = body[next]
            if (instruction.opcode !in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) return instruction
            next = (instruction as? BuilderOffsetInstruction)?.target?.location?.index ?: return null
        }
        return null
    }
    val divisions = body.indices.filter { index ->
        val division = body[index] as? TwoRegisterInstruction ?: return@filter false
        if (body[index].opcode != Opcode.DIV_INT_2ADDR) return@filter false
        val result = body.getOrNull(index - 1) as? OneRegisterInstruction ?: return@filter false
        val getter = body.getOrNull(index - 2)?.getReference<MethodReference>() ?: return@filter false
        val setterInstruction = setterAfter(index) ?: return@filter false
        val setter = setterInstruction.getReference<MethodReference>() ?: return@filter false
        result.opcode == Opcode.MOVE_RESULT && result.registerA == division.registerA &&
            getter.definingClass == "Ljava/lang/Integer;" && getter.name == "intValue" &&
            setter.parameterTypes.map(CharSequence::toString) == listOf("I") && setter.returnType == "V" &&
            setter.definingClass.startsWith("Landroidx/media3/") &&
            setterInstruction.registersUsed.lastOrNull() == division.registerA &&
            body.resolveIntegerLiteralOnCurrentPath(index, division.registerB) == 2
    }
    val divisionIndex = requireExactlyOne("native half-resolution adaptive constraint", divisions)
    method.insertHook(divisionIndex, excludedRegisters = body[divisionIndex].registersUsed, relocateBranchTargets = true) {
        val enabled = reservedRegister
        constString(enabled, settingId)
        invokeStatic(methodReference("$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;)Z"), enabled)
        moveResult(enabled, "Z")
        ifEqz(enabled, Target.Original)
        goto(Target.AfterOriginal(1))
    }
}
