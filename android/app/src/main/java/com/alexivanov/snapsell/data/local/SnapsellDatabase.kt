package com.alexivanov.snapsell.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ItemEntity::class, ListingEntity::class, ListingItemCrossRef::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SnapsellDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun listingDao(): ListingDao

    companion object {
        const val NAME = "snapsell.db"

        fun build(context: Context): SnapsellDatabase =
            Room.databaseBuilder(context.applicationContext, SnapsellDatabase::class.java, NAME)
                // Pre-1.0: no migrations yet, a schema change just resets the inventory.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
