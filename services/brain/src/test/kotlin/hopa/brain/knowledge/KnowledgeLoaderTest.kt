package hopa.brain.knowledge

import java.nio.file.Path

private var passed = 0

private fun check(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
    passed++
}

fun main(args: Array<String>) {
    val sample = """
        ---
        name: settings
        display: Settings
        aliases: [shezhi, settings]
        pace: careful
        validated: pending
        harmony: com.huawei.hmos.settings
        harmony_ability: MainAbility
        harmony_verified: Mate 80, 2026-11-08
        android: com.android.settings
        android_activity:
        android_verified:
        windows:
        windows_window:
        windows_verified:
        ---
        # Settings

        Open Settings, then Wi-Fi, Bluetooth or Display.
    """.trimIndent()

    val document = KnowledgeLoader.parse(sample)
    check(document.entry.name == "settings", "name")
    check(document.entry.display == "Settings", "display")
    check(document.entry.packages.contains("com.huawei.hmos.settings"), "harmony bundle")
    check(document.entry.service == "MainAbility", "harmony ability is the service")
    check(document.entry.aliases.contains("shezhi"), "alias")
    check(document.pace == "careful", "pace")
    check(document.platform == "harmonyos", "platform")
    check(document.body.contains("Bluetooth"), "body")

    var missing = false
    try {
        KnowledgeLoader.parse("---\ndisplay: x\n---\n")
    } catch (error: KnowledgeFormatException) {
        missing = true
    }
    check(missing, "missing name is rejected")

    val unconfirmed = KnowledgeLoader.parse(
        "---\nname: x\ndisplay: X\nharmony: UNCONFIRMED\n" +
            "pace: careful\nvalidated: pending\n---\n# X\n",
    )
    check(unconfirmed.entry.packages.isEmpty(), "UNCONFIRMED has no launchable package")

    // An app that is only on another platform is still a valid catalog entry.
    val otherOnly = KnowledgeLoader.parse(
        "---\nname: x\ndisplay: X\nharmony:\nandroid: a.b.c\n" +
            "pace: careful\nvalidated: pending\n---\n# X\n",
    )
    check(otherOnly.entry.packages.isEmpty(), "no harmony bundle means nothing to launch")

    val directory = if (args.isNotEmpty()) {
        Path.of(args[0])
    } else {
        Path.of("brain/knowledge/apps")
    }
    val loaded = KnowledgeLoader.load(directory)
    check(loaded.size == 54, "54 app files")
    check(loaded.all { it.platform == "harmonyos" }, "all harmonyos")
    check(loaded.all { it.pace.isNotEmpty() }, "all have a pace")
    check(loaded.any { it.entry.name == "clock" }, "clock present")
    check(loaded.any { it.entry.name == "browser" }, "browser present")
    check(loaded.any { it.entry.name == "wechat" }, "wechat present")
    check(
        loaded.first { it.entry.name == "wechat" }.entry.packages.contains("com.tencent.wechat"),
        "wechat uses the verified HarmonyOS bundle",
    )
    check(
        loaded.first { it.entry.name == "bilibili" }.entry.packages.contains("yylx.danmaku.bili"),
        "bilibili uses the verified HarmonyOS bundle",
    )
    check(
        loaded.first { it.entry.name == "douyin" }.entry.packages.contains("com.ss.hm.ugc.aweme"),
        "douyin uses the verified HarmonyOS bundle",
    )
    check(
        loaded.first { it.entry.name == "weibo" }
            .entry.packages.contains("com.sina.weibo.stage"),
        "weibo uses the stage-model bundle",
    )
    check(loaded.count { it.entry.packages.isEmpty() } == 28, "28 apps have no HarmonyOS build")
    check(
        loaded.first { it.entry.name == "chrome" }.entry.packages.isEmpty(),
        "chrome has no HarmonyOS build yet",
    )

    println("KnowledgeLoaderTest: $passed assertions passed")
}
