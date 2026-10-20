package org.ligi.survivalmanual

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ligi.survivalmanual.model.EncryptedHouseholdBackup as Crypto
import org.ligi.survivalmanual.model.HouseholdBackup
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import javax.crypto.BadPaddingException
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class EncryptedBackupTest {
    private fun fixture() = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("encrypted-backup-vector.json").bufferedReader().use { it.readText() })
    private fun bytes() = Base64.decode(fixture().getString("encrypted_base64"), Base64.DEFAULT)
    private val password get() = fixture().getString("password").toCharArray()

    @Test fun independentPythonVectorDecryptsExactlyWithUnicodePassphraseOnEveryApi() {
        val plaintext = Crypto.decrypt(bytes(), password)
        assertEquals(fixture().getString("plaintext_utf8"), plaintext.toString(Charsets.UTF_8))
        val snapshot = HouseholdBackup.read(ByteArrayInputStream(plaintext), setOf("water"))
        assertTrue(snapshot.notes.contains("Ärztin")); assertEquals(setOf("water"), snapshot.bookmarks)
    }
    @Test fun randomSaltAndNonceMakeRepeatedEncryptionDifferentAndRestorable() {
        val plain = fixture().getString("plaintext_utf8").toByteArray(Charsets.UTF_8)
        val one = Crypto.encrypt(plain, password); val two = Crypto.encrypt(plain, password)
        assertFalse(one.contentEquals(two))
        assertFalse(one.copyOfRange(12,28).contentEquals(two.copyOfRange(12,28)))
        assertFalse(one.copyOfRange(28,40).contentEquals(two.copyOfRange(28,40)))
        assertArrayEquals(plain, Crypto.decrypt(one, password))
        assertFalse(one.toString(Charsets.ISO_8859_1).contains("library"))
        // Public fixture only: host independently decrypts this Android output.
        val root=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val temp=java.io.File(root,"encrypted-interop.tmp"); temp.writeBytes(one)
        assertTrue(temp.renameTo(java.io.File(root,"encrypted-interop.oeh")))
    }
    @Test fun wrongPasswordAndTamperedSaltNonceCiphertextAndTagNeverReturnPlaintext() {
        try { Crypto.decrypt(bytes(), "incorrect passphrase".toCharArray()); fail("Wrong password accepted") }
        catch (_: BadPaddingException) { }
        for (position in listOf(12,28,Crypto.HEADER_BYTES,bytes().lastIndex)) {
            val corrupt=bytes(); corrupt[position]=(corrupt[position].toInt() xor 1).toByte()
            try { Crypto.decrypt(corrupt,password); fail("Tampering accepted at $position") }
            catch (_: BadPaddingException) { }
        }
    }
    @Test fun untrustedWorkFactorVersionLengthsTruncationAndTrailingDataAreRejectedBeforeKdf() {
        val original=bytes()
        val hugeCost=original.copyOf(); ByteBuffer.wrap(hugeCost).putInt(8,Int.MAX_VALUE)
        val downgrade=original.copyOf(); ByteBuffer.wrap(downgrade).putInt(8,1)
        val hugeLength=original.copyOf(); ByteBuffer.wrap(hugeLength).putInt(40,Int.MAX_VALUE)
        val version=original.copyOf(); version[7]='2'.code.toByte()
        for (bad in listOf(hugeCost,downgrade,hugeLength,version,original.copyOf(20),original.dropLast(1).toByteArray(),original+byteArrayOf(0))) {
            try { Crypto.validateEnvelope(bad); fail("Malformed envelope accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun oversizedAndWeakExportsAreRejectedAndReadsAreBounded() {
        try { Crypto.encrypt(ByteArray(HouseholdBackup.MAX_BYTES+1),password); fail("Oversized") } catch (_: IllegalArgumentException) { }
        for (pass in listOf("short", " ".repeat(12),"x".repeat(129))) {
            try { Crypto.encrypt(byteArrayOf(1),pass.toCharArray()); fail("Invalid password") } catch (_: IllegalArgumentException) { }
        }
        try { Crypto.readBounded(ByteArrayInputStream(ByteArray(Crypto.MAX_BYTES+1))); fail("Unbounded read") } catch (_: IllegalArgumentException) { }
    }
}
