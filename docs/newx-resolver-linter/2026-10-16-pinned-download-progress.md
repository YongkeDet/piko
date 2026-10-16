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

## Final source build

- Source: `6b2da69f22a09107597ca4a4f0b2d35465218b79`; tree `aed8eef5780299254549e6f78f39f8c61d3eb026`.
- CI: https://github.com/YongkeDet/piko/actions/runs/38930312006 , job `117072308254`, success.
- 86 patch tests + 279 extension tests = 365; zero failures/skips. API 29 animal-sniffer,
  Android lint, NewX resolver lint and extension descriptor checks passed.
- Bundle artifact `12058103927`, ZIP SHA-256 `6348f54ff98b91aa988a348fb0b72cfeb8c87c2ebb6bdb295d8fa6a32d7237a0`.
- Test artifact `12057934298`, ZIP SHA-256 `1141cd2a7ff934b58c7fec67d675f2e1ae8eb2211a96b48d6788c598693b2374`.
- MPP `patches-3.10.0-custom-newx.10.mpp`, SHA-256 `3de1e2ccbd0b38b087ff48ea88aca57c048c0a0ebf669570f5fdd76bfc0a5bef`.
- NewX MPE 568016 bytes, SHA-256 `f3b8fddc466400aa408a989c3b7b9b38ad0626cf0937c91c5e963c12dfbb3216`.
- Shared MPE 3371684 bytes, SHA-256 `ad486f4bf4537b12e4d0efaff8b809cf550d6f9eea4bff4549d481a9da105396`.

The bundle and both downloaded artifact ZIPs were checked against their provenance/digests before use.
`patch-twitter.sh` verifies the source HEAD, clean source paths and bundle SHA before patching.

Final review setup correction: the first BulkFixStaticReview invocation supplied `base.apk` as
its source-directory argument and failed opening `base.apk/patches/.../strings.xml`. This checker
accepts `OUTPUT.apk SOURCE_ROOT REPORT.json`, unlike the other reviews. Its arguments were
corrected and only that check rerun; no product source or APK changed.

## Exact APKM verification

Commands were run sequentially with the above CI bundle, clean production sources and HEAD
matching the provenance. No device installation or launch was requested or performed.

```sh
JAVA_TOOL_OPTIONS='-XX:-UsePerfData -Xshare:off' \
PATCHER_JAVA=/workspace/scratch/558c083b636f/verification/jdk-21.0.12.1+1-jre/bin/java \
MORPHE_CLI_JAR=/workspace/scratch/558c083b636f/verification/morphe-desktop-1.18.1-all.jar \
PATCHER_OUTPUT_APK=OUTPUT.apk ./patch-twitter.sh ORIGINAL.apkm \
  [--all-newx] --unsigned --continue-on-error --disable-purge --bytecode-mode FULL
```

- All: 59 applied, zero failed, rebuilt successfully; 121349965 bytes,
  SHA-256 `17133098b9a34fbf84cbd8808083ba31642c6d125f9aea7aadde7fcc151af29a`.
  [Result](x12.34-pinned-all.json), [full log](x12.34-pinned-all.log).
- Default: 53 applied, zero failed, rebuilt successfully; 120464631 bytes,
  SHA-256 `19f411828a9a6bf40f228f26435f4ec65d1ff7f2ec75dd75a612e16568490981`.
  [Result](x12.34-pinned-default.json), [full log](x12.34-pinned-default.log).

Each final APK passes 236 checks (135 pinned-native + 38 fixed/refresh + 33 glass + 30 bulk/resource):

- Pinned model construction, mask/button/style operands, native infinite duration vs result timeout,
  one native actor gate, original actor instructions/branches preserved, no replacement presentation,
  original present/dispose instructions preserved, live receiver at StateFlow publication, shared
  group-free localizer fallback preserved, Snapshot factory/get/set, text-only progress updates,
  task queue ownership, native bulk constructor/begin/progress/finish paths with no modal creation/show,
  scoped bulk cancellation, single terminal summary, keyed engine/merge outcomes and silent batch members.
- Bottom-bar host and automatic-refresh request controls, merge byte progress, native paging recovery,
  raw page lineage, empty/retry/terminal paths and no bottom success toast remain intact.
- All 621 Chinese strings are present. The new notification headings and counters are included in
  the same resource aggregation as the Chinese settings.

[Checker](PinnedBannerStaticReview.java); native [all](x12.34-pinned-all-native.json)/[default](x12.34-pinned-default-native.json),
fixed [all](x12.34-pinned-all-fixed.json)/[default](x12.34-pinned-default-fixed.json),
glass [all](x12.34-pinned-all-glass.json)/[default](x12.34-pinned-default-glass.json),
bulk [all](x12.34-pinned-all-bulk.json)/[default](x12.34-pinned-default-bulk.json).
[Tests](x12.34-pinned-tests.json), [bundle provenance](x12.34-pinned-source-provenance.json).

These are build, state-machine and final-bytecode/resource results, not device UI/runtime claims.
Native animations, visual hierarchy and foreground/background experience still need normal use
on the user's phone. No other target APK was available for full package regression.

Committed CLI logs preserve every line with trailing whitespace removed; raw captures remain in the verification working directory.

## Publication

Published release: https://github.com/YongkeDet/piko/releases/tag/v3.10.0-custom-newx.10 .
Publisher commit `b00f32dab5ee20da0fa54b14c838c7dd42922990`, publisher run `38931620157`, success.
Publication time: `2026-10-16T16:24:04Z`. The public MPP (9112989 bytes), SHA256SUMS and
source-provenance.json were downloaded again from the release. Their bytes/provenance match the
exact source-built and APK-tested bundle (`3de1e2ccbd0b38b087ff48ea88aca57c048c0a0ebf669570f5fdd76bfc0a5bef`).
Publishing and main-feed integration modify only documentation, publisher configuration and feed;
production source paths remain identical to the tested source commit.
