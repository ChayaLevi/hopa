package com.hopa.backend.board.client

import com.hopa.backend.crypto.PayloadCipher
import com.hopa.contracts.json.HopaJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import javax.crypto.AEADBadTagException

/** `spec/fixtures/family/cipher/board-cipher.v1.json`, made with another AES-GCM implementation. */
class BoardCipherTest {
    private val vectors: JsonObject = HopaJson.element.parseToJsonElement(
        File(requireNotNull(javaClass.getResource("/fixtures/family/cipher/board-cipher.v1.json")).toURI()).readText(),
    ).jsonObject

    private val cases = vectors.getValue("cases").jsonArray.map { it.jsonObject }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun place(case: JsonObject, override: JsonObject? = null) = BoardCipher.Place(
        householdId = override?.get("householdId")?.jsonPrimitive?.content ?: case.text("householdId"),
        scope = override?.get("scope")?.jsonPrimitive?.content ?: case.text("scope"),
        opId = override?.get("opId")?.jsonPrimitive?.content ?: case.text("opId"),
        keyEpoch = (override?.get("keyEpoch") ?: case.getValue("keyEpoch")).jsonPrimitive.int,
    )

    @Test
    fun `the AAD and the sealed bytes match the vectors`() {
        for (case in cases) {
            val place = place(case)
            assertArrayEquals(hex(case.text("aadHex")), BoardCipher.aad(place), case.text("name"))
            val sealed = BoardCipher.sealWithIv(
                hex(case.text("keyHex")),
                place,
                case.text("plaintext").toByteArray(),
                hex(case.text("ivHex")),
            )
            assertArrayEquals(hex(case.text("sealedHex")), sealed, case.text("name"))
            assertEquals(
                case.text("plaintext"),
                BoardCipher.open(hex(case.text("keyHex")), place, sealed).toString(Charsets.UTF_8),
            )
        }
    }

    @Test
    fun `a ciphertext opens nowhere else`() {
        for (wrong in vectors.getValue("mustNotOpen").jsonArray.map { it.jsonObject }) {
            val case = cases.single { it.text("name") == wrong.text("from") }
            assertThrows(AEADBadTagException::class.java, {
                BoardCipher.open(hex(case.text("keyHex")), place(case, wrong), hex(case.text("sealedHex")))
            }, wrong.text("name"))
        }
    }

    @Test
    fun `a board op is not a task body under the same household key`() {
        val case = cases.first()
        val key = hex(case.text("keyHex"))
        assertThrows(AEADBadTagException::class.java) { PayloadCipher.open(key, hex(case.text("sealedHex"))) }
        val task = PayloadCipher.seal(key, "a task".toByteArray())
        assertThrows(AEADBadTagException::class.java) { BoardCipher.open(key, place(case), task) }
    }

    @Test
    fun `two seals of the same op differ`() {
        val key = ByteArray(32) { 7 }
        val place = BoardCipher.Place("h", "family", "op-1", 0)
        val a = BoardCipher.seal(key, place, "x".toByteArray())
        val b = BoardCipher.seal(key, place, "x".toByteArray())
        assert(!a.contentEquals(b)) { "a fresh IV every time" }
    }
}
