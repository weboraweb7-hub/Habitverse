package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.widget.HabitWorksWidgetReceiver
import com.example.work.NotificationActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and manifest receivers are configured for HabitWorks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HabitWorks", appName)

        val pm = context.packageManager
        val widgetInfo = pm.getReceiverInfo(
            ComponentName(context, HabitWorksWidgetReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(widgetInfo)

        val actionReceiverInfo = pm.getReceiverInfo(
            ComponentName(context, NotificationActionReceiver::class.java),
            0
        )
        assertNotNull(actionReceiverInfo)
    }
}
