package app.crimera.patches.newx.timeline

// Catches the 12.34 save hook's scratch-allocation failure at a switch, and ensures a
// non-loaded state skips only map.put while preserving the native bookkeeping tail.
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderPackedSwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RestoreTimelinePositionPatchTest {
    @Test
    fun `automatic refresh gate fits two free locals and leaves request inputs untouched`() {
        // The real 12.34 repository entry has only two provably free four-bit locals.
        val owner = "Lfixture/Repository;"
        val implementation = MethodImplementationBuilder(11)
        val method = MutableMethod(ImmutableMethod(owner, "request",
            listOf("Ljava/lang/Enum;", "Ljava/lang/Object;").map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", AccessFlags.PUBLIC.value, emptySet(), emptySet(), implementation.methodImplementation))
        method.addInstructionsWithLabels(0, """
            invoke-static/range {v2 .. v10}, Lfixture/Sink;->consume(IIIIIILjava/lang/Object;Ljava/lang/Enum;Ljava/lang/Object;)V
            return-void
        """.trimIndent())
        val native = method.instructions[0]
        method.insertAutomaticContentRefreshGate(
            ImmutableMethodReference(owner, "timeline", emptyList<String>(), "Ljava/lang/Enum;"),
            ImmutableMethodReference(owner, "data", emptyList<String>(), "Lfixture/Flow;"),
            ImmutableMethodReference("Lfixture/Flow;", "pages", emptyList<String>(), "Ljava/util/List;"),
        )
        val code = method.instructions
        val content = (code[1] as OneRegisterInstruction).registerA
        val timeline = (code[4] as OneRegisterInstruction).registerA
        assertEquals(setOf(0, 1), setOf(content, timeline))
        assertEquals(content, (code[6] as OneRegisterInstruction).registerA)
        assertEquals(content, (code[8] as OneRegisterInstruction).registerA)
        assertEquals(content, (code[10] as OneRegisterInstruction).registerA)
        assertEquals(listOf(9, timeline, content), code[9].registersUsed)
        assertSame(native, (code[2] as BuilderOffsetInstruction).target.location.instruction)
        assertSame(native, (code[11] as BuilderOffsetInstruction).target.location.instruction)
        assertEquals(Opcode.RETURN_VOID, code[12].opcode)
        assertEquals((2..10).toList(), code[13].registersUsed)
        assertEquals(11, method.implementation!!.registerCount)
    }

    @Test
    fun `capture uses fresh locals before a native switch and preserves the map continuation`() {
        val owner = "Lapp/crimera/test/ScrollComponent;"
        val holder = "Lapp/crimera/test/ScrollHolder;"
        val implementation = MethodImplementationBuilder(32)
        val original = MutableMethod(ImmutableMethod(owner, "save",
            listOf(ImmutableMethodParameter(holder, emptySet(), null)), "V", AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), implementation.methodImplementation))
        original.addInstructionsWithLabels(0, "return-void")
        original.addInstructionsWithLabels(0, """
            invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/ConcurrentHashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            invoke-static/range {v0 .. v31}, Lapp/crimera/test/Sink;->consume(${"Ljava/lang/Object;".repeat(32)})V
        """.trimIndent())
        val body = original.implementation!!
        val exit = body.newLabelForIndex(original.instructions.lastIndex)
        val payloadIndex = original.instructions.size
        body.addInstruction(BuilderPackedSwitchPayload(0, listOf(exit)))
        val payload = body.newLabelForIndex(payloadIndex)
        body.addInstruction(2, BuilderInstruction31t(Opcode.PACKED_SWITCH, 3, payload))
        val map = original.instructions.first()
        original.addInstructionsWithLabels(0, "if-eqz v4, :map", ExternalLabel("map", map))

        val method = original.cloneMutable(additionalRegisters = original.numberOfParameterRegisters + 3)
        val mapIndex = method.instructions.indexOfFirst {
            it.getReference<MethodReference>()?.name == "put"
        }
        val originalMap = method.instructions[mapIndex]
        val tail = method.instructions[mapIndex + 1]
        val incoming = method.instructions.filter { it.opcode == Opcode.IF_EQZ }.single() as BuilderOffsetInstruction
        val capture = ImmutableMethodReference(owner, "capture", listOf(owner, "Ljava/lang/Enum;", holder), "Z")
        insertTimelinePositionCapture(method, mapIndex, capture, 1, 2, 32)

        val instructions = method.instructions
        val callIndex = instructions.indexOfFirst { it.getReference<MethodReference>() == capture }
        val call = instructions[callIndex] as RegisterRangeInstruction
        assertEquals(32, call.startRegister)
        assertEquals(3, call.registerCount)
        assertEquals(Opcode.MOVE_RESULT, instructions[callIndex + 1].opcode)
        assertEquals(32, (instructions[callIndex + 1] as OneRegisterInstruction).registerA)
        assertSame(originalMap, (instructions[callIndex + 2] as BuilderOffsetInstruction).target.location.instruction)
        assertSame(tail, (instructions[callIndex + 3] as BuilderOffsetInstruction).target.location.instruction)
        assertEquals(mapIndex, incoming.target.location.index)
        assertEquals(Opcode.MOVE_OBJECT_FROM16, incoming.target.location.instruction!!.opcode)
    }
}
