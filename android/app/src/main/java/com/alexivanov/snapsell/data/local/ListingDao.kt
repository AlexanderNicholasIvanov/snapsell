package com.alexivanov.snapsell.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.alexivanov.snapsell.domain.ListingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ListingDao {
    @Transaction
    @Query("SELECT * FROM listings ORDER BY createdAt DESC")
    fun observeAllWithItems(): Flow<List<ListingWithItems>>

    @Transaction
    @Query("SELECT * FROM listings WHERE id = :id")
    fun observeWithItems(id: String): Flow<ListingWithItems?>

    @Transaction
    @Query("SELECT * FROM listings WHERE id = :id")
    suspend fun getWithItems(id: String): ListingWithItems?

    @Upsert
    suspend fun upsert(listing: ListingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<ListingItemCrossRef>)

    @Query("DELETE FROM listing_items WHERE listingId = :listingId")
    suspend fun deleteCrossRefs(listingId: String)

    @Query(
        "UPDATE listings SET status = :status, listedAt = :listedAt, soldAt = :soldAt, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(id: String, status: ListingStatus, listedAt: Long?, soldAt: Long?, updatedAt: Long)

    /** Create a listing and its item links atomically. */
    @Transaction
    suspend fun insertWithItems(listing: ListingEntity, itemIds: List<String>) {
        upsert(listing)
        deleteCrossRefs(listing.id)
        insertCrossRefs(itemIds.map { ListingItemCrossRef(listing.id, it) })
    }
}
