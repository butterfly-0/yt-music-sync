package com.ytmusic.downloader.player

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {

    private val lrcRegex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)")

    /**
     * Parses standard LRC formatted lyrics or falls back to line-by-line text.
     */
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcContent.lines()

        for (raw in rawLines) {
            val match = lrcRegex.find(raw.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalTimeMs = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                if (text.isNotBlank()) {
                    lines.add(LyricLine(totalTimeMs, text))
                }
            } else if (raw.isNotBlank() && !raw.startsWith("[")) {
                // Plain lyrics fallback without timestamp
                lines.add(LyricLine(0L, raw.trim()))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Finds index of the active lyric line for the given playback position.
     */
    fun findActiveLyricIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (lyrics.all { it.timeMs == 0L }) return -1 // Plain text, no sync

        for (i in lyrics.indices.reversed()) {
            if (currentPositionMs >= lyrics[i].timeMs) {
                return i
            }
        }
        return 0
    }
}
