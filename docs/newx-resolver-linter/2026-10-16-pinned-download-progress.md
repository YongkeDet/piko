# Pinned native download progress and independent outcomes

Target: `com.twitter.android` 12.34.0-prod.01, APKMirror arm64-v8a APKM.
APKM SHA-256: `016a7eb2b6d8e2d6410da7f3c60e1bb46b002f6fedd797e366b068fb2897682b`.

## Reported runtime bug

User report (complete, no exception/stack was provided):

> 顶部通知不应重复弹出。下载进行中，将进度固定在顶部展示；下载完成后，再弹出完成窗口。多任务下载的弹窗需独立管理、有序展示，确保互不干扰。
> 信息层次鲜明。
> 全部下载的弹窗亦同上述。

Baseline tested source: `228c31fa95d27ab62d56b0602e7d2a0ba5dd26d1`.
Baseline MPP `.9` SHA-256: `465d063865a0dad7fe546d764af55124c7705038dccede897b5c0d10e6183e31`.
NewX extension SHA-256: `10a9c828bd2b5ded57666434e2beebf54b0303fe8f4702f908e3ca8d60fee5f3`.
Shared extension SHA-256: `ad486f4bf4537b12e4d0efaff8b809cf550d6f9eea4bff4549d481a9da105396`.
Baseline all-patches APK SHA-256: `8e2e465b8cdc5c73dfd0f15218e9b7ae081172fa96854d53d41c58c0a8a02365`.
Failing feature: NewX: Unified download notifications (runtime behavior, not patch application).
Classification: extension runtime lifecycle/queue policy; no linter/helper attribution.

## Evidence and repair

The baseline generated a new API model and invoked native presentation every second, with the
native default five-second timeout. Its severity hold could discard another task's outcome.
The bulk session separately showed both an automatic progress dialog and an automatic result dialog.

The replacement retains each task's model and native Snapshot MutableState. The exact notification
renderer uses a group-free composable Literal localizer; only our literal identities read these states.
Native `Custom(INFINITE)` is resolved from the duration field compared by native presentation.
Text changes do not call the facade, actor, presentation, disposal, or reset a timeout.

Per-task progress, a FIFO outcome queue and native admission/disposal acknowledgements replace the
hold timer. Task IDs span positive engine IDs, negative merge IDs and long IDs for bulk/generic
messages. Only the actual current entry can acknowledge an outcome or trigger scoped cancellation.
A clear control must match the currently owned progress model and cannot dismiss results or foreign
native notifications. PrimaryButtonClick invokes only the owning task's cancellation callback.
Bulk members remain silent; the session reports one native queued summary and no automatic dialogs.

Receiver lifetime matters: R8 overwrites dispose's p0 before return. Lifecycle observation is inserted
immediately after StateFlow publication, while p0 is still the manager, and fails closed if overwritten.
Existing typed hook helper is unchanged. The native actor's suspension/continuation remains intact.

Regression fixtures: NewXDownloaderTest covers repeated progress, admission races, independent
outcomes, native preemption, duplicate callbacks and bulk cancellation. UnifiedDownloadNotificationPatchTest
covers native actor continuation, reactive text fallthrough and pre-overwrite lifecycle observation.

Local Gradle attempt: `./gradlew :patches:compileKotlin :extensions:newx:testDebugUnitTest --no-daemon --max-workers=2`.
It failed before build while downloading Gradle 9.7.1: `java.net.SocketException: Network is unreachable`.
No build or patch result is inferred from that tooling failure. CI builds the exact source instead.

The unified adapter remains limited to the validated 12.34 compatibility declaration. Older target
notification/card/dialog paths remain behind hasUnifiedHost=false; an older APK is unavailable here.
No device was launched or controlled. Final APK evidence and source provenance will be appended after verification.

## First CI lint finding

Source `8fa648d1912cb770682ee08653309394dd1203f7`, run `38928279908`, job `117065400805`.
Extension tests and Kotlin compilation passed; the resolver linter stopped the build before publication.
This was a false positive on the helper-returned ordered parameter list (size == 2 was already checked),
not APK drift or a failed patch. Rewrite uses the explicit `parameterTypes` contract with the same size guard;
the library is unchanged. The observed finding and corrected non-finding are recorded in
NewXResolverLinterTest.

```text
misc/inlineactions/UnifiedDownloadNotificationPatch.kt:243:115: raw-index: indexed access on candidate-like 'types'; require exact cardinality or use an explicit fail-closed resolver
    it.definingClass == runtime.facadeDescriptor && it.returnType == "V" && it.types().size == 2 && it.types()[0] == runtime.apiDescriptor
Resolver lint found 1 gating issue(s) and 0 advisory finding(s)
Execution failed for task ':patches:lintNewxResolvers'.
```

Local offline state-machine smoke check compiled the actual DownloadBannerPolicy,
DownloadProgressState and existing NewXDownloaderTest with javac 17 and a minimal assertion
runner (not a replacement for CI/JUnit): all eight downloader regressions passed.
PinnedBannerStaticReview.java compiles against the official Morphe CLI and existing review helpers.

Second CI run `38929020837` (source `16d5c1092ecc05cfb3e7015cc3b1f5d8abafe37c`)
passed compilation, extension tests and production resolver lint. One newly added lint fixture failed:
`expected [RAW_INDEX] but was []`. The reduced fixture had omitted the preceding mapNotNull lambda,
which changes the library's lexical proof boundary. The corrected fixture includes the actual
mapNotNull/filter pipeline. Running the unchanged library classes from the verified .9 MPP locally
reproduces exactly one finding before, zero after; the entire current notification resolver has zero
findings. No production patch or extension change was required for this fixture correction.

Pre-patch smali review found the model's actual semantic property label is `buttonSpec`,
not `button`. The button resolver anchor was corrected before any APK test; no obfuscated
field name or descriptor was added. The final DEX review also symbolically checks that the
unified bulk constructor does not instantiate its legacy progress dialog.
