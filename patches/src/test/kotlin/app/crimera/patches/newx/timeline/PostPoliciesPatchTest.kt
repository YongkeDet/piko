package app.crimera.patches.newx.timeline

// Guards two bytecode failures: serialization branches bypassing the setting check, and a later
// policy causing an earlier disabled-policy branch to skip every remaining policy. The high-register
// fixture also verifies that final-field writes use legal nibble registers without clobbering this.
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PostPoliciesPatchTest {
    private val owner = "Lapp/crimera/test/PostPolicyFixture;"

    @Test
    fun `serialization exit branches run the guard and disabled policy skips its writes`() {
        val method = fixture()
        val originalReturn = method.instructions.last()
        val incoming = method.instructions.single { it.opcode == Opcode.IF_EQZ } as BuilderOffsetInstruction
        val note = ImmutableFieldReference(owner, "note", "Ljava/lang/Object;")
        val loaded = ImmutableFieldReference(owner, "loaded", "Z")

        injectPostPolicyExit(method, "newx.test.notes", listOf(note to 0, loaded to 1))

        val head = method.instructions.indexOfFirst {
            it.getReference<StringReference>()?.string == "newx.test.notes"
        }
        assertEquals(head, incoming.target.location.index)
        val guard = method.instructions.drop(head).single { it.opcode == Opcode.IF_EQZ } as BuilderOffsetInstruction
        assertSame(originalReturn, guard.target.location.instruction)
        val invoke = method.instructions.indexOfFirst { it.opcode == Opcode.INVOKE_STATIC }
        assertEquals(Opcode.MOVE_RESULT, method.instructions[invoke + 1].opcode)
        val resultRegister = (method.instructions[invoke + 1] as OneRegisterInstruction).registerA
        assertEquals(resultRegister, (guard as OneRegisterInstruction).registerA)

        val writes = method.instructions.filter { it.opcode in setOf(Opcode.IPUT_OBJECT, Opcode.IPUT_BOOLEAN) }
        assertEquals(listOf(note.toString(), loaded.toString()), writes.map { it.getReference<FieldReference>().toString() })
        writes.forEach {
            val operands = it as TwoRegisterInstruction
            assertTrue(operands.registerA in 0..15 && operands.registerB in 0..15)
            assertTrue(operands.registerA != method.p0Register && operands.registerB != method.p0Register)
        }
        assertTrue(method.instructions.any { it.opcode == Opcode.MOVE_OBJECT_FROM16 })
    }

    @Test
    fun `each disabled policy continues into the next policy`() {
        val method = fixture()
        val originalReturn = method.instructions.last()
        val ids = listOf("newx.test.notes", "newx.test.translate", "newx.test.promote")
        ids.forEachIndexed { index, id ->
            injectPostPolicyExit(method, id, listOf(ImmutableFieldReference(owner, "flag$index", "Z") to 1))
        }
        val instructions = method.instructions
        val heads = ids.map { id -> instructions.indexOfFirst { it.getReference<StringReference>()?.string == id } }
        heads.forEachIndexed { index, head ->
            val end = heads.getOrNull(index + 1) ?: instructions.indexOf(originalReturn)
            val guard = instructions.subList(head, end).single { it.opcode == Opcode.IF_EQZ } as BuilderOffsetInstruction
            assertEquals(end, guard.target.location.index)
        }
    }

    private fun fixture(): MutableMethod {
        val implementation = MethodImplementationBuilder(256)
        implementation.addInstruction("return-void".toInstruction())
        val method = MutableMethod(ImmutableMethod(
            owner, "<init>", emptyList<ImmutableMethodParameter>(), "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), implementation.methodImplementation,
        ))
        val originalReturn = method.instructions.single()
        method.addInstructionsWithLabels(0, """
            invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V
            const/4 v0, 0x0
            if-eqz v0, :exit
            nop
        """.trimIndent(), ExternalLabel("exit", originalReturn))
        return method
    }
}
