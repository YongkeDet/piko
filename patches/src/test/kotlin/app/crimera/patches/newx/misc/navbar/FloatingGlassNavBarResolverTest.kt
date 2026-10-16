package app.crimera.patches.newx.misc.navbar

// Guards the real 12.34 failure: SizeIn(FFFFZ) was mistaken for relative Padding(FFFFZ, inspector).
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FloatingGlassNavBarResolverTest {
    @Test
    fun `divider guard works with every four-bit local live`() {
        // The real Compose bar had v0..v15 live and its first free local was v16.
        val method = fixture("divider", emptyList(), "V",
            "invoke-static/range {v0 .. v15}, Lfixture/Divider;->draw(IIIIIIIIIIIIIIII)V")
        method.guardGlassDivider(0)
        val code = method.implementation!!.instructions
        assertEquals(Opcode.MOVE_RESULT, code[1].opcode)
        assertTrue((code[1] as OneRegisterInstruction).registerA >= 16)
        assertEquals(Opcode.IF_NEZ, code[2].opcode)
        assertEquals(4, (code[2] as BuilderOffsetInstruction).target.location.index)
    }

    @Test
    fun `relative padding rejects the same-shaped SizeIn and absolute padding`() {
        val paddingConstructor = fixture("paddingCtor", emptyList(), "V", "const-string v0, \"Padding must be non-negative\"")
        val sizeConstructor = fixture("sizeCtor", emptyList(), "V", "const-string v0, \"SizeElement\"")
        fun padding(name: String, relative: Int, owner: String, extra: String) = fixture(
            name, listOf("Landroidx/compose/ui/Modifier;", "F", "F", "F", "F"), "Landroidx/compose/ui/Modifier;",
            "const/4 v5, $relative",
            "invoke-direct/range {v0 .. v6}, Lfixture/$owner;-><init>(FFFFZ$extra)V",
        )
        val relative = padding("relative", 1, "Padding", "Lkotlin/jvm/functions/Function1;")
        val absolute = padding("absolute", 0, "Padding", "Lkotlin/jvm/functions/Function1;")
        val size = padding("size", 1, "Size", "")
        val lookup = { reference: com.android.tools.smali.dexlib2.iface.reference.MethodReference ->
            if (reference.definingClass == "Lfixture/Padding;") paddingConstructor else sizeConstructor
        }
        assertEquals(relative, resolveGlassPadding(listOf(size, absolute, relative), lookup))
        assertFailsWith<PatchException> { resolveGlassPadding(listOf(size, absolute), lookup) }
        assertFailsWith<PatchException> { resolveGlassPadding(listOf(relative, relative), lookup) }
    }

    private fun fixture(name: String, parameters: List<String>, result: String, vararg lines: String): MutableMethod {
        val implementation = MethodImplementationBuilder(22)
        lines.forEach { implementation.addInstruction(it.toInstruction()) }
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(ImmutableMethod(
            "Lfixture/Glass;", name,
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, result,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(),
            implementation.methodImplementation,
        ))
    }
}
