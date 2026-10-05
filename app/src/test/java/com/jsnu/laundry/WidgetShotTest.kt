package com.jsnu.laundry

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** 把桌面卡片 RemoteViews 布局按示例数据渲染成 PNG，用于人工核对视觉效果 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h180dp-420dpi", application = Application::class)
class WidgetShotTest {

    private fun dp(v: Int): Int {
        val d = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        return (v * d).toInt()
    }

    @Test
    fun renderWidgetPreview() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val v = LayoutInflater.from(ctx).inflate(com.jsnu.laundry.R.layout.widget_laundry, null)
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_point).text = "二组团17-19号楼·4楼"
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_idle_num).text = "2"
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_res_num).text = "1"
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_fault_num).text = "0"
        val w = dp(250)
        val h = dp(110)
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val out = File("app/build/outputs/roborazzi/widget_preview.png")
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderTrackingWidgetPreview() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val v = LayoutInflater.from(ctx).inflate(com.jsnu.laundry.R.layout.widget_tracking, null)
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_tracking_point).text = "二组团17-19号楼·4楼"
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_tracking_machine).text = "18#4楼1号机"
        val chrono = v.findViewById<android.widget.Chronometer>(com.jsnu.laundry.R.id.widget_tracking_countdown)
        chrono.text = "还剩 28:35"
        v.findViewById<TextView>(com.jsnu.laundry.R.id.widget_tracking_finish).text = "预计 14:30 洗完"
        val w = dp(250)
        val h = dp(170)
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val out = File("app/build/outputs/roborazzi/widget_tracking_preview.png")
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
