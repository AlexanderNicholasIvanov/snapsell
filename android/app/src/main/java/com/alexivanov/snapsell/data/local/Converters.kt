package com.alexivanov.snapsell.data.local

import androidx.room.TypeConverter
import com.alexivanov.snapsell.data.remote.SnapsellJson
import com.alexivanov.snapsell.data.remote.dto.PriceQuote
import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.domain.ListingKind
import com.alexivanov.snapsell.domain.ListingStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Room converters. Enums are stored by their wire name so the DB reads like the contracts. */
class Converters {
    @TypeConverter fun conditionToString(value: Condition): String = value.wire
    @TypeConverter fun stringToCondition(value: String): Condition = Condition.fromWire(value)

    @TypeConverter fun statusToString(value: ListingStatus): String = value.wire
    @TypeConverter fun stringToStatus(value: String): ListingStatus = ListingStatus.fromWire(value)

    @TypeConverter fun kindToString(value: ListingKind): String = value.wire
    @TypeConverter fun stringToKind(value: String): ListingKind = ListingKind.fromWire(value)

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        SnapsellJson.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        SnapsellJson.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun quoteToJson(value: PriceQuote?): String? =
        value?.let { SnapsellJson.encodeToString(PriceQuote.serializer(), it) }

    @TypeConverter
    fun jsonToQuote(value: String?): PriceQuote? =
        value?.let { SnapsellJson.decodeFromString(PriceQuote.serializer(), it) }
}
