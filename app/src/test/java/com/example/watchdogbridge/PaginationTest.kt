package com.example.watchdogbridge

import com.example.watchdogbridge.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PaginationTest {
    @Test fun readsEveryPageAndHandlesEmptyFinalToken() = runBlocking {
        val tokens = mutableListOf<String?>()
        val read = readAllPages { token ->
            tokens.add(token)
            when (token) {
                null -> RecordPage(listOf(1, 2), "second")
                else -> RecordPage(listOf(3), "")
            }
        }
        assertEquals(listOf(null, "second"), tokens)
        assertEquals(listOf(1, 2, 3), read.records)
        assertEquals(2, read.pages)
    }
    @Test fun emptyTypeIsACompleteRead() = runBlocking {
        val read = readAllPages<Int> { RecordPage(emptyList(), null) }
        assertTrue(read.records.isEmpty())
        assertEquals(1, read.pages)
    }
    @Test fun pageFailureIsNotReportedAsComplete() = runBlocking {
        var failed = false
        try {
            readAllPages<Int> { token -> if (token == null) RecordPage(listOf(1), "next") else error("failed page") }
        } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
    }
    @Test fun repeatedTokenFailsRatherThanLooping() = runBlocking {
        var failed = false
        try { readAllPages<Int> { RecordPage(listOf(1), "same") } } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
    }
}
