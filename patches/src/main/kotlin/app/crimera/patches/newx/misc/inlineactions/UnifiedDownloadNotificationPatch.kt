package app.crimera.patches.newx.misc.inlineactions

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X_12_34
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val BRIDGE = "Lapp/morphe/extension/newx/misc/NewXInAppNotification;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val IMPL = "Lcom/x/inappnotification/impl/"

context(context: BytecodePatchContext)
private fun nativeNotificationClass(type: String): ClassDef = context.classDefByOrNull(type)
    ?: throw PatchException("Missing NewX notification class $type")

private fun Method.code() = implementation?.instructions?.toList().orEmpty()
private fun MethodReference.types() = parameterTypes.map(CharSequence::toString)
private fun MethodReference.same(other: MethodReference) = toString() == other.toString()

/** Owns only the separately verified native adapter; older targets keep the original bridge/card. */
@Suppress("unused")
val unifiedDownloadNotificationPatch = bytecodePatch(
    name = "NewX: Unified download notifications",
    description = "Pins native download progress without repeated popups and queues independent task results, including profile downloads.",
) {
    compatibleWith(COMPATIBILITY_NEW_X_12_34)
    dependsOn(newXInlineDownloadButtonPatch)
    execute {
        val runtime = resolveNotificationRuntime()
        val classes = buildList<ClassDef> { classDefForEach { add(it) } }
        val model = nativeNotificationClass(runtime.apiDescriptor)
        val detail = notificationField(model, ", detailText=")
        val style = notificationField(model, ", action=null, style=")
        val priority = notificationField(model, ", priority=")
        val styleClass = nativeNotificationClass(style.type)
        val priorityClass = nativeNotificationClass(priority.type)
        val styles = listOf("Default", "Success", "Error").map { enumField(styleClass, it) }
        val priorities = listOf("Low", "Normal", "High").map { enumField(priorityClass, it) }
        val constructor = requireExactlyOne("NewX rich notification constructor", model.methods.filter { it.same(runtime.apiConstructor) })
        val styleParameter = requireExactlyOne("NewX notification style parameter", constructor.types().indices.filter { constructor.types()[it] == style.type })
        val priorityParameter = requireExactlyOne("NewX notification priority parameter", constructor.types().indices.filter { constructor.types()[it] == priority.type })
        val supplied = listOf(4, styleParameter, priorityParameter)
        if (detail.type != constructor.parameterTypes[0].toString() || supplied.any { it <= 0 }) {
            throw PatchException("NewX notification detail/style/priority contract changed")
        }
        val maskBits = supplied.associateWith { notificationDefaultBit(constructor, it) }

        // Show(entry=...) identifies the event, whose getter links to the manager's entry contract.
        val show = requireExactlyOne("NewX native Show notification event", classes.filter { type ->
            type.type.startsWith(IMPL) && type.methods.any { method -> method.code().any {
                it.getReference<StringReference>()?.string == "Show(entry="
            } }
        })
        val showConstructor = requireExactlyOne("NewX Show event constructor", show.methods.filter {
            it.name == "<init>" && it.types().size == 1 && it.types()[0].startsWith(IMPL)
        })
        val entryType = showConstructor.types()[0]
        val showGetter = requireExactlyOne("NewX Show entry accessor", show.methods.filter {
            it.name != "<init>" && it.types().isEmpty() && it.returnType == entryType
        })
        val entry = nativeNotificationClass(entryType)
        val modelGetter = requireExactlyOne("NewX notification entry model accessor", entry.methods.filter {
            it.types().isEmpty() && it.returnType == runtime.apiDescriptor
        })
        val actors = classes.flatMap { type -> type.methods.filter { method ->
            method.types().size == 2 && method.types()[0] == type.type &&
                method.types()[1].startsWith("Lkotlin/coroutines/") &&
                AccessFlags.STATIC.isSet(method.accessFlags) && method.returnType == OBJECT &&
                method.code().any { it.getReference<MethodReference>()?.same(showGetter) == true }
        } }
        val actorReference = requireExactlyOne("NewX notification event actor", actors)
        val manager = nativeNotificationClass(actorReference.definingClass)
        val current = requireExactlyOne("NewX current notification entry", manager.fields.filter {
            !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == entryType
        })
        if (!AccessFlags.PUBLIC.isSet(current.accessFlags)) throw PatchException("NewX current notification entry is not public")
        val dispose = requireExactlyOne("NewX notification entry disposal", manager.methods.filter {
            it.returnType == "V" && it.types().size == 2 && it.types()[0] == entryType &&
                classDefByOrNull(it.types()[1])?.let { type -> type.superclass == "Ljava/lang/Enum;" && type.fields.any { f -> f.name == "Replaced" } } == true
        })
        val reason = enumField(nativeNotificationClass(dispose.types()[1]), "Replaced")
        val dismiss = requireExactlyOne("NewX native dismiss current and advance queue", manager.methods.filter {
            it.returnType == "V" && it.types() == listOf(reason.type) &&
                it.code().any { ins -> ins.getReference<MethodReference>()?.same(dispose) == true }
        })
        val present = requireExactlyOne("NewX native notification presentation", manager.methods.filter {
            it.returnType == "V" && it.types() == listOf(entryType) &&
                it.code().any { ins -> ins.getReference<MethodReference>()?.same(modelGetter) == true } &&
                it.code().any { ins -> ins.getReference<MethodReference>()?.let { call ->
                    call.definingClass.startsWith("Lkotlinx/coroutines/flow/") && call.types() == listOf(OBJECT) && call.returnType == "V"
                } == true }
        })
        patchRichFactory(runtime, supplied, maskBits, styles, priorities,
            notificationField(model, ", button="))
        patchPinnedSender(runtime, classes, present)
        patchReactiveText(runtime, classes, model, detail)
        patchNativeInterceptor(manager.type, current, modelGetter, dispose, dismiss, reason)
        patchNativeLifecycle(manager, current, modelGetter, dispose, present)
        val actor = requireExactlyOne("NewX mutable notification actor", mutableClassDefBy(manager.type).methods.filter { it.same(actorReference) })
        actor.interceptNotificationActor(showGetter, present)
        bridgeMethod("hasUnifiedHost", emptyList(), 1).insertHook(0, relocateBranchTargets = false) { constInt(0, 1); returnValue(0) }
    }
}

/** The nearest field read before the next printed label belongs to that data-class property. */
internal fun notificationField(model: ClassDef, anchor: String): FieldReference {
    val printer = requireExactlyOne("NewX notification printer", model.methods.filter { it.name == "toString" && it.types().isEmpty() })
    val code = printer.code()
    val index = requireExactlyOne("NewX notification property $anchor", code.indices.filter { code[it].getReference<StringReference>()?.string == anchor })
    val section = code.drop(index + 1).takeWhile { it.getReference<StringReference>() == null }
    return requireExactlyOne("NewX notification field after $anchor", section.mapNotNull { it.getReference<FieldReference>() }.filter { it.definingClass == model.type }.distinctBy { it.toString() })
}

/** R8 can remove a constructor parameter without renumbering Kotlin's default-mask bits. */
internal fun notificationDefaultBit(constructor: Method, parameter: Int): Int {
    val code = constructor.code()
    val frame = constructor.implementation ?: throw PatchException("Missing notification constructor")
    val receiver = frame.registerCount - constructor.types().size - 1
    val destination = receiver + 1 + parameter
    val maskRegister = frame.registerCount - 1
    var bit: Int? = null
    val found = buildList {
        for (instruction in code) {
            if (instruction.opcode in setOf(Opcode.AND_INT_LIT8, Opcode.AND_INT_LIT16) &&
                (instruction as TwoRegisterInstruction).registerB == maskRegister) {
                bit = (instruction as NarrowLiteralInstruction).narrowLiteral
            }
            if (instruction.opcode in setOf(Opcode.SGET_OBJECT, Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16) &&
                (instruction as OneRegisterInstruction).registerA == destination) bit?.let { add(it) }
        }
    }
    val result = requireExactlyOne("NewX notification default bit for parameter $parameter", found.distinct())
    if (result <= 0 || result and (result - 1) != 0) throw PatchException("Non-bit notification default mask: $result")
    return result
}

private fun enumField(type: ClassDef, name: String): FieldReference {
    if (type.superclass != "Ljava/lang/Enum;") throw PatchException("NewX notification $name is not an enum")
    return requireExactlyOne("NewX notification enum ${type.type}.$name", type.fields.filter {
        it.name == name && it.type == type.type && AccessFlags.STATIC.isSet(it.accessFlags)
    })
}

context(context: BytecodePatchContext)
private fun bridgeMethod(name: String, types: List<String>, locals: Int): MutableMethod {
    val owner = context.mutableClassDefBy(BRIDGE)
    val stub = requireExactlyOne("NewX unified notification bridge $name", owner.methods.filter { it.name == name && it.types() == types })
    val required = locals + types.size
    val frame = stub.implementation ?: throw PatchException("Missing NewX notification bridge $name")
    return if (frame.registerCount >= required) stub else stub.cloneMutable(additionalRegisters = required - frame.registerCount).also {
        owner.methods.remove(stub); owner.methods.add(it)
    }
}

context(context: BytecodePatchContext)
private fun patchRichFactory(
    runtime: ResolvedNotificationRuntime, supplied: List<Int>, maskBits: Map<Int, Int>,
    styles: List<FieldReference>, priorities: List<FieldReference>, button: FieldReference,
) {
    val count = runtime.apiConstructor.parameterTypes.size
    if (count + 3 > 15) throw PatchException("NewX rich notification constructor exceeds the validated low-register frame")
    val buttonParameter = requireExactlyOne("NewX button constructor parameter", runtime.apiConstructor.types().indices.filter {
        runtime.apiConstructor.types()[it] == button.type
    })
    val constructor = requireExactlyOne("NewX rich constructor body", nativeNotificationClass(runtime.apiDescriptor).methods.filter { it.same(runtime.apiConstructor) })
    val buttonBit = notificationDefaultBit(constructor, buttonParameter)
    val method = bridgeMethod("createRich", listOf(STRING, STRING, "I", OBJECT), count + 4)
    method.insertHook(0, relocateBranchTargets = false) {
        val title = method.p0Register
        val work = count + 2
        val kind = count + 3
        move(kind, title + 2, "I")
        newInstance(1, runtime.apiDescriptor)
        newInstance(2, runtime.literalDescriptor)
        move(work, title, STRING)
        invokeDirect(runtime.literalConstructor, 2, work)
        (3..count + 1).forEach { constInt(it, 0) }
        move(buttonParameter + 2, title + 3, OBJECT)
        checkCast(buttonParameter + 2, button.type)
        val detailReg = supplied[0] + 2
        move(work, title + 1, STRING)
        newInstance(detailReg, runtime.literalDescriptor)
        invokeDirect(runtime.literalConstructor, detailReg, work)
        val styleReg = supplied[1] + 2
        val priorityReg = supplied[2] + 2
        sget(styleReg, styles[0]); sget(priorityReg, priorities[1])
        constInt(work, 1); ifEq(kind, work, Target.Local("success"))
        constInt(work, 2); ifEq(kind, work, Target.Local("error"))
        constInt(work, 3); ifEq(kind, work, Target.Local("progress"))
        goto(Target.Local("ready"))
        label("success"); sget(styleReg, styles[1]); goto(Target.Local("ready"))
        label("error"); sget(styleReg, styles[2]); goto(Target.Local("ready"))
        label("progress"); sget(priorityReg, priorities[0])
        label("ready")
        var mask = ((1 shl count) - 2) and buttonBit.inv()
        maskBits.values.forEach { mask = mask and it.inv() }
        constInt(count + 1, mask)
        invokeDirect(runtime.apiConstructor, 1, *IntArray(count) { it + 2 })
        returnObject(1)
    }
    val classes = buildList<ClassDef> { context.classDefForEach { add(it) } }
    val primary = requireExactlyOne("NewX primary-only button", classes.filter { type ->
        button.type in type.interfaces && type.methods.any { m -> m.code().any { it.getReference<StringReference>()?.string == "PrimaryOnly(text=" } }
    })
    val ctor = requireExactlyOne("NewX primary-only button constructor", primary.methods.filter {
        it.name == "<init>" && it.types() == listOf(runtime.apiConstructor.types()[0])
    })
    val cancel = bridgeMethod("cancelButton", listOf(STRING), 3)
    cancel.insertHook(0, relocateBranchTargets = false) {
        newInstance(0, runtime.literalDescriptor)
        move(1, cancel.p0Register, STRING); invokeDirect(runtime.literalConstructor, 0, 1)
        newInstance(2, primary.type); invokeDirect(ctor, 2, 0); returnObject(2)
    }
}

/** Infinity is the same field compared by the native Custom-duration branch, never a magic delay. */
context(context: BytecodePatchContext)
private fun patchPinnedSender(runtime: ResolvedNotificationRuntime, classes: List<ClassDef>, present: Method) {
    val sender = requireExactlyOne("NewX generic sender body", nativeNotificationClass(runtime.facadeDescriptor).methods.filter { it.same(runtime.sender) })
    val instance = requireExactlyOne("NewX sender with duration", sender.code().mapNotNull { it.getReference<MethodReference>() }.filter {
        val parameters = it.parameterTypes
        it.definingClass == runtime.facadeDescriptor && it.returnType == "V" && parameters.size == 2 && parameters[0].toString() == runtime.apiDescriptor
    }.distinctBy { it.toString() })
    val duration = instance.types()[1]
    val custom = requireExactlyOne("NewX custom notification duration", classes.filter { type ->
        type.superclass == duration && type.methods.any { m -> m.code().any { it.getReference<StringReference>()?.string == "Custom(duration=" } }
    })
    val ctor = requireExactlyOne("NewX custom duration constructor", custom.methods.filter { it.name == "<init>" && it.types() == listOf("J") })
    val infinity = requireExactlyOne("Native infinite notification duration", present.code().filter { it.opcode == Opcode.SGET_WIDE }
        .mapNotNull { it.getReference<FieldReference>() }.filter { it.definingClass == "Lkotlin/time/Duration;" && it.type == "J" }.distinctBy { it.toString() })
    if (present.code().none { it.getReference<MethodReference>()?.definingClass == custom.type }) {
        throw PatchException("NewX custom infinite duration is not consumed by presentation")
    }
    val method = bridgeMethod("postRich", listOf(OBJECT, "I"), 6)
    method.insertHook(0, relocateBranchTargets = false) {
        sget(0, fieldReference("$BRIDGE->facade:$OBJECT")); ifEqz(0, Target.Local("unavailable"))
        checkCast(0, runtime.facadeDescriptor)
        move(1, method.p0Register, OBJECT); checkCast(1, runtime.apiDescriptor)
        move(2, method.p0Register + 1, "I"); constInt(5, 3)
        ifNe(2, 5, Target.Local("result"))
        newInstance(2, custom.type); sget(3, infinity); invokeDirect(ctor, 2, 3, 4)
        invokeVirtual(instance, 0, 1, 2); goto(Target.Local("sent"))
        label("result"); invokeStatic(runtime.sender, 0, 1)
        label("sent"); constInt(0, 1); returnValue(0)
        label("unavailable"); constInt(0, 0); returnValue(0)
    }
}

/** Resolve the exact localizer consumed by the native notification renderer and its State factory. */
context(context: BytecodePatchContext)
private fun patchReactiveText(runtime: ResolvedNotificationRuntime, classes: List<ClassDef>, model: ClassDef, detail: FieldReference) {
    val message = notificationField(model, "InAppNotification(message=")
    listOf("titleLiteral" to message, "detailLiteral" to detail).forEach { (name, field) ->
        val method = bridgeMethod(name, listOf(OBJECT), 1)
        method.insertHook(0, relocateBranchTargets = false) {
            move(0, method.p0Register, OBJECT); checkCast(0, model.type); iget(0, 0, field); returnObject(0)
        }
    }
    val composer = "Landroidx/compose/runtime/Composer;"
    val renderers = classes.flatMap { type -> type.methods.filter { method ->
        composer in method.types() && model.type in method.types() &&
            method.code().any { it.getReference<FieldReference>()?.toString() == message.toString() }
    } }
    if (renderers.isEmpty()) throw PatchException("NewX native notification text renderer not found")
    val locals = renderers.flatMap { it.code().mapNotNull { ins -> ins.getReference<MethodReference>() } }.filter {
        it.returnType == STRING && composer in it.types() && message.type in it.types()
    }.distinctBy { it.toString() }
    val localizerRef = requireExactlyOne("NewX notification composable localizer", locals)
    val localizer = requireExactlyOne("NewX localizer body", context.mutableClassDefBy(localizerRef.definingClass).methods.filter { it.same(localizerRef) })
    val literalField = requireExactlyOne("NewX literal text field", nativeNotificationClass(runtime.literalDescriptor).fields.filter { it.type == STRING && !AccessFlags.STATIC.isSet(it.accessFlags) })
    val helper = requireExactlyOne("NewX localizer context helper", localizer.code().mapNotNull { it.getReference<MethodReference>() }.filter {
        it.returnType == STRING && message.type in it.types() && "Landroid/content/Context;" in it.types()
    })
    val helperBody = requireExactlyOne("NewX literal localizer body", nativeNotificationClass(helper.definingClass).methods.filter { it.same(helper) })
    if (helperBody.code().none { it.getReference<FieldReference>()?.toString() == literalField.toString() } ||
        localizer.code().any { it.getReference<MethodReference>()?.returnType == composer }) {
        throw PatchException("NewX text localizer no longer has the validated group-free Literal contract")
    }
    localizer.interceptProgressText(message.type)

    // The native renderer's owner also contains its host. Derive a factory used there which
    // returns a Snapshot-backed MutableState with the public Compose getValue/setValue contract.
    val rendererOwners = renderers.map { it.definingClass }.toSet()
    val factories = classes.filter { it.type in rendererOwners }.flatMap { type -> type.methods.filter { composer in it.types() }
        .flatMap { it.code().mapNotNull { ins -> ins.getReference<MethodReference>() } } }.filter { call ->
        call.types() == listOf(OBJECT) && call.returnType.startsWith("Landroidx/compose/runtime/") &&
            context.classDefByOrNull(call.returnType)?.let { state ->
                state.superclass?.startsWith("Landroidx/compose/runtime/snapshots/") == true &&
                    state.methods.any { it.name == "getValue" && it.types().isEmpty() && it.returnType == OBJECT } &&
                    state.methods.any { it.name == "setValue" && it.types() == listOf(OBJECT) && it.returnType == "V" }
            } == true
    }.distinctBy { it.toString() }
    val factory = requireExactlyOne("NewX native Snapshot state factory", factories)
    val state = nativeNotificationClass(factory.returnType)
    val get = requireExactlyOne("NewX Snapshot state read", state.methods.filter { it.name == "getValue" && it.types().isEmpty() && it.returnType == OBJECT })
    val set = requireExactlyOne("NewX Snapshot state write", state.methods.filter { it.name == "setValue" && it.types() == listOf(OBJECT) && it.returnType == "V" })
    val make = bridgeMethod("makeState", listOf(STRING), 1)
    make.insertHook(0, relocateBranchTargets = false) { invokeStatic(factory, make.p0Register); moveResult(0, factory.returnType); returnObject(0) }
    val read = bridgeMethod("readState", listOf(OBJECT), 1)
    read.insertHook(0, relocateBranchTargets = false) {
        move(0, read.p0Register, OBJECT); checkCast(0, state.type); invokeVirtual(get, 0); moveResult(0, OBJECT); checkCast(0, STRING); returnObject(0)
    }
    val write = bridgeMethod("writeState", listOf(OBJECT, STRING), 2)
    write.insertHook(0, relocateBranchTargets = false) {
        move(0, write.p0Register, OBJECT); checkCast(0, state.type); move(1, write.p0Register + 1, STRING); invokeVirtual(set, 0, 1); returnVoid()
    }
}

/** Only owned literal identities read State; unrelated app strings retain the original path. */
internal fun MutableMethod.interceptProgressText(model: String) {
    val types = types()
    val parameter = requireExactlyOne("NewX localizer text parameter", types.indices.filter { types[it] == model })
    if (!AccessFlags.STATIC.isSet(accessFlags) || types.any { it == "J" || it == "D" }) throw PatchException("NewX text localizer register shape changed")
    insertHook(0, relocateBranchTargets = false) {
        val value = scratchRegister(RegisterLimit.BYTE)
        invokeStatic(methodReference("$BRIDGE->progressText($OBJECT)$STRING"), p0Register + parameter)
        moveResult(value, STRING); ifEqz(value, Target.Original); returnObject(value)
    }
}

context(context: BytecodePatchContext)
private fun patchNativeLifecycle(manager: ClassDef, current: FieldReference, model: MethodReference, dispose: MethodReference, present: MethodReference) {
    val observe = bridgeMethod("observeNative", listOf(OBJECT), 2)
    observe.insertHook(0, relocateBranchTargets = false) {
        move(0, observe.p0Register, OBJECT); checkCast(0, manager.type); iget(0, 0, current)
        constInt(1, 0); ifEqz(0, Target.Local("observed"))
        invokeVirtual(model, 0); moveResult(1, model.returnType)
        label("observed"); invokeStatic(methodReference("$BRIDGE->nativeVisible($OBJECT)V"), 1); returnVoid()
    }
    val disposing = bridgeMethod("disposingNative", listOf(OBJECT, OBJECT, OBJECT), 4)
    disposing.insertHook(0, relocateBranchTargets = false) {
        move(0, disposing.p0Register, OBJECT); checkCast(0, manager.type)
        move(1, disposing.p0Register + 1, OBJECT); checkCast(1, current.type)
        iget(2, 0, current); constInt(3, 0)
        ifNe(1, 2, Target.Local("identity")); constInt(3, 1)
        label("identity"); invokeVirtual(model, 1); moveResult(0, model.returnType)
        invokeStatic(methodReference("$BRIDGE->nativeDisposed($OBJECT${OBJECT}Z)V"), 0, disposing.p0Register + 2, 3); returnVoid()
    }
    listOf(present, dispose).forEach { reference ->
        val method = requireExactlyOne("NewX native lifecycle body", context.mutableClassDefBy(manager.type).methods.filter { it.same(reference) })
        method.observeNotificationPublication()
        if (reference.same(dispose)) method.insertHook(0, relocateBranchTargets = false) {
            invokeStatic(methodReference("$BRIDGE->disposingNative($OBJECT$OBJECT$OBJECT)V"), method.p0Register, method.p0Register + 1, method.p0Register + 2)
        }
    }
}

context(context: BytecodePatchContext)
private fun patchNativeInterceptor(
    manager: String, current: FieldReference, model: MethodReference, dispose: MethodReference,
    dismiss: MethodReference, reason: FieldReference,
) {
    val method = bridgeMethod("interceptNative", listOf(OBJECT, OBJECT), 7)
    method.insertHook(0, relocateBranchTargets = false) {
        move(0, method.p0Register, OBJECT); checkCast(0, manager)
        move(1, method.p0Register + 1, OBJECT); checkCast(1, current.type)
        iget(2, 0, current)
        invokeVirtual(model, 1); moveResult(3, model.returnType)
        constInt(4, 0)
        ifEqz(2, Target.Local("decision"))
        invokeVirtual(model, 2); moveResult(4, model.returnType)
        label("decision")
        invokeStatic(methodReference("$BRIDGE->decideNative($OBJECT$OBJECT)I"), 3, 4); moveResult(5, "I")
        ifEqz(5, Target.Local("native"))
        sget(6, reason)
        constInt(4, 3); ifEq(5, 4, Target.Local("dismiss"))
        goto(Target.Local("drop"))
        label("dismiss"); invokeVirtual(dismiss, 0, 6)
        label("drop"); invokeVirtual(dispose, 0, 1, 6)
        label("handled"); constInt(0, 1); returnValue(0)
        label("native"); constInt(0, 0); returnValue(0)
    }
}

/** Hook only decoded Show events, leaving suspend/resume and all other native events untouched. */
internal fun MutableMethod.interceptNotificationActor(showGetter: MethodReference, present: MethodReference) {
    val code = implementation!!.instructions.toList()
    val read = requireExactlyOne("NewX actor Show entry read", code.indices.filter { code[it].getReference<MethodReference>()?.same(showGetter) == true })
    val result = code.getOrNull(read + 1) as? OneRegisterInstruction
        ?: throw PatchException("NewX Show getter has no result")
    if (code[read + 1].opcode != Opcode.MOVE_RESULT_OBJECT) throw PatchException("NewX Show getter result changed")
    val insertion = read + 2
    val continuations = code.indices.filter { index ->
        code[index].getReference<MethodReference>()?.same(present) == true &&
            code.getOrNull(index + 1)?.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
    }.map { it + 1 }
    requireExactlyOne("NewX actor common continuation", continuations.map {
        (code[it] as BuilderOffsetInstruction).target.location.index
    }.distinct())
    // newx-resolver-lint: allow instruction-order raw-first because the first forward goto is a validated common actor continuation
    val continuation = continuations.filter { it >= insertion }.minOrNull()
        ?: throw PatchException("NewX actor has no forward continuation")
    val receiver = p0Register
    insertHook(insertion, relocateBranchTargets = false) {
        val handled = scratchRegister(RegisterLimit.BYTE)
        invokeStatic(methodReference("$BRIDGE->interceptNative($OBJECT$OBJECT)Z"), receiver, result.registerA)
        moveResult(handled, "Z")
        ifNez(handled, Target.AfterOriginal(continuation - insertion))
    }
}

/** Publication still has the original receiver; return-site parameter registers may be reused. */
internal fun MutableMethod.observeNotificationPublication() {
    val code = code()
    val publication = requireExactlyOne("NewX lifecycle StateFlow publication", code.indices.filter { index ->
        code[index].getReference<MethodReference>()?.let { call ->
            call.definingClass.startsWith("Lkotlinx/coroutines/flow/") && call.types() == listOf(OBJECT) && call.returnType == "V"
        } == true
    })
    if (code.take(publication).any { instruction ->
        instruction.opcode.setsRegister() && (instruction as? OneRegisterInstruction)?.registerA == p0Register
    }) throw PatchException("NewX notification receiver overwritten before publication")
    // Only the fallthrough after StateFlow.setValue publishes a change. Do not relocate labels
    // belonging to a different path which joins after this publication.
    insertHook(publication + 1, relocateBranchTargets = false) {
        invokeStatic(methodReference("$BRIDGE->observeNative($OBJECT)V"), p0Register)
    }
}
