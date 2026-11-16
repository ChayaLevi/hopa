package hopa.brain.knowledge
import hopa.brain.routing.KnowledgeEntry


import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads HarmonyOS app knowledge from Markdown files with a small, strict
 * front-matter block. No YAML library is required.
 *
 * Format (one file per app, the same file every platform reads):
 * ```
 * ---
 * name: settings
 * display: Settings
 * aliases: [设置, settings]
 * pace: careful
 * validated: pending
 * harmony: com.huawei.hmos.settings
 * harmony_ability: MainAbility
 * harmony_verified: Mate 80, 2026-11-08
 * android: com.android.settings
 * android_activity:
 * android_verified:
 * windows:
 * windows_window:
 * windows_verified:
 * ---
 * ```
 *
 * An empty `harmony:` means the app has no HarmonyOS build yet, so this
 * service never launches it.
 */
data class KnowledgeDocument(
    val entry: KnowledgeEntry,
    val body: String,
    val pace: String,
    val validated: String,
    val platform: String,
)

class KnowledgeFormatException(message: String) : IllegalArgumentException(message)

object KnowledgeLoader {
    fun load(directory: Path): List<KnowledgeDocument> {
        val documents = mutableListOf<KnowledgeDocument>()
        Files.walk(directory).use { stream ->
            stream
                .filter { it.fileName.toString() == "APP.md" }
                .forEach { path ->
                    documents.add(parse(Files.readString(path), path.toString()))
                }
        }
        return documents.sortedBy { it.entry.name }
    }

    fun parse(text: String, source: String = "<memory>"): KnowledgeDocument {
        val normalised = text.replace("\r\n", "\n")
        if (!normalised.startsWith("---\n")) {
            throw KnowledgeFormatException("$source: missing front matter")
        }
        val end = normalised.indexOf("\n---", 4)
        if (end < 0) {
            throw KnowledgeFormatException("$source: front matter is not closed")
        }

        val front = normalised.substring(4, end)
        val body = normalised.substring(end + 4).trimStart('\n')
        val fields = mutableMapOf<String, String>()

        for (line in front.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colon = trimmed.indexOf(':')
            if (colon <= 0) {
                throw KnowledgeFormatException("$source: invalid line: $trimmed")
            }
            fields[trimmed.substring(0, colon).trim()] =
                trimmed.substring(colon + 1).trim()
        }

        fun required(key: String): String =
            fields[key] ?: throw KnowledgeFormatException("$source: missing $key")

        // One APP.md serves every platform. `harmony:` is the bundle this
        // service can launch; it is empty when the app has no HarmonyOS build.
        val bundle = fields["harmony"] ?: fields["bundle"].orEmpty()
        // UNCONFIRMED means the app is catalogued but no HarmonyOS bundle has
        // been verified on a device. It must never be used to launch anything.
        val packages = if (bundle.isBlank() ||
            bundle.equals("UNCONFIRMED", ignoreCase = true) ||
            bundle.equals("unknown", ignoreCase = true)
        ) {
            emptyList()
        } else {
            listOf(bundle)
        }
        val entry = KnowledgeEntry(
            name = required("name"),
            display = required("display"),
            aliases = parseList(fields["aliases"]),
            packages = packages,
            service = fields["harmony_ability"]?.ifBlank { null }
                ?: fields["service"]?.ifBlank { null }
                ?: required("name"),
        )
        return KnowledgeDocument(
            entry = entry,
            body = body,
            pace = required("pace"),
            validated = required("validated"),
            platform = "harmonyos",
        )
    }

    private fun parseList(raw: String?): List<String> {
        if (raw == null || raw.isEmpty()) return emptyList()
        val inner = raw.removePrefix("[").removeSuffix("]")
        return inner
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }
}
