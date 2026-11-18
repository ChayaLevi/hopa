#!/system/bin/sh
# Start the hidden-display helper as shell (uid 2000).
#
# The classpath is the installed APK: a zip on the classpath is a
# PathClassLoader, which loads every dex in it, so the class in a secondary dex
# is found without copying anything to /data/local/tmp (ADR-0023).
# The helper listens on 127.0.0.1 and wants the token the app keeps in its
# private files: pass it as the first argument, or leave it out on a debug build
# and this script reads it with run-as.
set -e
pkg=com.hopa.android
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
CLASSPATH="$apk" setsid app_process / com.hopa.capabilities.display.ShellMain \
  --token="$token" --verbose >"$log" 2>&1 &
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
