package com.example.watchdogbridge.data

/** Exhausts the provider's pages, including providers that finish with "". */
data class RecordPage<T>(val records: List<T>, val nextToken: String?)
data class PageRead<T>(val records: List<T>, val pages: Int)

suspend fun <T> readAllPages(read: suspend (String?) -> RecordPage<T>): PageRead<T> {
    val records = mutableListOf<T>()
    val seenTokens = mutableSetOf<String>()
    var token: String? = null
    var pages = 0
    do {
        val page = read(token)
        records.addAll(page.records)
        pages++
        token = page.nextToken?.takeIf { it.isNotBlank() }
        check(token == null || seenTokens.add(token)) { "Repeated Health Connect page token" }
    } while (token != null)
    return PageRead(records, pages)
}
