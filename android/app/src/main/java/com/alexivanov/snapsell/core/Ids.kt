package com.alexivanov.snapsell.core

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
