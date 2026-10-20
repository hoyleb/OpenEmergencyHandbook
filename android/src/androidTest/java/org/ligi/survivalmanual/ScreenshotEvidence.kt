package org.ligi.survivalmanual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** App-private screenshots, collected by the host through run-as before Gradle
 * uninstalls the fixture. No storage permission or API-specific shell UID needed. */
object ScreenshotEvidence {
    /** Test-only rendering of an empty form. FLAG_SECURE stays enabled on the
     * real dialog; no window capture or password text is used for this evidence. */
    fun captureEmptyForm(name: String, view: View) {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); save(name,bitmap)
    }

    private fun save(name: String, bitmap: Bitmap) {
        require(name.matches(Regex("[a-z-]+")))
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"usability").apply { mkdirs() }
        val temp=File(directory,"$name.tmp")
        temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle();check(temp.renameTo(File(directory,"$name.png")))
    }
    fun capture(name: String) {
        require(name.matches(Regex("[a-z-]+")))
        Thread.sleep(3500) // Let previous system toasts disappear.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        save(name,bitmap)
        // The host collector polls missing files during the instrumentation run.
        Thread.sleep(1000)
    }
}
