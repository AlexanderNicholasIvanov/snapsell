package com.alexivanov.snapsell.data.repository

import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.core.appResult
import com.alexivanov.snapsell.data.remote.SnapsellApi
import com.alexivanov.snapsell.data.remote.dto.BundleRequest
import com.alexivanov.snapsell.data.remote.dto.BundleRequestItem
import com.alexivanov.snapsell.data.remote.dto.BundleResponse
import com.alexivanov.snapsell.data.remote.dto.ItemDto

interface BundleRepository {
    /** Ask the backend to write title + description for a bundle priced on-device. */
    suspend fun writeListing(items: List<Pair<ItemDto, Double>>, bundlePrice: Double): AppResult<BundleResponse>
}

class RemoteBundleRepository(private val api: SnapsellApi) : BundleRepository {
    override suspend fun writeListing(items: List<Pair<ItemDto, Double>>, bundlePrice: Double): AppResult<BundleResponse> =
        appResult {
            api.bundle(
                BundleRequest(
                    items = items.map { (item, price) -> BundleRequestItem(item = item, price = price) },
                    bundlePrice = bundlePrice,
                ),
            )
        }
}
