package com.meridian11.books.bentobar.compose

import android.app.Application
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.LinkWatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The Network menu's link is followed only while a Network item is live: one callback on the default network however
 * often it goes live, and none once it is idle, with what was read forgotten. The Wi-Fi values come on that callback,
 * from a Wi-Fi network alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class LinkWatchTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val callbacks get() = shadowOf(app.getSystemService(ConnectivityManager::class.java)).networkCallbacks

    @Before fun env() = Env.init(app)
    @After fun end() = LinkWatch.idle()

    @Test fun oneCallbackWhileLiveAndNoneOnceIdle() {
        assertEquals(0, callbacks.size)
        LinkWatch.live()
        assertEquals(1, callbacks.size)
        // A second Network item in the layout, or live() again: still the one callback.
        LinkWatch.live()
        assertEquals(1, callbacks.size)
        LinkWatch.idle()
        assertEquals(0, callbacks.size)
        assertNull(LinkWatch.current())
        // Idle twice is idle.
        LinkWatch.idle()
        assertEquals(0, callbacks.size)
    }

    @Test fun wifiValuesComeFromAWifiNetworkAndAreForgotten() {
        val network = app.getSystemService(ConnectivityManager::class.java).activeNetwork!!
        // The numbers are the rules' to test: a WifiInfo built here can carry neither a speed nor a frequency.
        val wifi = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).setTransportInfo(WifiInfo.Builder().build()) }
        LinkWatch.live()
        val callback = callbacks.single()

        callback.onCapabilitiesChanged(network, wifi)
        assertNotNull(LinkWatch.currentWifi())
        // Any other network, a tunnel over Wi-Fi too, reports no WifiInfo.
        callback.onCapabilitiesChanged(network, ShadowNetworkCapabilities.newInstance())
        assertNull(LinkWatch.currentWifi())

        callback.onCapabilitiesChanged(network, wifi)
        assertNotNull(LinkWatch.currentWifi())
        callback.onLost(network)
        assertNull(LinkWatch.currentWifi())

        callback.onCapabilitiesChanged(network, wifi)
        assertNotNull(LinkWatch.currentWifi())
        LinkWatch.idle()
        assertNull(LinkWatch.currentWifi())
    }
}
