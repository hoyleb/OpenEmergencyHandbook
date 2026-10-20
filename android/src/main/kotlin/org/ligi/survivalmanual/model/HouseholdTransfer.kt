package org.ligi.survivalmanual.model

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors
import java.io.ByteArrayInputStream

sealed class TransferState {
    object Idle : TransferState()
    object Busy : TransferState()
    data class Preview(val snapshot: HouseholdSnapshot) : TransferState()
    // Only process memory: never place passwords/ciphertext in saved state or prefs.
    class Unlock(val encrypted: ByteArray, val ids: Set<String>?, val failed: Boolean = false) : TransferState()
    class Protect(val uri: Uri, val snapshot: String) : TransferState()
    object Saved : TransferState()
    object Restored : TransferState()
    object ReadFailed : TransferState()
    object WriteFailed : TransferState()
    object RestoreFailed : TransferState()
}

/** Keeps pending transfers across rotation without keeping an Activity or doing
 * provider/disk I/O on its main thread. No automatic restore or upload. */
class HouseholdTransfer : ViewModel() {
    private val executor = Executors.newSingleThreadExecutor()
    private val current = MutableLiveData<TransferState>(TransferState.Idle)
    val state: LiveData<TransferState> = current
    var pendingBackup: String? = null

    fun dismiss() { current.value = TransferState.Idle }
    fun read(resolver: ContentResolver, uri: Uri, ids: Set<String>?) {
        if (current.value == TransferState.Busy) return
        current.value = TransferState.Busy
        executor.execute {
            val result = try {
                val bytes = resolver.openInputStream(uri)?.use { EncryptedHouseholdBackup.readBounded(it) }
                    ?: throw java.io.IOException("No input stream")
                if (EncryptedHouseholdBackup.isEncrypted(bytes)) {
                    EncryptedHouseholdBackup.validateEnvelope(bytes)
                    TransferState.Unlock(bytes, ids)
                } else TransferState.Preview(HouseholdBackup.read(ByteArrayInputStream(bytes), ids))
            } catch (_: Exception) { TransferState.ReadFailed }
            current.postValue(result)
        }
    }
    fun protect(uri: Uri, snapshot: String) { current.value = TransferState.Protect(uri, snapshot) }

    fun encryptAndWrite(resolver: ContentResolver, password: CharArray) {
        val request = current.value as? TransferState.Protect ?: run { password.fill('\u0000'); return }
        current.value = TransferState.Busy
        executor.execute {
            val plain = request.snapshot.toByteArray(Charsets.UTF_8)
            val result = try {
                val encrypted = EncryptedHouseholdBackup.encrypt(plain, password)
                val output = resolver.openOutputStream(request.uri, "wt") ?: throw java.io.IOException("No output stream")
                output.use { it.write(encrypted) }
                TransferState.Saved
            } catch (_: Exception) { TransferState.WriteFailed }
            finally { plain.fill(0); password.fill('\u0000') }
            current.postValue(result)
        }
    }

    fun unlock(password: CharArray) {
        val request = current.value as? TransferState.Unlock ?: run { password.fill('\u0000'); return }
        current.value = TransferState.Busy
        executor.execute {
            val result = try {
                val plaintext = EncryptedHouseholdBackup.decrypt(request.encrypted, password)
                try { TransferState.Preview(HouseholdBackup.read(ByteArrayInputStream(plaintext), request.ids)) }
                finally { plaintext.fill(0) }
            } catch (_: javax.crypto.BadPaddingException) { TransferState.Unlock(request.encrypted, request.ids, true) }
            catch (_: Exception) { TransferState.ReadFailed }
            finally { password.fill('\u0000') }
            current.postValue(result)
        }
    }
    fun write(resolver: ContentResolver, uri: Uri, snapshot: String) {
        if (current.value == TransferState.Busy) return
        current.value = TransferState.Busy
        executor.execute {
            val result = try {
                val output = resolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No output stream")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                TransferState.Saved
            } catch (_: Exception) { TransferState.WriteFailed }
            current.postValue(result)
        }
    }
    fun restore(prefs: SharedPreferences, snapshot: HouseholdSnapshot) {
        current.value = TransferState.Busy
        executor.execute {
            val restored = try { HouseholdBackup.restore(prefs, snapshot) } catch (_: Exception) { false }
            current.postValue(if (restored) TransferState.Restored else TransferState.RestoreFailed)
        }
    }
    override fun onCleared() { executor.shutdown(); super.onCleared() }
}
