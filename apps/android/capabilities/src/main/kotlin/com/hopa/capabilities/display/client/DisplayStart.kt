package com.hopa.capabilities.display.client

/**
 * The shell command that starts the helper as uid 2000.
 *
 * The classpath is the installed APK itself. That is worth knowing, because the
 * old comment here said `app_process` reads only the dex named in `CLASSPATH`
 * and library classes land in a secondary dex - so the code unzipped every dex
 * into `/data/local/tmp` and hunted for the one that carries [ShellMain]. A zip
 * on the classpath is just a `PathClassLoader`, which loads all of its dex
 * files: no copy is needed, and none is made. See ADR-0023.
 */
object DisplayStart {
    fun command(packageName: String, token: String, verbose: Boolean = false): String {
        val extra = if (verbose) " --verbose" else ""
        return "apk=\$(pm path $packageName | cut -d: -f2); " +
            "CLASSPATH=\"\$apk\" setsid app_process / " +
            "com.hopa.capabilities.display.helper.ShellMain --token=$token$extra " +
            ">/dev/null 2>&1 & echo started"
    }
}
