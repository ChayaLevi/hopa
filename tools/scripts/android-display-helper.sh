#!/system/bin/sh
# Start the hidden-display helper as shell (uid 2000).
#
# The helper class sits in a library module, so R8 keeps it but D8 puts it in a
# secondary dex (classes2.dex or later). `app_process` only reads the dex named
# in CLASSPATH, so the right classesN.dex is extracted first. The keep rule in
# apps/android/app/proguard-rules.pro is what stops R8 removing the class.
set -e
pkg=com.hopa.android
work=/data/local/tmp/hopa-dex
log=/data/local/tmp/hopa-display.log
apk=$(pm path "$pkg" | cut -d: -f2)
if [ -z "$apk" ]; then
  echo "no apk for $pkg"
  exit 1
fi
rm -rf "$work"
mkdir -p "$work"
cd "$work"
unzip -o -q "$apk" 'classes*.dex'
dex=""
for f in classes*.dex; do
  if grep -q 'com/hopa/capabilities/display/ShellMain' "$f"; then
    dex="$work/$f"
    break
  fi
done
if [ -z "$dex" ]; then
  echo "no dex carries ShellMain: rebuild with the keep rule"
  exit 1
fi
CLASSPATH="$dex" setsid app_process / com.hopa.capabilities.display.ShellMain >"$log" 2>&1 &
sleep 1
if grep -q hopa-display /proc/net/unix; then
  echo "display ready"
else
  echo "display failed"
  cat "$log"
  exit 1
fi
