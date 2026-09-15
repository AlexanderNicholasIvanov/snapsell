package com.alexivanov.snapsell.data.remote

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendUrlInterceptorTest {
    /** Runs one request through the interceptor and returns the URL the "network" saw. */
    private fun urlSeen(interceptor: BackendUrlInterceptor, requestUrl: String): String {
        var seen = ""
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                seen = chain.request().url.toString()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("".toResponseBody()).build()
            }
            .build()
        client.newCall(Request.Builder().url(requestUrl).build()).execute().close()
        return seen
    }

    @Test
    fun `no override leaves the request alone`() {
        val i = BackendUrlInterceptor("http://10.0.2.2:8000/") { null }
        assertEquals("http://10.0.2.2:8000/identify", urlSeen(i, "http://10.0.2.2:8000/identify"))
    }

    @Test
    fun `override swaps host port and path prefix, keeps endpoint and query`() {
        var override: String? = "https://snapsell.example.net/api/"
        val i = BackendUrlInterceptor("http://10.0.2.2:8000/") { override }
        assertEquals("https://snapsell.example.net/api/price?x=1", urlSeen(i, "http://10.0.2.2:8000/price?x=1"))
        override = "http://192.168.1.20:9000"
        assertEquals("http://192.168.1.20:9000/health", urlSeen(i, "http://10.0.2.2:8000/health"))
    }

    @Test
    fun `override equal to the default is a no-op and invalid text clears it`() {
        var override: String? = "http://10.0.2.2:8000/"
        val i = BackendUrlInterceptor("http://10.0.2.2:8000/") { override }
        assertEquals(null, i.resolveOverride())
        override = "not a url"
        assertEquals(null, i.resolveOverride())
        override = ""
        assertEquals(null, i.resolveOverride())
    }

    @Test
    fun `validation wants absolute http or https ending in slash`() {
        assertTrue(BackendUrlInterceptor.isValidBaseUrl("http://10.0.2.2:8000/"))
        assertTrue(BackendUrlInterceptor.isValidBaseUrl("https://snapsell-api.fly.dev/"))
        assertFalse(BackendUrlInterceptor.isValidBaseUrl("https://snapsell-api.fly.dev"))
        assertFalse(BackendUrlInterceptor.isValidBaseUrl("ftp://x/"))
        assertFalse(BackendUrlInterceptor.isValidBaseUrl("10.0.2.2:8000/"))
        assertFalse(BackendUrlInterceptor.isValidBaseUrl(""))
    }
}
