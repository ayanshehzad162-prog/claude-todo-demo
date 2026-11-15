#!/usr/bin/env bash
# CleanYourTV helper. Run on your PC with adb connected to the TV.
# Everything here is reversible (restore / enable) and needs no root.
set -euo pipefail

LAUNCHER_PKG="com.cleanyourtv.launcher"

usage() {
  cat <<EOF
Usage: scripts/tv.sh <command> [args]

  info                    Show brand, model and Android version
  list                    Save all installed packages to packages_all.txt
  home-candidates         Show which apps currently handle the Home button
  install <apk>           Install the launcher APK
  set-home                Make CleanYourTV the default Home app
  remove <file>           Uninstall (for user 0) every package listed in <file>
  restore <file>          Bring back packages removed with 'remove'
  disable-stock <pkg>     Disable the stock launcher (only after set-home works!)
  enable-stock <pkg>      Re-enable a disabled stock launcher
EOF
}

need_device() { adb get-state >/dev/null 2>&1 || { echo "No adb device. Run: adb connect <TV-IP>:5555"; exit 1; }; }

read_pkgs() { grep -vE '^\s*(#|$)' "$1" | awk '{print $1}'; }

cmd="${1:-}"; shift || true
case "$cmd" in
  info)
    need_device
    for p in ro.product.brand ro.product.model ro.build.version.release ro.build.version.sdk; do
      printf '%s = %s\n' "$p" "$(adb shell getprop "$p" | tr -d '\r')"
    done ;;
  list)
    need_device
    adb shell pm list packages | tr -d '\r' | sed 's/^package://' | sort > packages_all.txt
    echo "Saved $(wc -l < packages_all.txt) packages to packages_all.txt" ;;
  home-candidates)
    need_device
    adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN ;;
  install)
    need_device
    adb install -r "${1:?apk path required}" ;;
  set-home)
    need_device
    adb shell cmd package set-home-activity "$LAUNCHER_PKG/.MainActivity" \
      || echo "If this failed, press Home on the TV and choose CleanYourTV -> Always." ;;
  remove)
    need_device
    while read -r pkg; do
      [ -z "$pkg" ] && continue
      printf 'Removing %s ... ' "$pkg"
      adb shell pm uninstall -k --user 0 "$pkg" | tr -d '\r'
    done < <(read_pkgs "${1:?package list file required}") ;;
  restore)
    need_device
    while read -r pkg; do
      [ -z "$pkg" ] && continue
      printf 'Restoring %s ... ' "$pkg"
      adb shell cmd package install-existing --user 0 "$pkg" | tr -d '\r'
    done < <(read_pkgs "${1:?package list file required}") ;;
  disable-stock)
    need_device
    adb shell pm disable-user --user 0 "${1:?package required}" ;;
  enable-stock)
    need_device
    adb shell pm enable "${1:?package required}" ;;
  *) usage ;;
esac
