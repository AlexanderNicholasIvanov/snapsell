package com.alexivanov.snapsell.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Item condition. Wire names match contracts/item.schema.json exactly. */
@Serializable
enum class Condition(val wire: String, val label: String) {
    @SerialName("new") NEW("new", "New"),
    @SerialName("like_new") LIKE_NEW("like_new", "Like new"),
    @SerialName("good") GOOD("good", "Good"),
    @SerialName("fair") FAIR("fair", "Fair"),
    @SerialName("for_parts") FOR_PARTS("for_parts", "For parts");

    companion object {
        fun fromWire(value: String): Condition =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown condition: $value")
    }
}
