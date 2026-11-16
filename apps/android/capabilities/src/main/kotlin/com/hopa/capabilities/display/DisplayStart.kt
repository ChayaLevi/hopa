package com.hopa.capabilities.display

/**
 * The shell command that starts the helper. It has to find the dex that carries
 * [ShellMain] (library classes land in classes2 or later, and app_process reads
 * only the dex named in CLASSPATH) and hand it the token from [DisplayToken].
 */
object DisplayStart {
    fun command(packageName: String, token: String): String {
        val work = "/data/local/tmp/hopa-dex"
        return "apk=\$(pm path $packageName | cut -d: -f2); " +
            "rm -rf $work; mkdir -p $work; cd $work; " +
            "unzip -o -q \"\$apk\" 'classes*.dex'; " +
            "for f in classes*.dex; do " +
            "if grep -q com/hopa/capabilities/display/ShellMain \"\$f\"; then " +
            "CLASSPATH=$work/\$f setsid app_process / com.hopa.capabilities.display.ShellMain " +
            "--token=$token >/dev/null 2>&1 & echo started; break; fi; done"
    }
}
