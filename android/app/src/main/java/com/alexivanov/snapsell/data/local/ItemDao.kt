package com.alexivanov.snapsell.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ItemEntity>

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)
}
