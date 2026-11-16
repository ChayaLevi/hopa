#!/system/bin/sh
# Start the hidden-display helper as shell (uid 2000).
#
# The helper class sits in a library module, so D8 puts it in classes2.dex or
# later; `app_process` reads only the dex named in CLASSPATH, so the right one is
# extracted first. The helper listens on 127.0.0.1 and wants the token the app
# keeps in its private files: pass it as the first argument, or leave it out on a
# debug build and this script reads it with run-as.
set -e
pkg=com.hopa.android
work=/data/local/tmp/hopa-dex
log=/data/local/tmp/hopa-display.log
token=${1:-}
if [ -z "$token" ]; then
  token=$(run-as "$pkg" cat files/display-token 2>/dev/null | tr -d '\r\n')
fi
if [ -z "$token" ]; then
  echo "token needed: sh $0 <token> (run-as reads it on debug builds)"
  exit 1
fi
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
CLASSPATH="$dex" setsid app_process / com.hopa.capabilities.display.ShellMain \
  --token="$token" >"$log" 2>&1 &
# The helper prints this line only after the socket is bound; /proc/net/tcp is
# not readable from shell on every device, so the log is the reliable witness.
i=0
while [ "$i" -lt 20 ]; do
  if grep -q 'listening on' "$log" 2>/dev/null; then
    echo "display ready"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.5
done
echo "display failed"
cat "$log"
exit 1
