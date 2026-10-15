package app.crimera.patches.newx.misc.mediaquality

// Catches 12.34's GOTO between the half-height division and the shared Media3 setter.
// Also verifies that disabling the policy still executes the original division.
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderPackedSwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PreferredVideoResolutionTest {
    @Test
    fun `height guard supports direct and shared setters while retaining the disabled path`() {
        for (merge in listOf(false, true)) for (mergedArm in listOf(false, true)) {
            val method = fixture(merge, mergedArm = mergedArm)
            val division = method.instructions.single { it.opcode == Opcode.DIV_INT_2ADDR }
            val continuation = method.instructions[method.instructions.indexOf(division) + 1]
            preserveSelectedVideoHeight(method, "newx.test.highest_video", 26)
            val instructions = method.instructions
            val disabled = instructions.single { it.opcode == Opcode.IF_EQZ } as BuilderOffsetInstruction
            assertSame(division, disabled.target.location.instruction)
            val enabled = instructions[instructions.indexOf(disabled) + 1] as BuilderOffsetInstruction
            assertSame(continuation, enabled.target.location.instruction)
            val invokeIndex = instructions.indexOf(disabled) - 2
            assertEquals(Opcode.INVOKE_STATIC_RANGE, instructions[invokeIndex].opcode)
            assertEquals(Opcode.MOVE_RESULT, instructions[invokeIndex + 1].opcode)
            assertEquals((instructions[invokeIndex + 1] as OneRegisterInstruction).registerA,
                (disabled as OneRegisterInstruction).registerA)
        }
        assertFailsWith<PatchException> { preserveSelectedVideoHeight(fixture(true, 3), "test", 26) }
    }

    private fun fixture(sharedSetter: Boolean, divisor: Int = 2, mergedArm: Boolean = false): MutableMethod {
        val implementation = MethodImplementationBuilder(26)
        val method = MutableMethod(ImmutableMethod("Lapp/crimera/test/VideoPolicy;", "apply",
            emptyList(), "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(), emptySet(), implementation.methodImplementation))
        method.addInstructionsWithLabels(0, """
            const/4 v3, $divisor
            invoke-virtual {v14}, Ljava/lang/Integer;->intValue()I
            move-result v15
            div-int/2addr v15, v3
            ${if (sharedSetter) "goto :setter\nmove v15, v5\n:setter" else ""}
            invoke-virtual {v1, v15}, Landroidx/media3/common/TrackSelectionParametersBuilder;->minimumHeight(I)V
            return-void
        """.trimIndent())
        if (mergedArm) {
            // A neighboring switch arm overwrites v3 and returns, exactly like the merged
            // coroutine in 12.34. That writer cannot reach the video-height division.
            val body = method.implementation!!
            val selected = body.newLabelForIndex(1)
            val payloadIndex = method.instructions.size
            body.addInstruction(BuilderPackedSwitchPayload(0, listOf(selected)))
            val payload = body.newLabelForIndex(payloadIndex)
            body.addInstruction(1, BuilderInstruction31t(Opcode.PACKED_SWITCH, 0, payload))
            body.addInstruction(2, BuilderInstruction11n(Opcode.CONST_4, 3, 3))
            body.addInstruction(3, BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        return method.cloneMutable(additionalRegisters = 2)
    }
}
