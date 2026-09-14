package com.alexivanov.snapsell.data.repository

import android.util.Base64
import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.core.DispatcherProvider
import com.alexivanov.snapsell.core.appResult
import com.alexivanov.snapsell.data.remote.SnapsellApi
import com.alexivanov.snapsell.data.remote.dto.IdentifyRequest
import com.alexivanov.snapsell.data.remote.dto.IdentifyResponse
import kotlinx.coroutines.withContext
import java.io.File

interface IdentifyRepository {
    /** Identify the item shown in [imageFile] (a JPEG cutout or photo). */
    suspend fun identify(imageFile: File, hint: String? = null): AppResult<IdentifyResponse>
}

class RemoteIdentifyRepository(
    private val api: SnapsellApi,
    private val dispatchers: DispatcherProvider,
) : IdentifyRepository {
    override suspend fun identify(imageFile: File, hint: String?): AppResult<IdentifyResponse> = appResult {
        val encoded = withContext(dispatchers.io) {
            // NO_WRAP: the contract wants the standard alphabet with no newlines.
            Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        }
        api.identify(IdentifyRequest(imageBase64 = encoded, mediaType = IdentifyRequest.MEDIA_TYPE_JPEG, hint = hint))
    }
}
