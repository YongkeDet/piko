package app.crimera.patches.newx.misc.postdetails

// Guards the 12.29.0-alpha.04 gesture-pill regression. Container classification preserves the
// existing inset contract, while the emitted-bytecode check proves the immersive likes/repost/share
// row receives navigation padding before it renders rather than mutating the reply editor.

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderPackedSwitchPayload
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HidePostReplyBarPatchTest {
    @Test
    fun `inset application classification preserves the gesture reservation contract`() {
        val gated = diamondContainer(skipArmInstruction = "const/4 v2, 0x0")
        assertEquals(
            InsetApplicationKind.GATED,
            gated.classifyInsetApplication(callIndex = 1),
            "gated insets (immersive photo screen) must keep their gesture reservation",
        )

        val unconditional = linearContainer()
        assertEquals(
            InsetApplicationKind.UNCONDITIONAL,
            unconditional.classifyInsetApplication(callIndex = 0),
            "unconditional legacy insets must stay removable with the reply bar",
        )

        assertFailsWith<PatchException> {
            diamondContainer(skipArmInstruction = "const/4 v1, 0x0")
                .classifyInsetApplication(callIndex = 1)
        }
    }

    @Test
    fun `immersive action row receives navigation padding before it renders`() {
        val method = immersiveActionBarFixture()
        val actionBarCall = method.instructions.first()
        val holderProvider =
            ImmutableMethodReference(
                "Lapp/crimera/test/WindowInsetsHolder;",
                "current",
                listOf("Landroidx/compose/runtime/Composer;"),
                "Lapp/crimera/test/WindowInsetsHolder;",
            )
        val navigationBarsField =
            ImmutableFieldReference(
                "Lapp/crimera/test/WindowInsetsHolder;",
                "navigationBarsIgnoringVisibility",
                "Lapp/crimera/test/WindowInsets;",
            )
        val insetsPadding =
            ImmutableMethodReference(
                "Landroidx/compose/foundation/layout/TestLayout;",
                "windowInsetsPadding",
                listOf(
                    "Landroidx/compose/ui/Modifier;",
                    "Lapp/crimera/test/WindowInsets;",
                ),
                "Landroidx/compose/ui/Modifier;",
            )
        val hook = ImmersiveActionBarSafeAreaHook(
                method = method,
                actionBarCallIndex = 0,
                actionBarCall = actionBarCall,
                modifierRegister = 16,
                composerRegister = 22,
                windowInsetsHolderProvider = holderProvider,
                navigationBarsField = navigationBarsField,
                insetsPaddingCall = insetsPadding,
        )
        val bridgeImplementation = MethodImplementationBuilder(8)
        bridgeImplementation.addInstruction("return-object v6".toInstruction())
        val bridge = MutableMethod(ImmutableMethod(
            "Lapp/morphe/extension/newx/misc/ReplyBarInsets;", "apply",
            List(2) { ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null) },
            "Ljava/lang/Object;", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), bridgeImplementation.methodImplementation,
        ))
        emitImmersiveInsetsBridge(bridge, "newx.test.hide_post_reply_bar", hook)
        val prepared = reserveImmersiveInsetsCall(hook)
        applyImmersiveActionBarSafeAreaHook(prepared)

        val instructions = bridge.instructions
        val insetFieldIndex = instructions.indexOfFirst { instruction ->
            instruction.getReference<FieldReference>() == navigationBarsField
        }
        val paddingCallIndex = instructions.indexOfFirst { instruction ->
            instruction.getReference<MethodReference>() == insetsPadding
        }
        assertEquals(true, insetFieldIndex >= 0)
        assertEquals(true, paddingCallIndex > insetFieldIndex)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[paddingCallIndex + 1].opcode)
        val caller = prepared.method.instructions
        val bridgeCall = caller.indexOfFirst { it.getReference<MethodReference>()?.definingClass == bridge.definingClass }
        assertEquals(true, bridgeCall in 0 until caller.indexOf(prepared.actionBarCall))
        assertEquals(
            16,
            (caller[bridgeCall + 1] as OneRegisterInstruction).registerA,
            "the padded Modifier must replace the action row's Modifier register",
        )
    }

    /** `if-eqz v0, :skip` / apply arm (`invoke` + `move-result-object v1`) / `goto :merge` / skip arm / merge. */
    private fun diamondContainer(skipArmInstruction: String): MutableMethod {
        val method = fixture()
        val returnInstruction = method.instructions[0]
        method.addInstructionsWithLabels(0, skipArmInstruction)
        val skipInstruction = method.instructions[0]
        method.addInstructionsWithLabels(0, "goto :merge", ExternalLabel("merge", returnInstruction))
        method.addInstructionsWithLabels(
            0,
            """
            invoke-static {}, Lapp/crimera/test/Fixture;->insets()Landroidx/compose/ui/Modifier;
            move-result-object v1
            """.trimIndent(),
        )
        method.addInstructionsWithLabels(0, "if-eqz v0, :skip", ExternalLabel("skip", skipInstruction))
        return method
    }

    /** `invoke` + `move-result-object v1` with no gate before the call. */
    private fun linearContainer(): MutableMethod {
        val method = fixture()
        method.addInstructionsWithLabels(
            0,
            """
            invoke-static {}, Lapp/crimera/test/Fixture;->insets()Landroidx/compose/ui/Modifier;
            move-result-object v1
            """.trimIndent(),
        )
        return method
    }

    private fun immersiveActionBarFixture(): MutableMethod {
        val method = fixture(registerCount = 32)
        // All four-bit registers are live at the action-row call on 12.34. The old inline
        // iget-based implementation failed here while looking for a four-bit scratch register.
        val consumeSignature = "Ljava/lang/Object;".repeat(32)
        method.addInstructionsWithLabels(
            0,
            """
                invoke-static/range {v16 .. v24}, Lapp/crimera/test/ActionBar;->renderActionBar(Landroidx/compose/ui/Modifier;IIIIILandroidx/compose/runtime/Composer;II)V
                invoke-static/range {v0 .. v31}, Lapp/crimera/test/Sink;->consume($consumeSignature)V
            """.trimIndent(),
        )
        // The allocator intentionally stops at a switch. A fresh but unused local therefore
        // cannot be discovered by scratchRegister, even after growing this frame.
        val implementation = method.implementation!!
        val exit = implementation.newLabelForIndex(method.instructions.lastIndex)
        val payloadIndex = method.instructions.size
        implementation.addInstruction(BuilderPackedSwitchPayload(0, listOf(exit)))
        val payload = implementation.newLabelForIndex(payloadIndex)
        implementation.addInstruction(2, BuilderInstruction31t(Opcode.PACKED_SWITCH, 0, payload))
        return method
    }

    private fun fixture(registerCount: Int = 3): MutableMethod {
        val implementation = MethodImplementationBuilder(registerCount)
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/HidePostReplyBarFixture;",
                "fixture",
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }
}
