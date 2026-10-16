package app.crimera.patches.newx.misc.mediatab

import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.misc.inlineactions.newXInlineDownloadButtonPatch
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.modelFieldsByAppendOrder
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.timeline.NewXTimelineSuccessFingerprint
import app.crimera.patches.newx.timeline.newXTimelineFilterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION = "Lapp/morphe/extension/newx/misc/ProfileBulkDownload;"
private const val OBJECT = "Ljava/lang/Object;"
private const val LIST = "Ljava/util/List;"

private fun Method.parameters() = parameterTypes.map(CharSequence::toString)
private fun Method.readFields() = implementation?.instructions?.toList().orEmpty().mapNotNull {
    it.getReference<FieldReference>().takeIf { _ -> it.opcode == Opcode.IGET_OBJECT }
}.distinctBy(FieldReference::toString)

/** All unstable native contracts are resolved here; the Java worker knows no R8 identities. */
@Suppress("unused")
val newXProfileBulkDownloadPatch = bytecodePatch(
    name = "NewX: Download all profile media",
    description = "Downloads every accessible photo and video from a profile, with pagination, progress and cancellation.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXProfilePhotosGalleryPatch, newXInlineDownloadButtonPatch, newXTimelineFilterPatch)
    newXToggle(
        id = "newx.downloads.profile_bulk",
        category = Categories.DOWNLOADS,
        strings = settingStrings("piko_newx_profile_bulk"),
        order = 50,
        defaultValue = true,
    )
    execute {
        val classes = buildList<ClassDef> { classDefForEach { add(it) } }
        val byType = classes.associateBy { it.type.toString() }
        val paging = resolvePagingEvent(classes)
        val nativeHost = requireExactlyOne("NewX bulk URT host", classes.filter { cls ->
            cls.type.startsWith("Lcom/x/urt/") && cls.fields.any { it.type == paging.paginatorType }
        })
        val bottom = requireExactlyOne("NewX bulk bottom paginator getter", nativeHost.methods.filter { method ->
            method.parameterTypes.isEmpty() && method.readFields().any { it.type == paging.paginatorType }
        })
        val hostInterface = requireExactlyOne("NewX bulk URT interface", nativeHost.interfaces.mapNotNull {
            byType[it.toString()]
        }.filter { cls -> cls.methods.any { it.name == bottom.name && it.parameters() == bottom.parameters() } })
        val stateGetter = requireExactlyOne("NewX bulk timeline state getter", nativeHost.methods.filter {
            it.parameterTypes.isEmpty() && !AccessFlags.STATIC.isSet(it.accessFlags) &&
                isStateFlowInterface(it.returnType.toString(), byType)
        })
        val stateValue = resolveStateValueGetter(stateGetter.returnType.toString(), byType)
        val stateInterfaceGetter = requireExactlyOne("NewX bulk timeline interface getter", hostInterface.methods.filter {
            it.name == stateGetter.name && it.parameters() == stateGetter.parameters()
        })
        val bottomInterfaceGetter = requireExactlyOne("NewX bulk bottom interface getter", hostInterface.methods.filter {
            it.name == bottom.name && it.parameters() == bottom.parameters()
        })
        val callback = requireExactlyOne("NewX bulk gallery callback capture", classes.filter { cls ->
            cls.type.startsWith("Lcom/x/urt/ui/") &&
                cls.interfaces.any { it == "Lkotlin/jvm/functions/Function1;" } &&
                cls.fields.any { it.type == hostInterface.type }
        })
        val callbackField = requireExactlyOne("NewX bulk callback component field", callback.fields.filter {
            it.type == hostInterface.type && !AccessFlags.STATIC.isSet(it.accessFlags)
        })
        val combinedConstructor = requireExactlyOne("NewX bulk combined media component", classes.filter {
            it.type.startsWith("Lcom/x/profile/timeline/")
        }.flatMap { cls -> cls.methods.filter { method ->
            val parameters = method.parameters()
            val tab = parameters.getOrNull(1)?.let(byType::get)
            method.name == "<init>" && parameters.getOrNull(0) == LIST &&
                tab?.superclass == "Ljava/lang/Enum;" &&
                tab.fields.any { it.name == "Photos" } && tab.fields.any { it.name == "Videos" }
        } })
        val combined = byType[combinedConstructor.definingClass]!!
        val tabType = combinedConstructor.parameters().getOrNull(1)
            ?: throw PatchException("NewX bulk selected tab parameter missing")
        val controller = requireExactlyOne("NewX bulk profile tab controller", combined.interfaces.mapNotNull {
            byType[it.toString()]
        }.filter { cls -> cls.type.startsWith("Lcom/x/profile/timeline/") && cls.methods.any {
            it.parameters() == listOf(tabType) && it.returnType == "V"
        } })
        val select = requireExactlyOne("NewX bulk native tab selection", controller.methods.filter {
            it.parameters() == listOf(tabType) && it.returnType == "V"
        })
        val tabs = requireExactlyOne("NewX bulk available tabs", combined.fields.filter {
            !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == LIST
        })
        // Follow the field -> getValue -> result -> cast-to-tab chain in the native current-
        // component getter. The constructor's boolean seed may already have been rewritten by
        // the default-post-sorting patch, so it is deliberately not a discovery anchor here.
        val selected = requireExactlyOne("NewX bulk selected tab state field", combined.methods.filter {
            it.parameterTypes.isEmpty() && it.returnType == hostInterface.type
        }.flatMap { method ->
            val code = method.implementation?.instructions?.toList().orEmpty()
            code.withIndex().mapNotNull { (index, cast) ->
                if (cast.opcode != Opcode.CHECK_CAST || cast.getReference<TypeReference>()?.type != tabType) {
                    return@mapNotNull null
                }
                val result = code.getOrNull(index - 1)
                val call = code.getOrNull(index - 2)
                val read = code.getOrNull(index - 3)
                val ref = call?.getReference<MethodReference>()
                val field = read?.getReference<FieldReference>()
                field?.takeIf {
                    read?.opcode == Opcode.IGET_OBJECT && ref?.name == "getValue" &&
                        ref.parameterTypes.isEmpty() && ref.returnType == OBJECT &&
                        field.definingClass == combined.type &&
                        result?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                        (result as? OneRegisterInstruction)?.registerA == (cast as? OneRegisterInstruction)?.registerA &&
                        call?.registersUsed?.toList() == listOf((read as TwoRegisterInstruction).registerA)
                }
            }
        }.distinctBy(FieldReference::toString))
        val success = requireExactlyOne("NewX bulk timeline success", NewXTimelineSuccessFingerprint.scopedMatchAll())
        val successToString = requireExactlyOne("NewX bulk success labels", success.originalClassDef.methods.filter {
            it.name == "toString" && it.parameterTypes.isEmpty()
        })
        val items = successToString.fieldForToStringLabel(", timelineItems=")
        val timelineType = successToString.fieldForToStringLabel("Success(timelineType=")
        val pagingStateClass = byType[paging.state.stateType]!!
        val pagingToString = requireExactlyOne("NewX bulk paginator labels", pagingStateClass.methods.filter {
            it.name == "toString" && it.parameterTypes.isEmpty()
        })
        val labels = modelFieldsByAppendOrder(pagingToString) { ref ->
            byType[ref.definingClass]?.methods?.let { methods ->
                requireExactlyOne("NewX bulk model-label helper $ref", methods.filter {
                    it.name == ref.name && it.parameters() == ref.parameterTypes.map(CharSequence::toString) &&
                        it.returnType == ref.returnType
                })
            }
        }
        fun field(label: String) = labels[label]
            ?: throw PatchException("NewX bulk paginator field missing: $label")
        val loading = field(", isLoading=")
        val cursor = field(", cursorToUseForLoad=")
        val fetchEvent = field(", lastKnownTimelineNetworkFetchEvent=")
        listOf(callbackField, tabs, selected, items, timelineType, loading, cursor, fetchEvent).groupBy { it.definingClass }.forEach {
            (owner, fields) -> mutableClassDefBy(owner).requirePublicFields(fields)
        }

        replaceBulkBridge("componentOf") { method ->
            instanceOf(0, method.p0Register, callback.type)
            ifEqz(0, Target.Local("missing"))
            move(0, method.p0Register, OBJECT)
            checkCast(0, callback.type)
            iget(0, 0, callbackField)
            returnObject(0)
            label("missing")
            constInt(0, 0)
            returnObject(0)
        }
        replaceBulkBridge("availableTabs") { method ->
            instanceOf(0, method.p0Register, combined.type)
            ifEqz(0, Target.Local("missing"))
            move(0, method.p0Register, OBJECT)
            checkCast(0, combined.type)
            iget(0, 0, tabs)
            returnObject(0)
            label("missing")
            constInt(0, 0)
            returnObject(0)
        }
        replaceBulkBridge("selectedTab") { method ->
            move(0, method.p0Register, OBJECT)
            checkCast(0, combined.type)
            iget(0, 0, selected)
            invoke(stateValue.opcode, methodReference(stateValue.descriptor), listOf(0))
            moveResult(0, OBJECT)
            returnObject(0)
        }
        replaceBulkBridge("selectTab") { method ->
            move(0, method.p0Register, OBJECT)
            move(1, method.p0Register + 1, OBJECT)
            checkCast(0, controller.type)
            checkCast(1, tabType)
            invokeInterface(methodReference(select.toString()), 0, 1)
            returnVoid()
        }
        replaceBulkBridge("currentItems") { method ->
            move(0, method.p0Register, OBJECT)
            checkCast(0, hostInterface.type)
            invokeInterface(methodReference(stateInterfaceGetter.toString()), 0)
            moveResult(0, OBJECT)
            invoke(stateValue.opcode, methodReference(stateValue.descriptor), listOf(0))
            moveResult(0, OBJECT)
            instanceOf(1, 0, success.originalClassDef.type)
            ifEqz(1, Target.Local("missing"))
            checkCast(0, success.originalClassDef.type)
            iget(1, 0, timelineType)
            move(2, method.p0Register, OBJECT)
            checkCast(2, combined.type)
            iget(2, 2, selected)
            invoke(stateValue.opcode, methodReference(stateValue.descriptor), listOf(2))
            moveResult(2, OBJECT)
            invokeStatic(methodReference("$EXTENSION->matchesTimeline($OBJECT$OBJECT)Z"), 1, 2)
            moveResult(1, "Z")
            ifEqz(1, Target.Local("missing"))
            iget(1, 0, items)
            invokeStatic(methodReference("$EXTENSION->itemsBeforeFilters($OBJECT$LIST)$LIST"), 0, 1)
            moveResult(0, LIST)
            returnObject(0)
            label("missing")
            constInt(0, 0)
            returnObject(0)
        }
        fun Block.readPaginator(method: MutableMethod) {
            move(0, method.p0Register, OBJECT)
            checkCast(0, hostInterface.type)
            invokeInterface(methodReference(bottomInterfaceGetter.toString()), 0)
            moveResult(0, OBJECT)
            checkCast(0, paging.paginatorType)
        }
        replaceBulkBridge("pagingSnapshot") { method ->
            readPaginator(method)
            val state = paging.state
            invoke(state.stateFlowGetter.opcode, methodReference(state.stateFlowGetter.descriptor), listOf(0))
            moveResult(0, OBJECT)
            invoke(state.stateValueGetter.opcode, methodReference(state.stateValueGetter.descriptor), listOf(0))
            moveResult(0, OBJECT)
            checkCast(0, state.stateType)
            constInt(1, 5)
            newArray(2, 1, "[Ljava/lang/Object;")
            listOf(state.needsMore, state.terminated).forEachIndexed { index, call ->
                invoke(call.opcode, methodReference(call.descriptor), listOf(0))
                moveResult(3, "Z")
                invokeStatic(methodReference("Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;"), 3)
                moveResult(3, OBJECT)
                constInt(1, index)
                aputObject(3, 2, 1)
            }
            iget(3, 0, loading)
            invokeStatic(methodReference("Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;"), 3)
            moveResult(3, OBJECT)
            constInt(1, 2)
            aputObject(3, 2, 1)
            listOf(cursor, fetchEvent).forEachIndexed { index, value ->
                iget(3, 0, value)
                constInt(1, index + 3)
                aputObject(3, 2, 1)
            }
            returnObject(2)
        }
        replaceBulkBridge("requestNext") { method ->
            readPaginator(method)
            sget(1, fieldReference(paging.eventKindField))
            newInstance(2, paging.eventType)
            constInt(3, 0)
            constInt(4, 0)
            invokeDirect(methodReference(paging.constructorReference), 2, 1, 3, 4)
            invoke(paging.dispatch.opcode, methodReference(paging.dispatch.descriptor), listOf(0, 2))
            returnVoid()
        }
    }
}

context(context: BytecodePatchContext)
private fun replaceBulkBridge(name: String, body: Block.(MutableMethod) -> Unit) {
    val owner = context.mutableClassDefBy(EXTENSION)
    val stub = requireExactlyOne("NewX profile bulk bridge $name", owner.methods.filter { it.name == name })
    val method = stub.cloneMutable(additionalRegisters = 8)
    owner.methods.remove(stub)
    owner.methods.add(method)
    method.implementation!!.apply {
        while (instructions.isNotEmpty()) removeInstruction(instructions.lastIndex)
    }
    method.insertHook(0, relocateBranchTargets = false) { body(method) }
}
