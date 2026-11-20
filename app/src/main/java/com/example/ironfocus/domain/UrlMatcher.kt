package com.example.ironfocus.domain

import java.util.Locale

object UrlMatcher {

    val DEFAULT_QUICK_DOMAINS: List<String> = listOf(
        "instagram.com",
        "x.com",
        "twitter.com"
    )

    private val DOMAIN_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")

    /**
     * Normalizes user-entered URL or domain text by stripping http://, https://, www.,
     * paths/queries/fragments, ports, and trailing slashes.
     * Returns null if the resulting domain is invalid.
     */
    fun normalizeDomain(rawInput: String): String? {
        var cleaned = rawInput.trim().lowercase(Locale.ROOT)
        if (cleaned.isEmpty()) return null

        // Strip protocol prefixes
        if (cleaned.startsWith("https://")) {
            cleaned = cleaned.removePrefix("https://")
        } else if (cleaned.startsWith("http://")) {
            cleaned = cleaned.removePrefix("http://")
        }

        // Strip leading slashes if any
        cleaned = cleaned.trimStart('/')

        // Strip path, query, or fragment
        val delimiterIndex = cleaned.indexOfAny(charArrayOf('/', '?', '#'))
        if (delimiterIndex != -1) {
            cleaned = cleaned.substring(0, delimiterIndex)
        }

        // Strip port if specified
        val portIndex = cleaned.indexOf(':')
        if (portIndex != -1) {
            cleaned = cleaned.substring(0, portIndex)
        }

        // Strip www. prefix repeatedly in case of malformed input
        while (cleaned.startsWith("www.")) {
            cleaned = cleaned.removePrefix("www.")
        }

        // Strip trailing slashes and dots
        cleaned = cleaned.trimEnd('/', '.')

        if (cleaned.isEmpty() || !DOMAIN_REGEX.matches(cleaned)) {
            return null
        }
        return cleaned
    }

    /**
     * Checks whether the extracted browser address/URL text references any blocked domain.
     * Uses lowercase matching with domain-boundary verification so short domains like "x.com"
     * match "x.com", "https://x.com/home", "m.x.com", "www.x.com" without false-positiving "box.com".
     */
    fun findMatchingBlockedDomain(
        rawUrlText: CharSequence?,
        blockedDomains: Set<String>
    ): String? {
        if (rawUrlText.isNullOrBlank() || blockedDomains.isEmpty()) return null
        val text = rawUrlText.toString().trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return null

        for (rawDomain in blockedDomains) {
            val domain = rawDomain.lowercase(Locale.ROOT).trim()
            if (domain.isEmpty()) continue

            var searchIndex = text.indexOf(domain)
            while (searchIndex != -1) {
                val validPrefix = if (searchIndex == 0) {
                    true
                } else {
                    val prev = text[searchIndex - 1]
                    prev == '/' || prev == '.' || prev == '@' || prev == ':' || prev.isWhitespace()
                }

                val endIndex = searchIndex + domain.length
                val validSuffix = if (endIndex == text.length) {
                    true
                } else {
                    val next = text[endIndex]
                    next == '/' || next == '?' || next == '#' || next == ':' || next == '&' || next.isWhitespace()
                }

                if (validPrefix && validSuffix) {
                    return domain
                }
                searchIndex = text.indexOf(domain, startIndex = searchIndex + 1)
            }
        }
        return null
    }
}

object TimeFormatter {

    fun formatCountdown(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        return if (days > 0) {
            String.format(Locale.US, "%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    fun formatDurationHuman(millis: Long): String {
        val totalMinutes = (millis.coerceAtLeast(0L) / 60_000L)
        val days = totalMinutes / 1_440L
        val hours = (totalMinutes % 1_440L) / 60L
        val minutes = totalMinutes % 60L

        val parts = mutableListOf<String>()
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0 || parts.isEmpty()) parts += "${minutes}m"
        return parts.joinToString(" ")
    }

    fun formatNotificationRemaining(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            days > 0 -> String.format(Locale.US, "%dd %02dh %02dm remaining", days, hours, minutes)
            hours > 0 -> String.format(Locale.US, "%dh %02dm %02ds remaining", hours, minutes, seconds)
            else -> String.format(Locale.US, "%02dm %02ds remaining", minutes, seconds)
        }
    }
}
