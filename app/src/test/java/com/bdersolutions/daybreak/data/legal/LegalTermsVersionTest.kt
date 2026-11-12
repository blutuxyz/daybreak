package com.bdersolutions.daybreak.data.legal

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Users are asked to accept again only when [LegalTerms.VERSION] goes up. This test makes sure it
 * does: it hashes the section texts (German and English) and compares them with the hash recorded
 * for the current version, so the text cannot change without the version changing with it.
 *
 * When this fails because you edited the text on purpose: raise [LegalTerms.VERSION], then add the
 * new version and the hash from the failure message to [HASH_BY_VERSION]. Keep the old entries.
 * The contact address is hashed as a placeholder, so changing only the address needs no new version.
 */
class LegalTermsVersionTest {
    private val sectionKeys = listOf(
        "what", "privacy", "stored", "sent", "rights", "terms", "liability", "changes", "contact",
    )

    @Test
    fun theSectionListCoversTheKeysThisTestHashes() {
        assertEquals(sectionKeys.size, LegalTerms.sections.size)
    }

    @Test
    fun theTextOnlyChangesTogetherWithTheVersion() {
        val expected = HASH_BY_VERSION[LegalTerms.VERSION]
        assertNotNull(
            "LegalTerms.VERSION is ${LegalTerms.VERSION} but HASH_BY_VERSION has no entry for it. " +
                "Add ${LegalTerms.VERSION} to HASH_BY_VERSION with hash ${currentHash()}.",
            expected,
        )
        assertEquals(
            "The privacy and terms text changed but LegalTerms.VERSION is still ${LegalTerms.VERSION}. " +
                "Raise LegalTerms.VERSION and record hash ${currentHash()} for the new version, " +
                "or undo the text change.",
            expected,
            currentHash(),
        )
    }

    @Test
    fun theContactSectionNamesTheContactAddress() {
        listOf("values", "values-en").forEach { dir ->
            val text = File("src/main/res/$dir/strings.xml").readText(Charsets.UTF_8)
            val body = Regex("""<string name="legal_contact_body">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.groupValues?.get(1).orEmpty()
            assertTrue("$dir legal_contact_body must contain ${LegalTerms.CONTACT_EMAIL}", LegalTerms.CONTACT_EMAIL in body)
        }
    }

    private fun currentHash(): String {
        val pattern = Regex("""<string name="(legal_[a-z_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val wanted = sectionKeys.flatMap { listOf("legal_${it}_title", "legal_${it}_body") }.toSet()
        val lines = listOf("values", "values-en").flatMap { dir ->
            val file = File("src/main/res/$dir/strings.xml")
            val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
            pattern.findAll(text)
                .filter { it.groupValues[1] in wanted }
                .map { "$dir/${it.groupValues[1]}=${it.groupValues[2].replace(LegalTerms.CONTACT_EMAIL, "{CONTACT_EMAIL}")}" }
                .toList()
        }.sorted()
        assertEquals("Every section needs a title and a body in both languages.", wanted.size * 2, lines.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val HASH_BY_VERSION = mapOf(
            1 to "a2d2a15004c743e8ef9878e9a112add0852d0020a654a5044a9819a93b357aeb",
            2 to "1e0729fc5cad65d241b457a6cd22cd3399a03785b8692ddba75bc00ab49e78e8",
            3 to "048cd236c09ffcd5c706dbb78842fd5e6589c6bcb924d996ef3fcc779687f0cc",
            4 to "15f6ea0f433188d2c3e352fdffd9765d64e8ef4dd8e28f18baf0f3302a2eb9d6",
            5 to "2adc2ef99130cbd22799fd9083c75b2902a7b26827e7c8e6d69453036d286627",
            6 to "26bdc60406356787173800ef11ab4e9d520e02caca2a45cfb795b868c3b87837",
            7 to "af1c7b81d67690d3ce29926d7c46092372f9867c454a82114b600e44cf4bf1a7",
            8 to "fbdee6f7205367e54d7991414100f64eeaacc23db67b87791170d05b69fb3586",
        )
    }
}
