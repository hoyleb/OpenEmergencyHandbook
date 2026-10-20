package org.ligi.survivalmanual.model

import org.bouncycastle.crypto.PBEParametersGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Version 1: fixed PBKDF2-HMAC-SHA256 parameters and authenticated AES-256-GCM.
 * No provider registration, network, device-bound key or persistent password.
 * File metadata cannot select algorithms, allocate arbitrary memory or lower KDF cost.
 */
object EncryptedHouseholdBackup {
    private val MAGIC = "OEHENC01".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 600000
    const val HEADER_BYTES = 44
    const val MAX_BYTES = HouseholdBackup.MAX_BYTES + HEADER_BYTES + 16
    const val MIN_PASSPHRASE = 12
    const val MAX_PASSPHRASE = 128

    fun isEncrypted(data: ByteArray) = data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = input.read(chunk)
            if (count == -1) break
            require(out.size() + count <= MAX_BYTES) { "Backup too large" }
            out.write(chunk, 0, count)
        }
        return out.toByteArray()
    }

    /** Validate structure before showing an unlock prompt or deriving a key. */
    fun validateEnvelope(data: ByteArray) {
        require(data.size in HEADER_BYTES + 16..MAX_BYTES && isEncrypted(data)) { "Unsupported encrypted backup" }
        val buffer = ByteBuffer.wrap(data)
        buffer.position(8)
        require(buffer.int == ITERATIONS) { "Unsupported backup parameters" }
        buffer.position(40)
        val length = buffer.int
        require(length in 16..HouseholdBackup.MAX_BYTES + 16 && data.size == HEADER_BYTES + length) { "Invalid backup length" }
    }

    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        require(password.size in 1..MAX_PASSPHRASE) { "Invalid passphrase length" }
        // BC's documented UTF-8 conversion preserves the exact passphrase on old
        // Android too. Do not trim or normalize; Unicode/spaces are significant.
        val bytes = PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password)
        return try {
            val kdf = PKCS5S2ParametersGenerator(SHA256Digest())
            kdf.init(bytes, salt, ITERATIONS)
            (kdf.generateDerivedParameters(256) as KeyParameter).key
        } finally { bytes.fill(0) }
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size <= HouseholdBackup.MAX_BYTES) { "Backup too large" }
        require(password.size in MIN_PASSPHRASE..MAX_PASSPHRASE && password.any { !it.isWhitespace() }) { "Use a longer passphrase" }
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(ITERATIONS)
            .put(salt).put(nonce).putInt(plaintext.size + 16).array()
        val derived = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(plaintext)
        } finally { derived.fill(0) }
    }

    /** No plaintext leaves this method before the authentication tag is checked. */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        validateEnvelope(data)
        val derived = key(password, data.copyOfRange(12, 28))
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, data.copyOfRange(28, 40)))
            cipher.updateAAD(data.copyOfRange(0, HEADER_BYTES))
            cipher.doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
        } finally { derived.fill(0) }
    }
}
