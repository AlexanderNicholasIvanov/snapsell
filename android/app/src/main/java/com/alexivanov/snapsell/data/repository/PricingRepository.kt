package com.alexivanov.snapsell.data.repository

import com.alexivanov.snapsell.core.AppResult
import com.alexivanov.snapsell.core.appResult
import com.alexivanov.snapsell.data.remote.SnapsellApi
import com.alexivanov.snapsell.data.remote.dto.ItemDto
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.data.remote.dto.PriceRequest

interface PricingRepository {
    suspend fun price(item: ItemDto, localSaleFactor: Double): AppResult<PriceQuote>
}

class RemotePricingRepository(private val api: SnapsellApi) : PricingRepository {
    override suspend fun price(item: ItemDto, localSaleFactor: Double): AppResult<PriceQuote> = appResult {
        api.price(PriceRequest(item = item, localSaleFactor = localSaleFactor))
    }
}
