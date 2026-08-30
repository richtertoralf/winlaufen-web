#!/usr/bin/env bash
# Statische und lokale Logiktests für die Tag-basierte Release-Pipeline.
set -uo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repository_root=$(cd -- "$script_dir/../.." && pwd -P)
workflow="$repository_root/.github/workflows/release.yml"
verifier="$repository_root/installer/common/verify-release-tag.sh"

passed=0
failed=0
work=$(mktemp -d /tmp/winlaufen-release-workflow-tests.XXXXXX) || exit 1
trap 'rm -rf -- "$work"' EXIT HUP INT TERM

ok()  { printf 'PASS  %s\n' "$1"; passed=$((passed + 1)); }
bad() { printf 'FAIL  %s\n        %s\n' "$1" "${2:-}" >&2; failed=$((failed + 1)); }
assert_contains() {
    grep -qF -- "$2" "$1" 2>/dev/null && ok "$3" || bad "$3" "'$2' fehlt in $1"
}

write_reactor() {
    local root=$1 root_version=$2 contract_version=$3 bridge_version=$4 live_version=$5
    mkdir -p "$root/contract" "$root/bridge" "$root/live-server"
    printf '<project>\n  <version>%s</version>\n  <modules>\n    <module>contract</module>\n    <module>bridge</module>\n    <module>live-server</module>\n  </modules>\n</project>\n' \
        "$root_version" > "$root/pom.xml"
    printf '<project><parent><version>%s</version></parent><artifactId>contract</artifactId></project>\n' \
        "$contract_version" > "$root/contract/pom.xml"
    printf '<project><parent><version>%s</version></parent><artifactId>bridge</artifactId></project>\n' \
        "$bridge_version" > "$root/bridge/pom.xml"
    printf '<project><parent><version>%s</version></parent><artifactId>live-server</artifactId></project>\n' \
        "$live_version" > "$root/live-server/pom.xml"
}

echo "=== Release-Workflow ==="
[[ -f "$workflow" ]] && ok "Release-Workflow vorhanden" || bad "Release-Workflow vorhanden" "$workflow fehlt"
bash -n "$verifier" && ok "Release-Versionsprüfung ist syntaktisch gültig" \
    || bad "Release-Versionsprüfung ist syntaktisch gültig"

if python3 - "$workflow" <<'PY'
import sys
import yaml

with open(sys.argv[1], encoding="utf-8") as handle:
    workflow = yaml.load(handle, Loader=yaml.BaseLoader)

assert isinstance(workflow, dict)
assert workflow["on"]["push"]["tags"] == ["v*.*.*"]
jobs = workflow["jobs"]
assert set(jobs) == {"validate", "linux-package", "windows-package", "publish"}
assert jobs["linux-package"]["needs"] == "validate"
assert jobs["windows-package"]["needs"] == "validate"
assert jobs["publish"]["needs"] == ["validate", "linux-package", "windows-package"]
PY
then
    ok "Release-Workflow ist gültiges YAML mit der erwarteten Jobstruktur"
else
    bad "Release-Workflow ist gültiges YAML mit der erwarteten Jobstruktur"
fi

actionlint_bin=$(command -v actionlint 2>/dev/null || true)
if [[ -z "$actionlint_bin" && -x /tmp/actionlint ]]; then
    actionlint_bin=/tmp/actionlint
fi
if [[ -n "$actionlint_bin" ]]; then
    "$actionlint_bin" "$workflow" >/dev/null \
        && ok "actionlint akzeptiert den Release-Workflow" \
        || bad "actionlint akzeptiert den Release-Workflow"
else
    printf 'HINWEIS  actionlint nicht verfügbar; strukturierter YAML-Parser wurde ausgeführt.\n'
fi

assert_contains "$workflow" 'tags:' "Workflow reagiert auf Tags"
assert_contains "$workflow" '"v*.*.*"' "Workflow filtert vX.Y.Z-Tags"
assert_contains "$workflow" 'ref: ${{ github.ref }}' "Checkout verwendet den auslösenden Tag"
assert_contains "$workflow" 'git rev-parse HEAD' "Workflow prüft den exakten Source-Commit"
assert_contains "$workflow" 'java-version: "25"' "Workflow verwendet JDK 25"
assert_contains "$workflow" './mvnw -B clean package' "Linux-Build verwendet den Maven Wrapper"
assert_contains "$workflow" '.\mvnw.cmd -B clean package' "Windows-Build verwendet den Maven Wrapper"
assert_contains "$workflow" './installer/tests/run-installer-tests.sh' "Installer-Tests blockieren den Release"
assert_contains "$workflow" './devtools/smoke-fanout.sh' "Fan-out-Smoke-Test blockiert den Release"
assert_contains "$workflow" 'build-dist.sh --skip-build --with-runtime' "Linux-Paket nutzt das bestehende Dist-Skript"
assert_contains "$workflow" 'build-dist.ps1 -SkipBuild -WithRuntime' "Windows-Paket nutzt das bestehende Dist-Skript"
assert_contains "$workflow" 'linux-amd64.tar.gz' "Linux-Asset ist eindeutig benannt"
assert_contains "$workflow" 'windows-x64.zip' "Windows-Asset ist eindeutig benannt"
assert_contains "$workflow" 'sha256sum winlaufen-web-* > SHA256SUMS' "Workflow erzeugt SHA256SUMS"
assert_contains "$workflow" 'needs:' "Veröffentlichung hängt von erfolgreichen Vorjobs ab"
assert_contains "$workflow" 'gh release create' "Workflow erstellt erst am Ende das GitHub Release"

release_reactor="$work/release-reactor"
module_snapshot_reactor="$work/module-snapshot-reactor"
module_mismatch_reactor="$work/module-mismatch-reactor"
root_snapshot_reactor="$work/root-snapshot-reactor"
write_reactor "$release_reactor" 1.2.3 1.2.3 1.2.3 1.2.3
write_reactor "$module_snapshot_reactor" 1.2.3 1.2.3-SNAPSHOT 1.2.3 1.2.3
write_reactor "$module_mismatch_reactor" 1.2.3 1.2.3 1.2.4 1.2.3
write_reactor "$root_snapshot_reactor" 1.2.3-SNAPSHOT 1.2.3-SNAPSHOT 1.2.3-SNAPSHOT 1.2.3-SNAPSHOT

if "$verifier" v1.2.3 "$release_reactor" >/dev/null 2>&1; then
    ok "Passender Release-Tag und vollständiger Reaktor werden akzeptiert"
else
    bad "Passender Release-Tag und vollständiger Reaktor werden akzeptiert"
fi
if ! "$verifier" v1.2.3 "$module_snapshot_reactor" >/dev/null 2>&1; then
    ok "Snapshot-Parent in einem Modul wird abgelehnt"
else
    bad "Snapshot-Parent in einem Modul wird abgelehnt"
fi
if ! "$verifier" v1.2.3 "$module_mismatch_reactor" >/dev/null 2>&1; then
    ok "Abweichende Parent-Version eines Moduls wird abgelehnt"
else
    bad "Abweichende Parent-Version eines Moduls wird abgelehnt"
fi
if ! "$verifier" v1.2.4 "$release_reactor" >/dev/null 2>&1; then
    ok "Abweichende Tag-/POM-Version wird abgelehnt"
else
    bad "Abweichende Tag-/POM-Version wird abgelehnt"
fi
if ! "$verifier" v1.2.3 "$root_snapshot_reactor" >/dev/null 2>&1; then
    ok "Snapshot-Reaktor wird abgelehnt"
else
    bad "Snapshot-Reaktor wird abgelehnt"
fi
if ! "$verifier" v1.2.3-rc1 "$release_reactor" >/dev/null 2>&1; then
    ok "Prerelease-Tagformat wird abgelehnt"
else
    bad "Prerelease-Tagformat wird abgelehnt"
fi
if ! "$verifier" release-1.2.3 "$release_reactor" >/dev/null 2>&1; then
    ok "Ungültiges Tagformat wird abgelehnt"
else
    bad "Ungültiges Tagformat wird abgelehnt"
fi

echo
printf 'Release-Workflow-Tests: %s bestanden, %s fehlgeschlagen\n' "$passed" "$failed"
((failed == 0)) || exit 1
