package com.example.on_safe.messaging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LiveRequestInboxTest {
    private val now = 1_000_000L
    private val data = mapOf("room" to "live-elder", "expires_at" to "1300")

    @Before
    @After
    fun clearInbox() {
        LiveRequestInbox.clear()
        LiveRequestInbox.isPublishing = false
    }

    @Test
    fun loggedOutRequestIsRejected() {
        assertEquals(LiveRequestInbox.Result.NOT_LOGGED_IN, LiveRequestInbox.receive(data, "", 0, now))
        assertNull(LiveRequestInbox.take(now))
    }

    @Test
    fun otherAccountRequestIsRejected() {
        assertEquals(LiveRequestInbox.Result.OTHER_ACCOUNT, LiveRequestInbox.receive(data, "other", 0, now))
        assertNull(LiveRequestInbox.take(now))
    }

    @Test
    fun expiredRequestIsRejected() {
        val expired = data + ("expires_at" to "999")
        assertEquals(LiveRequestInbox.Result.EXPIRED, LiveRequestInbox.receive(expired, "elder", 0, now))
        assertNull(LiveRequestInbox.take(now))
    }

    @Test
    fun publishingRequestIsRejected() {
        LiveRequestInbox.isPublishing = true
        assertEquals(LiveRequestInbox.Result.ALREADY_PUBLISHING, LiveRequestInbox.receive(data, "elder", 0, now))
        assertNull(LiveRequestInbox.take(now))
    }

    @Test
    fun requestUsesLocalClockAndCanOnlyBeTakenOnce() {
        assertEquals(LiveRequestInbox.Result.ACCEPTED, LiveRequestInbox.receive(data, "elder", 1_200_000L, now))
        assertEquals(LiveRequest("live-elder", now + 100_000L), LiveRequestInbox.take(now))
        assertNull(LiveRequestInbox.take(now))
    }
}
