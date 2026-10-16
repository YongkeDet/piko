package app.crimera.patches.newx.misc.mediatab

// Reproduces the exact 12.34 failure: the real URT component and two coroutine captures all
// hold the bottom paginator. Field presence alone returned three candidates and blocked patching.
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileBulkHostResolverTest {
    private val paginator = "Lcom/x/urt/paging/bottom/RenamedPaginator;"
    private val contract = "Lcom/x/urt/paging/RenamedContract;"

    @Test
    fun `empty state and native refresh objects resolve by contract and label across renames`() {
        val state = "Lcom/x/urt/RenamedState;"
        val events = "Lcom/x/urt/RenamedEvent;"
        val unrelated = singleton("Lother/Empty;", "Lother/State;", "Empty")
        for (name in listOf("FirstRename", "SecondRename")) {
            val empty = singleton("Lcom/x/urt/$name;", state, "Empty")
            val refresh = singleton("Lcom/x/urt/Refresh$name;", events, "RefreshTimeline")
            val classes = listOf(unrelated, empty, refresh)
            assertEquals(empty.type, resolveBulkSingleton(classes, state, "Empty").definingClass)
            assertEquals(refresh.type, resolveBulkSingleton(classes, events, "RefreshTimeline").definingClass)
            assertFailsWith<PatchException> { resolveBulkSingleton(classes, state, "RefreshTimeline") }
            assertFailsWith<PatchException> {
                resolveBulkSingleton(classes + singleton("Lcom/x/urt/Duplicate;", state, "Empty"), state, "Empty")
            }
        }
    }

    @Test
    fun `host getter excludes suspend and zero-argument captures and fails closed on real ambiguity`() {
        val suspendA = fixture("Lcom/x/urt/paging/bottom/StateCollector;", "invokeSuspend", "Ljava/lang/Object;", true)
        val suspendB = fixture("Lcom/x/urt/paging/bottom/NetworkCollector;", "invokeSuspend", "Ljava/lang/Object;", true)
        val zeroArg = fixture("Lcom/x/urt/ui/Callback;", "invoke", "Ljava/lang/Object;", false)
        val captures = listOf(suspendA, suspendB, zeroArg)
        for (name in listOf("Alpha", "Production", "AnotherRename")) {
            val host = fixture("Lcom/x/urt/$name;", "renamedGetter", contract, false)
            assertSame(host, resolveBulkTimelineHost(captures + host, paginator, contract))
        }
        assertFailsWith<PatchException> { resolveBulkTimelineHost(captures, paginator, contract) }
        val first = fixture("Lcom/x/urt/First;", "one", contract, false)
        val second = fixture("Lcom/x/urt/Second;", "two", contract, false)
        val ambiguous = assertFailsWith<PatchException> {
            resolveBulkTimelineHost(captures + first + second, paginator, contract)
        }
        assertTrue(ambiguous.message.orEmpty().contains("found 2"))
    }

    private fun fixture(owner: String, name: String, result: String, argument: Boolean): ImmutableClassDef {
        val parameters = if (argument) listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)) else emptyList()
        val implementation = MethodImplementationBuilder(if (argument) 3 else 2)
        val method = MutableMethod(ImmutableMethod(owner, name, parameters, result, AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), implementation.methodImplementation))
        method.addInstructionsWithLabels(0, """
            iget-object v0, p0, $owner->captured:$paginator
            return-object v0
        """.trimIndent())
        return ImmutableClassDef(owner, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
            emptySet(), listOf(ImmutableField(owner, "captured", paginator, AccessFlags.PUBLIC.value,
                null, emptySet(), emptySet())), listOf(method))
    }

    private fun singleton(owner: String, contract: String, label: String): ImmutableClassDef {
        val method = MutableMethod(ImmutableMethod(owner, "toString", emptyList(), "Ljava/lang/String;",
            AccessFlags.PUBLIC.value, emptySet(), emptySet(), MethodImplementationBuilder(2).methodImplementation))
        method.addInstructionsWithLabels(0, """
            const-string v0, "$label"
            return-object v0
        """.trimIndent())
        return ImmutableClassDef(owner, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", listOf(contract), null,
            emptySet(), listOf(ImmutableField(owner, "renamedInstance", owner,
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
                null, emptySet(), emptySet())), listOf(method))
    }
}
