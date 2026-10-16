package app.crimera.patches.newx.misc.navbar

// Guards the real 12.34 failure: SizeIn(FFFFZ) was mistaken for relative Padding(FFFFZ, inspector).
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FloatingGlassNavBarResolverTest {
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
        val implementation = MethodImplementationBuilder(12)
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
