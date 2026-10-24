package zorahm.zochat.chat

import zorahm.zochat.config.ChatConfig
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Banned-words filter v2.
 *
 * Key idea: normalize the message (lowercase, strip separators, leet/Cyrillic look-alikes) into
 * runs of one repeated character, each mapped back to the original text it covers. That lets
 * detection and censoring share ONE set of matches — fixing the old version where detection ran
 * on the normalized text but censoring ran literally on the original (so bypassed words were
 * detected yet never censored).
 */
class BannedWordsFilter(private val config: ChatConfig) {

    enum class Mode { EXACT, CONTAINS, SMART }
    enum class Action { BLOCK, REPLACE }

    class FilterResult(
        val isBlocked: Boolean,
        val processedMessage: String,
        val matchedWord: String?
    )

    fun checkMessage(message: String): FilterResult {
        if (!config.isBannedWordsEnabled) return FilterResult(false, message, null)
        val words = config.bannedWords
        if (words.isEmpty()) return FilterResult(false, message, null)
        return apply(
            message,
            words,
            parseMode(config.bannedWordsMode),
            parseAction(config.bannedWordsAction),
            config.isBannedWordsNormalizeEnabled
        )
    }

    companion object {
        // leet/Cyrillic look-alikes -> latin
        private val REPLACEMENTS: Map<Char, Char> = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x',
            '4' to 'a', '3' to 'e', '1' to 'i', '0' to 'o', '5' to 's', '7' to 't', '@' to 'a', '$' to 's'
        )

        private fun isSeparator(c: Char): Boolean = c.isWhitespace() || c in "_-.*+"

        // Runs of one repeated (normalized) character with the [start, end) range each covers in
        // the original text. Repeat counts are kept rather than collapsed: collapsing 3+ to one char
        // while a double stayed double turned "asss" into "as", which slipped past "ass".
        private class Runs(val chars: CharArray, val counts: IntArray, val starts: IntArray, val ends: IntArray) {
            val size: Int get() = chars.size
        }

        /** Canonical text form: separators gone, look-alikes mapped, runs of 3+ shown as one char. */
        @JvmStatic
        @JvmOverloads
        fun normalize(text: String, leet: Boolean = true): String {
            val runs = buildRuns(text, leet)
            val sb = StringBuilder()
            for (k in 0 until runs.size) {
                repeat(if (runs.counts[k] >= 3) 1 else runs.counts[k]) { sb.append(runs.chars[k]) }
            }
            return sb.toString()
        }

        private fun buildRuns(original: String, leet: Boolean): Runs {
            val chars = StringBuilder()
            val counts = ArrayList<Int>()
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            for ((i, ch) in original.withIndex()) {
                val lower = ch.lowercaseChar()
                if (isSeparator(lower)) continue
                val c = if (leet) REPLACEMENTS[lower] ?: lower else lower
                val last = chars.length - 1
                // A run continues across separators, so "s s" is one run spanning the space too.
                if (last >= 0 && chars[last] == c) {
                    counts[last] = counts[last] + 1
                    ends[last] = i + 1
                } else {
                    chars.append(c)
                    counts.add(1)
                    starts.add(i)
                    ends.add(i + 1)
                }
            }
            return Runs(chars.toString().toCharArray(), counts.toIntArray(), starts.toIntArray(), ends.toIntArray())
        }

        // 3+ repeats are emphasis and stand for any count; 1 vs 2 is a real spelling difference
        // ("god" vs "good"), so those must match exactly.
        private fun runMatches(messageCount: Int, wordCount: Int): Boolean =
            messageCount == wordCount || messageCount >= 3

        private fun matchesAt(message: Runs, at: Int, word: Runs): Boolean {
            if (at + word.size > message.size) return false
            for (k in 0 until word.size) {
                if (message.chars[at + k] != word.chars[k] || !runMatches(message.counts[at + k], word.counts[k])) {
                    return false
                }
            }
            return true
        }

        @JvmStatic
        fun apply(message: String, bannedWords: List<String>, mode: Mode, action: Action, leet: Boolean): FilterResult {
            val runs = buildRuns(message, leet)
            val spans = ArrayList<IntArray>() // [start, end) ranges in the ORIGINAL text
            var matched: String? = null

            for (raw in bannedWords) {
                if (raw.startsWith("regex:")) {
                    if (collectRegex(message, raw.substring(6), spans)) {
                        matched = raw
                        if (action == Action.BLOCK) return FilterResult(true, message, matched)
                    }
                    continue
                }
                val word = buildRuns(raw, leet)
                if (word.size == 0) continue
                val before = spans.size
                collectWord(message, runs, word, mode, spans)
                if (spans.size > before) {
                    matched = raw
                    if (action == Action.BLOCK) return FilterResult(true, message, matched)
                }
            }

            if (spans.isEmpty()) return FilterResult(false, message, null)
            // Only reached in REPLACE mode (a BLOCK match would have returned earlier).
            return FilterResult(false, censor(message, spans), matched)
        }

        private fun collectRegex(message: String, regex: String, spans: MutableList<IntArray>): Boolean {
            return try {
                val m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(message)
                var any = false
                while (m.find()) {
                    spans.add(intArrayOf(m.start(), m.end()))
                    any = true
                }
                any
            } catch (e: PatternSyntaxException) {
                false
            }
        }

        private fun collectWord(message: String, runs: Runs, word: Runs, mode: Mode, spans: MutableList<IntArray>) {
            when (mode) {
                Mode.EXACT -> if (runs.size == word.size && matchesAt(runs, 0, word)) {
                    spans.add(intArrayOf(runs.starts.first(), runs.ends.last()))
                }
                Mode.CONTAINS, Mode.SMART -> {
                    for (at in 0..runs.size - word.size) {
                        if (!matchesAt(runs, at, word)) continue
                        val origStart = runs.starts[at]
                        val origEnd = runs.ends[at + word.size - 1]
                        // SMART adds a word-boundary check on the ORIGINAL text so that
                        // "ass" does not match inside "class".
                        if (mode == Mode.CONTAINS || isWordBoundary(message, origStart, origEnd)) {
                            spans.add(intArrayOf(origStart, origEnd))
                        }
                    }
                }
            }
        }

        private fun isWordBoundary(message: String, start: Int, end: Int): Boolean {
            val leftOk = start == 0 || !message[start - 1].isLetter()
            val rightOk = end >= message.length || !message[end].isLetter()
            return leftOk && rightOk
        }

        private fun censor(message: String, spans: List<IntArray>): String {
            val sorted = spans.sortedBy { it[0] }
            val merged = ArrayList<IntArray>()
            for (s in sorted) {
                val lastSpan = merged.lastOrNull()
                if (lastSpan != null && s[0] <= lastSpan[1]) {
                    lastSpan[1] = maxOf(lastSpan[1], s[1])
                } else {
                    merged.add(intArrayOf(s[0], s[1]))
                }
            }
            val sb = StringBuilder()
            var last = 0
            for (m in merged) {
                sb.append(message, last, m[0])
                sb.append("*".repeat(maxOf(3, m[1] - m[0])))
                last = m[1]
            }
            sb.append(message, last, message.length)
            return sb.toString()
        }

        private fun parseMode(s: String): Mode = try {
            Mode.valueOf(s.uppercase())
        } catch (e: IllegalArgumentException) {
            Mode.SMART
        }

        private fun parseAction(s: String): Action = try {
            Action.valueOf(s.uppercase())
        } catch (e: IllegalArgumentException) {
            Action.BLOCK
        }
    }
}
