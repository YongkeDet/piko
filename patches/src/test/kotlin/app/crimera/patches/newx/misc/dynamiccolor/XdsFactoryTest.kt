package app.crimera.patches.newx.misc.dynamiccolor

// Guards the 12.34 theme failure: R8 reuses p1 for later colors, and the singleton factory
// calls a mask wrapper instead of directly calling the constructor which owns the fields.
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XdsFactoryTest {
    private val owner = "Lapp/crimera/test/ColorScheme;"

    @Test
    fun `reused first-parameter register does not identify later colors as the background`() {
        val constructor = fixture(listOf("J", "J"), 5, """
            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            iput-wide p1, p0, $owner->background:J
            move-wide p1, p3
            iput-wide p1, p0, $owner->foreground:J
            return-void
        """.trimIndent())
        assertTrue(constructor.assignsFirstColorParameter(ImmutableFieldReference(owner, "background", "J")))
        assertFalse(constructor.assignsFirstColorParameter(ImmutableFieldReference(owner, "foreground", "J")))
    }

    @Test
    fun `default-mask wrapper forwards the unmasked first color to the concrete constructor`() {
        val constructor = fixture(listOf("J", "J"), 5, """
            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        """.trimIndent())
        val wrapper = fixture(listOf("J", "J", "I"), 10, """
            move-object v0, p0
            and-int/lit8 v3, p5, 0x1
            if-eqz v3, :unmasked
            const-wide/16 v1, 0x0
            goto :join
            :unmasked
            move-wide v1, p1
            :join
            move-wide v3, p3
            invoke-direct/range {v0 .. v4}, $owner-><init>(JJ)V
            return-void
        """.trimIndent())
        assertTrue(wrapper.delegatesFirstColorTo(constructor))
    }

    private fun fixture(parameters: List<String>, registers: Int, body: String): MutableMethod {
        val method = MutableMethod(ImmutableMethod(owner, "<init>",
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), MethodImplementationBuilder(registers).methodImplementation,
        ))
        method.addInstructionsWithLabels(0, body)
        return method
    }
}
