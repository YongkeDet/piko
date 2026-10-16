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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileBulkHostResolverTest {
    private val paginator = "Lcom/x/urt/paging/bottom/RenamedPaginator;"
    private val contract = "Lcom/x/urt/paging/RenamedContract;"

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
}
