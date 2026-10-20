package org.ligi.survivalmanual

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** App-private screenshots, collected by the host through run-as before Gradle
 * uninstalls the fixture. No storage permission or API-specific shell UID needed. */
object ScreenshotEvidence {
    fun capture(name: String) {
        require(name.matches(Regex("[a-z-]+")))
        Thread.sleep(3500) // Let previous system toasts disappear.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.cacheDir, "usability").apply { mkdirs() }
        val temp = File(directory, "$name.tmp")
        temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        check(temp.renameTo(File(directory, "$name.png")))
        // The host collector polls missing files during the instrumentation run.
        Thread.sleep(1000)
    }
}
