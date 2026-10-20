package org.ligi.survivalmanual.model

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

sealed class TransferState {
    object Idle : TransferState()
    object Busy : TransferState()
    data class Preview(val snapshot: HouseholdSnapshot) : TransferState()
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
                val data = resolver.openInputStream(uri)?.use { HouseholdBackup.read(it, ids) }
                    ?: throw java.io.IOException("No input stream")
                TransferState.Preview(data)
            } catch (_: Exception) { TransferState.ReadFailed }
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
