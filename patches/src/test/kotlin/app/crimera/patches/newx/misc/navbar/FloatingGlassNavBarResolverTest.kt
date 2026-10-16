package app.crimera.patches.newx.misc.navbar

// Guards the real 12.34 failure: SizeIn(FFFFZ) was mistaken for relative Padding(FFFFZ, inspector).
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FloatingGlassNavBarResolverTest {
    @Test
    fun `fixed bar traces the scroll state and preserves the outer hide branch`() {
        val body = MethodImplementationBuilder(22)
        fun add(line: String) { body.addInstruction(line.toInstruction()) }
        body.addInstruction(BuilderInstruction21t(Opcode.IF_NEZ, 8, body.getLabel("hide")))
        add("invoke-interface {v4}, Lfixture/State;->getValue()Ljava/lang/Object;")
        add("move-result-object v11")
        add("check-cast v11, Ljava/lang/Boolean;")
        add("invoke-virtual {v11}, Ljava/lang/Boolean;->booleanValue()Z")
        add("move-result v11")
        body.addInstruction(BuilderInstruction21t(Opcode.IF_NEZ, 11, body.getLabel("show")))
        body.addLabel("hide")
        add("const/4 v12, 0")
        body.addLabel("show")
        add("invoke-direct {v0, v4, v5}, Lfixture/Glass;-><init>(Lfixture/State;Ljava/lang/Object;)V")
        add("return-void")
        val host = fixture("host", emptyList(), "V", body)
        val constructorBody = MethodImplementationBuilder(3)
        constructorBody.addInstruction("iput-object v1, v0, Lfixture/Glass;->visible:Lfixture/State;".toInstruction())
        constructorBody.addInstruction("return-void".toInstruction())
        val constructor = MutableMethod(ImmutableMethod("Lfixture/Glass;", "<init>",
            listOf("Lfixture/State;", "Ljava/lang/Object;").map { ImmutableMethodParameter(it, emptySet(), null) }, "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, emptySet(), emptySet(), constructorBody.methodImplementation))
        val getter = fixture("isVisible", emptyList(), "Z",
            "iget-object v0, v1, Lfixture/Glass;->visible:Lfixture/State;",
            "invoke-interface {v0}, Lfixture/State;->getValue()Ljava/lang/Object;")
        val read = resolveBottomBarScrollRead(host, constructor, getter)
        assertEquals(BottomBarScrollRead(5, 11), read)
        host.pinBottomBarScrollRead(read)
        val code = host.implementation!!.instructions
        assertEquals(Opcode.INVOKE_STATIC, code[6].opcode)
        assertEquals(Opcode.MOVE_RESULT, code[7].opcode)
        assertEquals(11, (code[7] as OneRegisterInstruction).registerA)
        val hide = (code[0] as BuilderOffsetInstruction).target.location.index
        assertEquals(9, hide)
        assertEquals(Opcode.CONST_4, code[hide].opcode)
        assertEquals(Opcode.IF_NEZ, code[8].opcode)
        // A reused local that no longer reaches the state constructor must fail closed.
        val wrong = fixture("wrong", emptyList(), "V",
            "invoke-interface {v4}, Lfixture/State;->getValue()Ljava/lang/Object;",
            "move-result-object v11", "check-cast v11, Ljava/lang/Boolean;",
            "invoke-virtual {v11}, Ljava/lang/Boolean;->booleanValue()Z", "move-result v11",
            "const/4 v4, 0",
            "invoke-direct {v0, v4, v5}, Lfixture/Glass;-><init>(Lfixture/State;Ljava/lang/Object;)V")
        assertFailsWith<PatchException> { resolveBottomBarScrollRead(wrong, constructor, getter) }
    }

    @Test
    fun `inset decoration precedes both backgrounds and preserves their shared join label`() {
        val body = MethodImplementationBuilder(22)
        fun add(line: String) { body.addInstruction(line.toInstruction()) }
        add("invoke-static {v0}, Lfixture/Insets;->padding(Ljava/lang/Object;)Ljava/lang/Object;")
        add("move-result-object v1")
        add("const/4 v2, 0")
        body.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 2, body.getLabel("solid")))
        add("const/4 v3, 0")
        body.addInstruction(BuilderInstruction10t(Opcode.GOTO, body.getLabel("join")))
        body.addLabel("solid")
        add("const/4 v3, 0")
        body.addLabel("join")
        add("invoke-interface {v1, v3}, Landroidx/compose/ui/Modifier;->then(Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;")
        add("move-result-object v1")
        add("return-void")
        val method = fixture("diamond", emptyList(), "V", body)
        method.decorateGlassInsets(resultIndex = 1, register = 1)
        val code = method.implementation!!.instructions
        assertEquals(Opcode.INVOKE_STATIC, code[2].opcode)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, code[3].opcode)
        assertEquals(Opcode.CHECK_CAST, code[4].opcode)
        assertEquals(Opcode.IF_EQZ, code[6].opcode)
        val join = (code[8] as BuilderOffsetInstruction).target.location.index
        assertEquals(10, join)
        assertEquals(Opcode.INVOKE_INTERFACE, code[join].opcode)
    }

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
        return fixture(name, parameters, result, implementation)
    }

    private fun fixture(name: String, parameters: List<String>, result: String, implementation: MethodImplementationBuilder): MutableMethod {
        return MutableMethod(ImmutableMethod(
            "Lfixture/Glass;", name,
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, result,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(),
            implementation.methodImplementation,
        ))
    }
}
