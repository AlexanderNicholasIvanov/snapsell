package com.alexivanov.snapsell.handoff

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardStagerTest {
    @Test
    fun `block is title, blank, dollar price, blank, description`() {
        val expected = "Apple iPad Air 2 64GB Wi-Fi, Space Gray\n\n\$75\n\nWorks well, light scuffs on the back. Local pickup."
        assertEquals(
            expected,
            ClipboardStager.format(
                title = "Apple iPad Air 2 64GB Wi-Fi, Space Gray",
                price = 75.0,
                description = "Works well, light scuffs on the back. Local pickup.",
            ),
        )
    }

    @Test
    fun `non-whole prices keep two decimals`() {
        assertEquals("T\n\n\$79.99\n\nD", ClipboardStager.format("T", 79.99, "D"))
    }

    @Test
    fun `surrounding whitespace is trimmed but inner newlines kept`() {
        assertEquals("T\n\n\$5\n\nline one\nline two", ClipboardStager.format("  T \n", 5.0, "\nline one\nline two\n"))
    }
}
