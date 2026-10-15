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
import app.crimera.patches.newx.models.booleanModelFields
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
    fun `R8 boolean append helper keeps label and field order`() {
        val helper = bytecodeMethod("Lapp/crimera/test/AppendHelper;", "append", listOf(
            "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/StringBuilder;", "Z", "Z",
        ), "V", 5, true, """
            invoke-virtual {p2, p3}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
            invoke-virtual {p2, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            invoke-virtual {p2, p4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
            invoke-virtual {p2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            return-void
        """.trimIndent())
        val model = bytecodeMethod(owner, "toString", emptyList(), "Ljava/lang/String;", 6, false, """
            new-instance v0, Ljava/lang/StringBuilder;
            invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
            const-string v1, ", loaded="
            invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            const-string v1, ", translatable="
            const-string v2, ", language="
            iget-boolean v3, p0, $owner->loaded:Z
            iget-boolean v4, p0, $owner->translatable:Z
            invoke-static {v1, v2, v0, v3, v4}, Lapp/crimera/test/AppendHelper;->append(Ljava/lang/String;Ljava/lang/String;Ljava/lang/StringBuilder;ZZ)V
            invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
            move-result-object v0
            return-object v0
        """.trimIndent())
        val fields = booleanModelFields(model) { helper }
        assertEquals(mapOf(
            ", loaded=" to "$owner->loaded:Z",
            ", translatable=" to "$owner->translatable:Z",
        ), fields.mapValues { it.value.toString() })
    }

    @Test
    fun `every normal serialization constructor exit applies the policy`() {
        val method = fixture()
        method.addInstructionsWithLabels(method.instructions.lastIndex, "return-void")
        injectPostPolicyExit(method, "newx.test.multiple_exits", listOf(ImmutableFieldReference(owner, "flag", "Z") to 1))
        assertEquals(2, method.instructions.count { it.opcode == Opcode.IPUT_BOOLEAN })
    }

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

    private fun bytecodeMethod(type: String, name: String, parameters: List<String>, result: String, registers: Int, static: Boolean, body: String): MutableMethod {
        val implementation = MethodImplementationBuilder(registers)
        val method = MutableMethod(ImmutableMethod(type, name,
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, result,
            AccessFlags.PUBLIC.value or if (static) AccessFlags.STATIC.value else 0,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), implementation.methodImplementation,
        ))
        method.addInstructionsWithLabels(0, body)
        return method
    }
}
