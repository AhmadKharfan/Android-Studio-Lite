#!/usr/bin/env bash
# Runs the shell embedded in the managed build workflow against fixtures, so a change to the workflow
# that breaks input validation or output collection fails CI instead of every user's build.
# Needs bash, jq, sha256sum and GNU stat (all present on ubuntu runners).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workflow="$here/../../main/resources/com/ahmadkharfan/androidstudiolite/data/githubactions/workflow/asl-build.yml"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Prints the `run: |` body of the step named $1, with the block indentation removed.
step_script() {
  awk -v step="- name: $1" '
    index($0, step) { in_step = 1; next }
    in_step && /^      - name: / { exit }
    in_step && /run: \|/ { in_run = 1; next }
    in_step && in_run { print }
  ' "$workflow" | sed 's/^          //'
}

failures=0
expect() { # expect <ok|reject> <label> <env assignments...>
  local want="$1" label="$2"; shift 2
  if env "$@" RUNNER_TEMP="$work/runner" GITHUB_ENV="$work/github_env" bash "$work/validate.sh" > /dev/null 2>&1; then
    got=ok
  else
    got=reject
  fi
  if [ "$got" != "$want" ]; then
    echo "FAIL: validate $label: expected $want, got $got"
    failures=$((failures + 1))
  fi
}

mkdir -p "$work/runner"
step_script "Validate request" > "$work/validate.sh"
[ -s "$work/validate.sh" ] || { echo "FAIL: Validate request step not found"; exit 1; }

sha=0123456789abcdef0123456789abcdef01234567
base=(ASL_CORRELATION_ID=5f0c1e2a-0000 ASL_SOURCE_REF=asl/src/0a1b2c ASL_SOURCE_SHA=$sha ASL_JAVA_VERSION=17)
expect ok "single task" "${base[@]}" "ASL_TASKS=:app:assembleDebug"
expect ok "several tasks" "${base[@]}" "ASL_TASKS=:app:assembleDebug :lib:bundleRelease"
expect ok "root task" "${base[@]}" "ASL_TASKS=assembleDebug"
expect reject "command injection" "${base[@]}" "ASL_TASKS=:app:assembleDebug;id"
expect reject "substitution" "${base[@]}" 'ASL_TASKS=$(id)'
expect reject "no tasks" "${base[@]}" "ASL_TASKS="
expect reject "too many tasks" "${base[@]}" "ASL_TASKS=a b c d e f g h i"
expect reject "foreign ref" ASL_CORRELATION_ID=5f0c1e2a-0000 ASL_SOURCE_REF=main ASL_SOURCE_SHA=$sha \
  ASL_JAVA_VERSION=17 ASL_TASKS=assembleDebug
expect reject "short sha" ASL_CORRELATION_ID=5f0c1e2a-0000 ASL_SOURCE_REF=asl/src/0a1b2c ASL_SOURCE_SHA=abc \
  ASL_JAVA_VERSION=17 ASL_TASKS=assembleDebug
expect reject "unsupported jdk" ASL_CORRELATION_ID=5f0c1e2a-0000 ASL_SOURCE_REF=asl/src/0a1b2c \
  ASL_SOURCE_SHA=$sha ASL_JAVA_VERSION=8 ASL_TASKS=assembleDebug

step_script "Collect outputs" > "$work/collect.sh"
[ -s "$work/collect.sh" ] || { echo "FAIL: Collect outputs step not found"; exit 1; }
project="$work/project"
mkdir -p "$project/app/build/outputs/apk/debug" "$project/app/build/outputs/apk/androidTest/debug" \
  "$project/app/build/outputs/bundle/release" "$work/out/artifacts"
printf 'apk' > "$project/app/build/outputs/apk/debug/app-debug.apk"
printf 'test' > "$project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
printf 'bundle' > "$project/app/build/outputs/bundle/release/app-release.aab"
printf '{}' > "$project/app/build/outputs/apk/debug/output-metadata.json"
(cd "$project" && ASL_OUT="$work/out" ASL_BUILD_OUTCOME=success bash "$work/collect.sh" > /dev/null)

result="$work/out/asl-result.json"
check_json() { # check_json <label> <jq filter that must be true>
  if ! jq -e "$2" "$result" > /dev/null; then
    echo "FAIL: result $1"
    failures=$((failures + 1))
  fi
}
check_json "protocol" '.protocol == 1'
check_json "success" '.success == true'
check_json "artifact count" '.artifacts | length == 2'
check_json "apk entry" '.artifacts | any(.name == "app-debug.apk" and .kind == "APK" and .sizeBytes == 3)'
check_json "aab entry" '.artifacts | any(.name == "app-release.aab" and .kind == "AAB")'
check_json "apk checksum" ".artifacts[] | select(.name == \"app-debug.apk\") | .sha256 == \"$(printf 'apk' | sha256sum | cut -d' ' -f1)\""
[ -f "$work/out/artifacts/app-debug.apk" ] || { echo "FAIL: apk not copied"; failures=$((failures + 1)); }
[ ! -e "$work/out/artifacts/app-debug-androidTest.apk" ] || { echo "FAIL: test apk copied"; failures=$((failures + 1)); }

rm -rf "$work/out" && mkdir -p "$work/out/artifacts"
(cd "$project" && ASL_OUT="$work/out" ASL_BUILD_OUTCOME=failure bash "$work/collect.sh" > /dev/null)
check_json "failed build reported" '.success == false'

# The Build step runs Gradle in the background and republishes the log tail to a check run.
step_script "Build" > "$work/build.sh"
[ -s "$work/build.sh" ] || { echo "FAIL: Build step not found"; exit 1; }
bin="$work/bin"
mkdir -p "$bin" "$work/live/out" "$work/live/project"
cat > "$bin/gh" <<'STUB'
#!/usr/bin/env bash
# Records the last check-run update the step published.
cat > "$ASL_OUT/last-publish.json"
STUB
chmod +x "$bin/gh"
run_build() { # run_build <exit code of the fake gradle>
  printf '#!/usr/bin/env bash\nfor i in 1 2 3 4 5; do echo "line $i"; done\nexit %s\n' "$1" > "$work/live/project/gradlew"
  chmod +x "$work/live/project/gradlew"
  (cd "$work/live/project" && PATH="$bin:$PATH" ASL_OUT="$work/live/out" ASL_TASKS=assembleDebug \
    ASL_CHECK_RUN_ID=1 GITHUB_REPOSITORY=octo/asl-build bash "$work/build.sh" > /dev/null 2>&1)
}
if ! run_build 0; then
  echo "FAIL: live build step failed for a successful build"; failures=$((failures + 1))
fi
if ! jq -e '.output.summary == "asl-lines 5" and (.output.text | endswith("line 5\n"))' \
  "$work/live/out/last-publish.json" > /dev/null; then
  echo "FAIL: live log did not publish the final tail"; failures=$((failures + 1))
fi
if run_build 3; then
  echo "FAIL: live build step hid a gradle failure"; failures=$((failures + 1))
fi

if [ "$failures" -gt 0 ]; then
  echo "$failures workflow script check(s) failed"
  exit 1
fi
echo "Workflow script checks passed"
