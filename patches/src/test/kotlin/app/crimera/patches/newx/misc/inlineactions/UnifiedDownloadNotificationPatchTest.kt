package app.crimera.patches.newx.misc.inlineactions

// Prevents supplying the wrong Kotlin default-mask bit after R8 removes the action parameter,
// and prevents a consumed progress event from falling through into the native message queue.
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.methodReference
import app.morphe.patcher.patch.PatchException
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
import kotlin.test.assertFailsWith
import app.morphe.util.registersUsed

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

    @Test
    fun `full banner text keeps wide operands and native fallback while expanding only owned notices`() {
        val body = MethodImplementationBuilder(30)
        fun add(s: String) { body.addInstruction(s.toInstruction()) }
        add("move-object/from16 v1, v27")
        add("iget-object v10, v1, Lfixture/Notice;->title:Landroidx/compose/ui/text/AnnotatedString;")
        add("move-object/from16 v18, v28")
        add("const/4 v14, 0x2"); add("const/4 v15, 0x0"); add("const/16 v16, 0x1"); add("const/16 v17, 0x1")
        add("invoke-static/range {v10 .. v19}, Landroidx/compose/material3/Fixture;->rich(Landroidx/compose/ui/text/AnnotatedString;Landroidx/compose/ui/Modifier;JIZIILandroidx/compose/runtime/Composer;I)V")
        add("iget-object v10, v1, Lfixture/Notice;->detail:Ljava/lang/String;")
        add("invoke-static/range {v10 .. v19}, Landroidx/compose/material3/Fixture;->text(Ljava/lang/String;Landroidx/compose/ui/Modifier;JIZIILandroidx/compose/runtime/Composer;I)V")
        add("invoke-static {v1}, Lfixture/Sink;->keep(Ljava/lang/Object;)V"); add("return-void")
        val method = fixture("caption", listOf("Lfixture/Notice;", "Landroidx/compose/runtime/Composer;", "I"), "V", body, true)
        val fallback = fixture("captionFallback", listOf("Lfixture/Notice;", "Landroidx/compose/runtime/Composer;", "I"), "V", body, true)
        // Real APK regression: modern and fallback layouts each have a rich headline and detail.
        val renderers = resolveBannerRenderers(listOf(method, fallback).flatMap { bannerTextCalls(it, "Lfixture/Notice;") })
        assertEquals(2, renderers.size)
        val site = renderers.single { it.detail.method === method }.detail
        assertEquals(listOf(1, 18, 14, 15, 16), listOf(site.model, site.composer, site.overflow, site.wrap, site.max))
        method.expandBannerText(site)
        val code = method.implementation!!.instructions
        val gate = code.indexOfFirst { it.getReference<MethodReference>()?.name == "fullContent" }
        assertEquals(listOf(1), code[gate].registersUsed.toList())
        assertEquals(Opcode.MOVE_RESULT, code[gate + 1].opcode)
        val skip = code[gate + 2] as BuilderOffsetInstruction
        assertEquals(Opcode.IF_EQZ, skip.opcode)
        val original = code[skip.target.location.index]
        assertEquals("text", original.getReference<MethodReference>()?.name)
        assertEquals((10..19).toList(), original.registersUsed.toList())
        assertEquals(listOf(14, 15, 16), code.subList(gate + 3, gate + 6).map { (it as OneRegisterInstruction).registerA })
        assertEquals(listOf(1, 1, Int.MAX_VALUE), code.subList(gate + 3, gate + 6).map { (it as com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction).narrowLiteral })
        assertEquals(30, method.implementation!!.registerCount)
        // R8 reusing the model alias cannot silently expand an unrelated object.
        method.implementation!!.addInstruction(2, "const/4 v1, 0x0".toInstruction())
        assertFailsWith<PatchException> { bannerTextCalls(method, "Lfixture/Notice;") }
    }

    @Test
    fun `native progress bridge preserves wide colors and chooses animated unknown total`() {
        val function = "Lkotlin/jvm/functions/Function0;"
        val modifier = "Landroidx/compose/ui/Modifier;"
        val composer = "Landroidx/compose/runtime/Composer;"
        val linearTypes = listOf(function, modifier, "J", "J", "I", "F", "Lkotlin/jvm/functions/Function1;", composer, "I", "I")
        val linearBody = MethodImplementationBuilder(30)
        linearBody.addInstruction("and-int/lit8 v0, v29, 0x10".toInstruction()); linearBody.addInstruction("return-void".toInstruction())
        val linear = fixture("linear", linearTypes, "V", linearBody, true)
        val unknownTypes = listOf("F", "I", "I", "J", "J", composer, modifier)
        val unknownBody = MethodImplementationBuilder(20)
        unknownBody.addInstruction("and-int/lit8 v0, v13, 0x6".toInstruction()); unknownBody.addInstruction("return-void".toInstruction())
        val unknown = fixture("unknown", unknownTypes, "V", unknownBody, true)
        val bridgeBody = MethodImplementationBuilder(18); bridgeBody.addInstruction("return-void".toInstruction())
        val bridge = fixture("draw", listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "Z"), "V", bridgeBody, true)
        bridge.emitNativeProgress(linear, unknown, methodReference("Lfixture/Color;->argb(I)J"),
            methodReference("Lfixture/Width;->fill(Landroidx/compose/ui/Modifier;F)Landroidx/compose/ui/Modifier;"),
            fieldReference("Lfixture/Empty;->a:Lfixture/Empty;"))
        val code = bridge.implementation!!.instructions
        val known = code.indexOfFirst { it.getReference<MethodReference>()?.name == "linear" }
        val animated = code.indexOfFirst { it.getReference<MethodReference>()?.name == "unknown" }
        assertEquals((0..11).toList(), code[known].registersUsed.toList())
        assertEquals((0..8).toList(), code[animated].registersUsed.toList())
        val branch = code.indexOfFirst { it.opcode == Opcode.IF_NEZ }
        assertTrue((code[branch] as BuilderOffsetInstruction).target.location.index in (known + 1)..animated)
        assertEquals(4, code.count { it.getReference<MethodReference>()?.name == "argb" })
        code.indices.filter { code[it].getReference<MethodReference>()?.name == "argb" }.forEach {
            assertEquals(Opcode.MOVE_RESULT_WIDE, code[it + 1].opcode)
        }
        assertEquals(18, bridge.implementation!!.registerCount)
    }

    private fun fixture(name: String, types: List<String>, result: String, body: MethodImplementationBuilder, static: Boolean) = MutableMethod(ImmutableMethod(
        "Lfixture/Manager;", name, types.map { ImmutableMethodParameter(it, emptySet(), null) }, result,
        AccessFlags.PUBLIC.value or if (static) AccessFlags.STATIC.value else AccessFlags.CONSTRUCTOR.value,
        emptySet(), emptySet(), body.methodImplementation,
    ))
}
