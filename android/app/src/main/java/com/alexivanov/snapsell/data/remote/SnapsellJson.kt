package com.alexivanov.snapsell.data.remote

import kotlinx.serialization.json.Json

/** The one Json instance used for the network, Room converters and tests. */
val SnapsellJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
