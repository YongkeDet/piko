package app.crimera.patches.newx.misc.inlineactions

// Prevents supplying the wrong Kotlin default-mask bit after R8 removes the action parameter,
// and prevents a consumed progress event from falling through into the native message queue.
import app.crimera.bytecode.methodReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedDownloadNotificationPatchTest {
    @Test
    fun `constructor mask follows actual bit tests instead of surviving parameter ordinal`() {
        val body = MethodImplementationBuilder(14)
        fun add(s: String) { body.addInstruction(s.toInstruction()) }
        add("and-int/lit8 v0, v13, 16")
        body.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, body.getLabel("style")))
        add("const/4 v1, 0"); add("move-object v7, v1")
        body.addLabel("style"); add("and-int/lit16 v0, v13, 128")
        body.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, body.getLabel("priority")))
        add("sget-object v9, Lfixture/Style;->Default:Lfixture/Style;")
        body.addLabel("priority"); add("and-int/lit16 v0, v13, 256")
        body.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, body.getLabel("end")))
        add("sget-object v10, Lfixture/Priority;->Normal:Lfixture/Priority;")
        body.addLabel("end"); add("return-void")
        val constructor = fixture("<init>", List(10) { "Ljava/lang/Object;" } + "I", "V", body, false)
        assertEquals(16, notificationDefaultBit(constructor, 4))
        assertEquals(128, notificationDefaultBit(constructor, 6))
        assertEquals(256, notificationDefaultBit(constructor, 7))
    }

    @Test
    fun `actor consumes only decoded Show events and branches through the original loop continuation`() {
        val body = MethodImplementationBuilder(12)
        fun add(s: String) { body.addInstruction(s.toInstruction()) }
        body.addLabel("loop")
        add("invoke-interface {v0}, Lfixture/Channel;->next()Ljava/lang/Object;")
        add("move-result-object v11")
        add("check-cast v11, Lfixture/Show;")
        add("invoke-virtual {v11}, Lfixture/Show;->entry()Lfixture/Entry;")
        add("move-result-object v11")
        add("invoke-virtual {v10}, Lfixture/Manager;->enabled()Z")
        add("move-result v6")
        body.addInstruction(BuilderInstruction21t(Opcode.IF_NEZ, 6, body.getLabel("busy")))
        add("invoke-virtual {v10, v11}, Lfixture/Manager;->present(Lfixture/Entry;)V")
        body.addInstruction(BuilderInstruction10t(Opcode.GOTO, body.getLabel("loop")))
        body.addLabel("busy"); add("return-object v2")
        val actor = fixture("actor", listOf("Lfixture/Manager;", "Lkotlin/coroutines/Continuation;"), "Ljava/lang/Object;", body, true)
        actor.interceptNotificationActor(methodReference("Lfixture/Show;->entry()Lfixture/Entry;"), methodReference("Lfixture/Manager;->present(Lfixture/Entry;)V"))
        val code = actor.implementation!!.instructions
        val hook = code.indexOfFirst { it.getReference<MethodReference>()?.name == "interceptNative" }
        assertEquals(5, hook)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, code[hook - 1].opcode)
        assertEquals(Opcode.MOVE_RESULT, code[hook + 1].opcode)
        assertEquals((code[hook + 1] as OneRegisterInstruction).registerA, (code[hook + 2] as OneRegisterInstruction).registerA)
        val consumed = code[hook + 2] as BuilderOffsetInstruction
        assertEquals(Opcode.IF_NEZ, consumed.opcode)
        val continuation = code[consumed.target.location.index] as BuilderOffsetInstruction
        assertEquals(Opcode.GOTO, continuation.opcode)
        assertEquals(0, continuation.target.location.index)
        assertTrue(code[hook + 3].getReference<MethodReference>()?.name == "enabled")
    }

    @Test
    fun `state read returns only owned text and preserves the original localizer branch`() {
        val body = MethodImplementationBuilder(3)
        body.addInstruction("invoke-static {v1, v2}, Lfixture/Text;->localize(Ljava/lang/Object;Landroidx/compose/runtime/Composer;)Ljava/lang/String;".toInstruction())
        body.addInstruction("move-result-object v1".toInstruction())
        body.addInstruction("return-object v1".toInstruction())
        val method = fixture("text", listOf("Ljava/lang/Object;", "Landroidx/compose/runtime/Composer;"), "Ljava/lang/String;", body, true)
        method.interceptProgressText("Ljava/lang/Object;")
        val code = method.implementation!!.instructions
        assertEquals("progressText", code[0].getReference<MethodReference>()?.name)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, code[1].opcode)
        assertEquals(Opcode.IF_EQZ, code[2].opcode)
        assertEquals(4, (code[2] as BuilderOffsetInstruction).target.location.index)
        assertEquals(Opcode.RETURN_OBJECT, code[3].opcode)
        assertEquals("localize", code[4].getReference<MethodReference>()?.name)
        assertEquals(3, method.implementation!!.registerCount)
    }

    @Test
    fun `lifecycle observes publication before the receiver is reused by R8`() {
        val body = MethodImplementationBuilder(3)
        body.addInstruction("invoke-virtual {v0, v1}, Lkotlinx/coroutines/flow/Fixture;->publish(Ljava/lang/Object;)V".toInstruction())
        body.addInstruction("const/4 v1, 0x3".toInstruction()) // p0 is overwritten here.
        body.addInstruction("return-void".toInstruction())
        val method = fixture("dispose", listOf("Ljava/lang/Object;"), "V", body, false)
        method.observeNotificationPublication()
        val code = method.implementation!!.instructions
        assertEquals("publish", code[0].getReference<MethodReference>()?.name)
        assertEquals("observeNative", code[1].getReference<MethodReference>()?.name)
        assertEquals(Opcode.CONST_4, code[2].opcode)
        assertEquals(1, (code[2] as OneRegisterInstruction).registerA)
        assertEquals(3, method.implementation!!.registerCount)
    }

    private fun fixture(name: String, types: List<String>, result: String, body: MethodImplementationBuilder, static: Boolean) = MutableMethod(ImmutableMethod(
        "Lfixture/Manager;", name, types.map { ImmutableMethodParameter(it, emptySet(), null) }, result,
        AccessFlags.PUBLIC.value or if (static) AccessFlags.STATIC.value else AccessFlags.CONSTRUCTOR.value,
        emptySet(), emptySet(), body.methodImplementation,
    ))
}
