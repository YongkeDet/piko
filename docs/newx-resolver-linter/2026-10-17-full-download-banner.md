# Full native download information and restored graphical progress

Target: com.twitter.android 12.34.0-prod.01 / 312340001, APKMirror arm64-v8a APKM.
APKM SHA-256 `016a7eb2b6d8e2d6410da7f3c60e1bb46b002f6fedd797e366b068fb2897682b`.
Base APK SHA-256 `132074017a77fc5963965368a88105b6468036c72a1c98fa27e691cb55732341`.

## Report and baseline

Complete user reports (no exception stack was supplied):

> 弹窗应当显示全部信息，不要省略。
> 下载进度不光要有百分比，还要有进度条，速度，你之前明明做了这些，为什么删去了呢？

Baseline .10 source `6b2da69f22a09107597ca4a4f0b2d35465218b79`, MPP SHA-256
`3de1e2ccbd0b38b087ff48ea88aca57c048c0a0ebf669570f5fdd76bfc0a5bef`.
NewX MPE SHA-256 `f3b8fddc466400aa408a989c3b7b9b38ad0626cf0937c91c5e963c12dfbb3216`;
shared MPE SHA-256 `ad486f4bf4537b12e4d0efaff8b809cf550d6f9eea4bff4549d481a9da105396`.
Baseline all APK SHA-256 `17133098b9a34fbf84cbd8808083ba31642c6d125f9aea7aadde7fcc151af29a`;
default `19f411828a9a6bf40f228f26435f4ec65d1ff7f2ec75dd75a612e16568490981`.
Affected patch: NewX: Unified download notifications. Classification: extension/UI integration regression.
This is unrelated to resolver-linter or cardinality-helper behavior.

## Smali evidence and change

The extension explicitly shortened ordinary progress filenames to 60 characters. Native modern
notifications limit their rich headline to three lines and detail text to one line; the fallback
also constrains its headline and detail. Overflow uses native Ellipsis. A speed line existed but could
be hidden by these constraints. The native adapter omitted the earlier graphical progress bar.

The fix removes filename shortening and resolves native text sites by the API model receiver,
Material3 text contract and the ordered overflow/softWrap/maxLines/minLines tuple. Only notification
models registered by this extension get Clip, softWrap=true and unlimited lines. Those are also the
native default values, so default masks and changed flags remain untouched. Other native messages
retain their original operands and flow. Live model aliases and wide-argument offsets are derived
from actual bytecode; overwritten parameters fail closed.

A native Material3 horizontal progress indicator is inserted into the existing text column after
the detail body, once per renderer. Its Float Snapshot state and Function0 callback belong
to the same task/model as the text. A known total draws determinate progress; an unknown total or
pagination wait draws the native animated indeterminate bar. Colors use the existing theme accent
and a translucent track, packed through the APK's native Color function. The bar fills the available
text-column width. It neither creates a second card nor re-presents the notification.

Bulk downloads show current-transfer percentage, the same native bar, and size/speed throughout
pagination. Counts and recovery/context text remain available and wrap without ellipsis. The
percentage describes current transfers, not undiscovered profile media. Result ordering, task IDs,
cancellation isolation and the single bulk summary remain intact. Native UI animations and device
layout still require use on the user's phone; no device was controlled.

Tests extend UnifiedDownloadNotificationPatchTest (wide text operands, ownership branch, lost
model alias, Float/long progress ABI and determinate/indeterminate branches) and existing downloader
queue tests (owned result vs progress state). No local bytecode/linter library changes.


## Initial validation failures and regression fixture

Source `81134d96ea529a14b18f95e050141e72a4e309be`, CI 38935711159 failed only the
new emitted-instruction fixture: `const/4 v16`/`v17` cannot encode high registers.
Using `const/16` fixes that test setup. Source `6178458f3b3c25c308fc2857dfb4521591115c16`,
CI 39001709822 then passed (88 patch and 279 extension tests).

Its actual all-NewX APKM patch test exposed a real resolver error: both modern and fallback
layouts contain a rich headline AND detail, whereas the first adapter expected a single
rich headline globally. The shared cardinality helper correctly rejected two matches; this
was resolver logic, not a helper or linter bug. Both layouts now resolve headline/detail
cardinality within each renderer. The existing emitted fixture contains both layouts so this
same error cannot pass regression unnoticed. Full original error and command output:
[x12.34-full-first-failure.log](x12.34-full-first-failure.log).

Failed artifact: CI bundle artifact 12089491094, ZIP SHA-256
`cc6fd5d3c95b3e72444096b611890c31d6c2f92dcb1bd6b66afd50a6615eacdd`;
MPP `d611be44cba491638a0d7562bcf80e02f24d540303bda0135b34981b8425e694`;
NewX MPE `6f05f37111ae56324f28d9b61cd01fab7aa69e11c619499e520e38d5d532f8be`;
shared MPE `ad486f4bf4537b12e4d0efaff8b809cf550d6f9eea4bff4549d481a9da105396`.
Failed output: `build/newx-full-banner-first.apk`; validation was explicitly rejected by
`patch-twitter.sh` because the result report contains a failed required patch.

Command (official Desktop 1.18.1, Java 21.0.12.1):
```bash
JAVA_TOOL_OPTIONS='-XX:-UsePerfData -Xshare:off' \
PATCHER_JAVA=/workspace/scratch/558c083b636f/verification/jdk-21.0.12.1+1-jre/bin/java \
MORPHE_CLI_JAR=/workspace/scratch/558c083b636f/verification/morphe-desktop-1.18.1-all.jar \
PATCHER_OUTPUT_APK=/workspace/scratch/558c083b636f/piko/build/newx-full-banner-first.apk \
./patch-twitter.sh /workspace/scratch/558c083b636f/recovered-input/com.twitter.android_12.34.0-prod.01-312340001_1arch_2dpi_24lang_0d5e5dfcd6d1ce2f7899e98625ad1541_apkmirror.com.apkm \
  --all-newx --unsigned --continue-on-error --disable-purge --bytecode-mode FULL
```
