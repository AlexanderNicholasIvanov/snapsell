package com.alexivanov.snapsell.update

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The network is faked with an OkHttp interceptor that answers every call
 * from memory, so these run on the plain JVM with no server and no extra
 * dependency. The interceptor also records the request for header checks.
 */
class UpdateCheckerTest {
    private class FakeServer(private val code: Int, private val body: String) : Interceptor {
        var lastRequest: Request? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            lastRequest = chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun checker(code: Int, body: String, config: UpdateConfig = UpdateConfig(repo = "someone/snapsell")): Pair<UpdateChecker, FakeServer> {
        val server = FakeServer(code, body)
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        return UpdateChecker(client, config) to server
    }

    private fun release(tag: String, body: String?, assets: String = APK_ASSET) = """
        {"tag_name":"$tag","name":"SnapSell $tag",${if (body == null) "\"body\":null" else "\"body\":\"$body\""},
         "html_url":"https://github.com/someone/snapsell/releases/tag/$tag","published_at":"2026-09-14T00:00:00Z",
         "prerelease":false,"assets":[$assets]}
    """.trimIndent()

    @Test
    fun `version_code in body wins and apk asset is picked`() = runBlocking {
        val (c, server) = checker(200, release("v0.1.42", "Fixes.\\nversion_code: 57"))
        val info = c.check(currentVersionCode = 3)
        assertNotNull(info)
        assertEquals(57, info!!.versionCode)
        assertEquals("v0.1.42", info.versionName)
        assertEquals("https://example.com/snapsell-0.1.42.apk", info.apkUrl)
        assertEquals(21_000_000L, info.sizeBytes)
        assertTrue(info.notes.contains("Fixes."))
        assertEquals("https://api.github.com/repos/someone/snapsell/releases/latest", server.lastRequest!!.url.toString())
        assertEquals("application/vnd.github+json", server.lastRequest!!.header("Accept"))
        assertNull(server.lastRequest!!.header("Authorization"))
    }

    @Test
    fun `falls back to the last numeric segment of the tag`() = runBlocking {
        val (c, _) = checker(200, release("v0.1.42", "Release notes without a code"))
        assertEquals(42, c.check(1)!!.versionCode)
        val (c2, _) = checker(200, release("v0.1.42", null))
        assertEquals(42, c2.check(1)!!.versionCode)
    }

    @Test
    fun `no apk asset means null`() = runBlocking {
        val (c, _) = checker(200, release("v0.1.42", "version_code: 42", assets = """{"name":"mapping.txt","browser_download_url":"https://example.com/mapping.txt","size":10}"""))
        assertNull(c.check(1))
    }

    @Test
    fun `not newer means null, equal included`() = runBlocking {
        val (c, _) = checker(200, release("v0.1.42", "version_code: 42"))
        assertNull(c.check(42))
        assertNull(c.check(100))
        assertNotNull(c.check(41))
    }

    @Test
    fun `malformed JSON, HTTP errors and transport failures are quiet nulls`() = runBlocking {
        assertNull(checker(200, "{not json").first.check(1))
        assertNull(checker(200, "").first.check(1))
        assertNull(checker(404, """{"message":"Not Found"}""").first.check(1))
        assertNull(checker(500, "boom").first.check(1))
        val throwing = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("no network") }.build()
        assertNull(UpdateChecker(throwing, UpdateConfig("x/y")).check(1))
    }

    @Test
    fun `detailed outcome separates up to date from failure`() = runBlocking {
        assertEquals(UpdateChecker.Outcome.UpToDate, checker(200, release("v0.1.5", "version_code: 5")).first.checkDetailed(5))
        assertEquals(UpdateChecker.Outcome.Failed, checker(404, "{}").first.checkDetailed(5))
        assertTrue(checker(200, release("v0.1.9", "version_code: 9")).first.checkDetailed(5) is UpdateChecker.Outcome.Newer)
    }

    @Test
    fun `token and manifest url are honoured`() = runBlocking {
        val cfg = UpdateConfig(repo = "someone/snapsell", token = "ghp_abc", manifestUrl = "http://10.0.2.2:8090/latest.json")
        val (c, server) = checker(200, release("v0.1.999", "version_code: 999"), cfg)
        assertEquals(999, c.check(1)!!.versionCode)
        assertEquals("http://10.0.2.2:8090/latest.json", server.lastRequest!!.url.toString())
        assertEquals("Bearer ghp_abc", server.lastRequest!!.header("Authorization"))
    }

    @Test
    fun `unknown keys are ignored and size formats for the banner`() {
        val (c, _) = checker(200, "")
        val info = c.parse(release("v2", "version_code: 7", assets = """{"name":"a.apk","browser_download_url":"https://e/a.apk","size":21474836,"content_type":"application/vnd.android.package-archive","state":"uploaded"}"""), 1)
        assertEquals(7, info!!.versionCode)
        assertEquals("21 MB", UpdateChecker.formatSize(info.sizeBytes))
        assertEquals("512 KB", UpdateChecker.formatSize(512_000))
        assertEquals("", UpdateChecker.formatSize(0))
    }

    companion object {
        const val APK_ASSET = """{"name":"snapsell-0.1.42.apk","browser_download_url":"https://example.com/snapsell-0.1.42.apk","size":21000000}"""
    }
}
