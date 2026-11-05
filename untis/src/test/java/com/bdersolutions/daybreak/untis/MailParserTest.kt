package com.bdersolutions.daybreak.untis

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MailParserTest {

    @Test
    fun readsTheInboxWithSenderAndReadState() {
        val json = JsonParser.parseString(
            """{"incomingMessages":[{"id":7,"subject":"Test","contentPreview":"Hallo",""" +
                """"sender":{"displayName":"ABCD","userId":3,"className":null},""" +
                """"sentDateTime":"2026-11-04T10:00:00","hasAttachments":true,"isMessageRead":false},""" +
                """{"subject":"no id"}],"readConfirmationMessages":[]}""",
        )

        val mail = MailParser.parseList(MailFolder.INBOX, json).single()

        assertEquals(7L, mail.id)
        assertEquals("Test", mail.subject)
        assertEquals("Hallo", mail.preview)
        assertEquals(UntisMailPerson(3L, "ABCD"), mail.sender)
        assertEquals(LocalDateTime.of(2026, 11, 4, 10, 0), mail.sentAt)
        assertFalse(mail.read)
        assertTrue(mail.hasAttachments)
    }

    @Test
    fun readsTheSentFolderWithRecipientsAndANullPreview() {
        val json = JsonParser.parseString(
            """{"sentMessages":[{"id":1,"subject":"Frage","contentPreview":null,""" +
                """"recipientPersons":[{"displayName":"ZING","userId":9},{"displayName":" "}],""" +
                """"sentDateTime":"2026-11-02T08:30:00"}]}""",
        )

        val mail = MailParser.parseList(MailFolder.SENT, json).single()

        assertEquals("", mail.preview)
        assertEquals(listOf(UntisMailPerson(9L, "ZING")), mail.recipients)
        assertTrue(mail.read)
    }

    @Test
    fun anEmptyDraftFolderIsAnEmptyList() {
        val json = JsonParser.parseString("""{"draftMessages":[]}""")
        assertTrue(MailParser.parseList(MailFolder.DRAFTS, json).isEmpty())
    }

    @Test
    fun readsASingleMessageAndKeepsLineBreaks() {
        val json = JsonParser.parseString(
            """{"id":4,"subject":"S","content":"Zeile 1\nZeile 2<br>Zeile 3 &amp; mehr",""" +
                """"sender":{"displayName":"KRAB","userId":38},"sentDateTime":"2026-11-04T10:00:00"}""",
        )

        val mail = MailParser.parseMessage(json)!!

        assertEquals("Zeile 1\nZeile 2\nZeile 3 & mehr", mail.content)
        assertEquals("KRAB", mail.sender?.name)
    }

    @Test
    fun aWrongShapeGivesNothing() {
        assertNull(MailParser.parseMessage(JsonParser.parseString("[]")))
        assertTrue(MailParser.parseList(MailFolder.INBOX, JsonParser.parseString("[]")).isEmpty())
    }

    @Test
    fun readsTheNamesOfStorageAttachments() {
        val json = JsonParser.parseString(
            """{"id":9,"subject":"S","content":"x","hasAttachments":true,"attachments":[],"blobAttachment":null,""" +
                """"storageAttachments":[{"id":"abc","name":"Aufgabe.pdf"},{"id":"def"}]}""",
        )

        val mail = MailParser.parseMessage(json)!!

        assertEquals(listOf(UntisAttachment("abc", "Aufgabe.pdf")), mail.attachments)
    }


    @Test
    fun readsTheRecipientGroupsWithUserIds() {
        val json = JsonParser.parseString(
            """[{"type":"CLASS_TEACHERS","persons":[{"displayName":"HUTT","userId":26,"tags":["","D","GGP"]}]},""" +
                """{"type":"TEACHERS","persons":[{"displayName":"ZOTT","userId":70,"imageUrl":null,"tags":["RK"]},{"displayName":" ","userId":1}]},""" +
                """{"type":"OTHERS","persons":[{"displayName":"NAGL","userId":73,"tags":[]}]}]""",
        )

        val recipients = RecipientParser.parse(json)

        assertEquals(3, recipients.size)
        assertEquals(UntisRecipient(26L, "HUTT", RecipientGroup.CLASS_TEACHER, listOf("D", "GGP")), recipients[0])
        assertEquals(UntisRecipient(70L, "ZOTT", RecipientGroup.TEACHER, listOf("RK")), recipients[1])
        assertEquals(RecipientGroup.OTHER, recipients[2].group)
    }


    @Test
    fun readsTheDownloadLinkAndDropsTheHostHeader() {
        val json = JsonParser.parseString(
            """{"downloadUrl":"https://storage.example/bucket/file","additionalHeaders":[""" +
                """{"key":"x-amz-server-side-encryption-customer-algorithm","value":"AES256"},""" +
                """{"key":"host","value":"storage.example"}]}""",
        )

        val link = AttachmentLinkParser.parse(json)!!

        assertEquals("https://storage.example/bucket/file", link.url)
        assertEquals(mapOf("x-amz-server-side-encryption-customer-algorithm" to "AES256"), link.headers)
    }

    @Test
    fun aLinkWithoutAnAddressIsNothing() {
        assertNull(AttachmentLinkParser.parse(JsonParser.parseString("""{"additionalHeaders":[]}""")))
    }

}
