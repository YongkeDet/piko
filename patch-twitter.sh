#!/usr/bin/env bash
# Build or verify the current NewX bundle, then patch an explicitly supplied original APKM.
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT_DIR"

JAVA_BIN="${PATCHER_JAVA:-java}"
CLI_JAR="${MORPHE_CLI_JAR:?Set MORPHE_CLI_JAR to an official Morphe Desktop jar}"
PATCHER_MAX_HEAP_MB="${PATCHER_MAX_HEAP_MB:-4096}"
OUTPUT_APK="${PATCHER_OUTPUT_APK:-$ROOT_DIR/build/newx-patched.apk}"
RESULT_FILE="${PATCHER_RESULT_FILE:-${OUTPUT_APK%.apk}-result.json}"
APK=""
ALL_NEWX=false
FLAGS=()

for arg in "$@"; do
  case "$arg" in
    --all-newx) ALL_NEWX=true ;;
    --install|--fastdeploy)
      echo 'This validation script does not install or control devices.' >&2
      exit 2
      ;;
    *.apk|*.apkm|*.apks) APK="$arg" ;;
    *) FLAGS+=("$arg") ;;
  esac
done
if [[ ! -f "$APK" || ! -f "$CLI_JAR" || ! "$PATCHER_MAX_HEAP_MB" =~ ^[1-9][0-9]*$ ]]; then
  echo 'Usage: ./patch-twitter.sh ORIGINAL.apkm [--all-newx] [Morphe patch options]' >&2
  exit 2
fi

VERSION=$(awk -F= '/^version[[:space:]]*=/ {gsub(/[[:space:]]/, "", $2); print $2}' gradle.properties)
MPP="patches/build/libs/patches-${VERSION}.mpp"
PROVENANCE="patches/build/libs/source-provenance.json"
if [[ -f "$PROVENANCE" ]]; then
  # CI builds the exact commit before uploading this pair. Refuse stale bundles after edits.
  python3 - "$MPP" "$PROVENANCE" <<'PY'
import hashlib, json, pathlib, subprocess, sys
bundle, record = map(pathlib.Path, sys.argv[1:])
metadata = json.loads(record.read_text())
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
if metadata['source_sha'] != head or metadata['sha256'] != hashlib.sha256(bundle.read_bytes()).hexdigest():
    raise SystemExit('Bundle provenance differs from the current checkout; rebuild before testing.')
sources = ['patches/src', 'extensions', 'gradle', 'gradle.properties', 'settings.gradle.kts', 'build.gradle.kts']
subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--', *sources], check=True)
untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', *sources], text=True)
if untracked.strip():
    raise SystemExit('Unbuilt source files are present; commit and rebuild before testing.')
PY
else
  ./gradlew :patches:build --no-daemon
fi

mkdir -p "$(dirname -- "$OUTPUT_APK")" "$(dirname -- "$RESULT_FILE")"
SELECTION=()
if [[ "$ALL_NEWX" == true ]]; then
  PATCH_LIST=$(mktemp)
  trap 'rm -f "$PATCH_LIST"' EXIT
  "$JAVA_BIN" -jar "$CLI_JAR" list-patches --patches "$MPP" -f com.twitter.android --out "$PATCH_LIST"
  mapfile -t PATCH_NAMES < <(sed -n 's/^Name: \(NewX: .*\)/\1/p' "$PATCH_LIST")
  if [[ "${#PATCH_NAMES[@]}" -eq 0 ]]; then
    echo 'No NewX patches were found in the built bundle.' >&2
    exit 1
  fi
  SELECTION+=(--exclusive)
  for patch_name in "${PATCH_NAMES[@]}"; do SELECTION+=(-e "$patch_name"); done
fi

"$JAVA_BIN" "-Xmx${PATCHER_MAX_HEAP_MB}m" -jar "$CLI_JAR" patch \
  -p "$MPP" "${SELECTION[@]}" --striplibs=arm64-v8a \
  -o "$OUTPUT_APK" -r "$RESULT_FILE" "${FLAGS[@]}" -- "$APK"

python3 - "$RESULT_FILE" <<'PY'
import json, sys
result = json.load(open(sys.argv[1]))
if result.get('failedPatches') or not result.get('appliedPatches'):
    raise SystemExit('Patch verification failed; inspect the result report.')
if not any(s['step'] == 'REBUILDING' and s['success'] for s in result['patchingSteps']):
    raise SystemExit('The APK was not rebuilt successfully.')
print(f"Verified {len(result['appliedPatches'])} applied patches and a successful APK rebuild.")
PY
