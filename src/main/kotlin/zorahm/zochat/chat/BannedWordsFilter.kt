package zorahm.zochat.chat

import zorahm.zochat.config.ChatConfig
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Banned-words filter v2.
 *
 * Key idea: normalize the message (lowercase, strip separators, leet/Cyrillic
 * look-alikes) into runs of identical characters, each keeping the original index
 * range it came from. That lets detection and censoring share ONE set of matches —
 * fixing the old version where detection ran on the normalized text but censoring ran
 * literally on the original (so bypassed words were detected yet never censored).
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
        if (!config.isBannedWordsEnabled()) return FilterResult(false, message, null)
        val words = config.getBannedWords()
        if (words.isEmpty()) return FilterResult(false, message, null)
        return apply(
            message,
            words,
            parseMode(config.getBannedWordsMode()),
            parseAction(config.getBannedWordsAction()),
            config.isBannedWordsNormalizeEnabled()
        )
    }

    companion object {
        // leet/Cyrillic look-alikes -> latin
        private val REPLACEMENTS: Map<Char, Char> = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x',
            '4' to 'a', '3' to 'e', '1' to 'i', '0' to 'o', '5' to 's', '7' to 't', '@' to 'a', '$' to 's'
        )

        private fun isSeparator(c: Char): Boolean = c.isWhitespace() || c in "_-.*+"

        /**
         * A maximal run of one normalized character, with the ORIGINAL index range it covers — so
         * detection and censoring share one match set (bypassed words are censored in place).
         */
        private class Run(val ch: Char, val count: Int, val start: Int, val end: Int)

        // Separators are dropped before runs are formed, so "a a a" is one run of three.
        private fun runs(original: String, leet: Boolean): List<Run> {
            val out = ArrayList<Run>()
            for ((i, raw) in original.withIndex()) {
                val c = raw.lowercaseChar()
                if (isSeparator(c)) continue
                val sub = if (leet) REPLACEMENTS[c] ?: c else c
                val last = out.lastOrNull()
                if (last != null && last.ch == sub) {
                    out[out.size - 1] = Run(sub, last.count + 1, last.start, i + 1)
                } else {
                    out += Run(sub, 1, i, i + 1)
                }
            }
            return out
        }

        /** Human-readable normalized form (runs of 3+ collapsed to one char); matching works on runs. */
        @JvmStatic
        @JvmOverloads
        fun normalize(text: String, leet: Boolean = true): String =
            runs(text, leet).joinToString("") { r -> r.ch.toString().repeat(if (r.count >= 3) 1 else r.count) }

        @JvmStatic
        fun apply(message: String, bannedWords: List<String>, mode: Mode, action: Action, leet: Boolean): FilterResult {
            val textRuns = runs(message, leet)
            val spans = ArrayList<IntArray>() // [start, end) ranges in the ORIGINAL text
            var matched: String? = null

            for (raw in bannedWords) {
                val before = spans.size
                when {
                    raw.startsWith("regex:") -> collectRegex(message, raw.substring(6), spans)
                    mode == Mode.EXACT -> collectExact(message, raw, leet, spans)
                    else -> collectRuns(message, textRuns, runs(raw, leet), mode == Mode.SMART, spans)
                }
                if (spans.size > before) {
                    matched = raw
                    if (action == Action.BLOCK) return FilterResult(true, message, matched)
                }
            }

            if (spans.isEmpty()) return FilterResult(false, message, null)
            // Only reached in REPLACE mode (a BLOCK match would have returned earlier).
            return FilterResult(false, censor(message, spans), matched)
        }

        private fun collectRegex(message: String, regex: String, spans: MutableList<IntArray>) {
            try {
                val m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(message)
                while (m.find()) spans.add(intArrayOf(m.start(), m.end()))
            } catch (e: PatternSyntaxException) {
                // An admin typo in one regex must not break the whole filter; the entry just never matches.
            }
        }

        private fun collectRuns(
            message: String, text: List<Run>, word: List<Run>, smart: Boolean, spans: MutableList<IntArray>,
        ) {
            if (word.isEmpty() || word.size > text.size) return
            for (i in 0..text.size - word.size) {
                // A text run may be LONGER than the word's ("fuuuck", "asss") but never shorter, so "as"
                // doesn't match "ass". Comparing runs instead of chars is what closes the old bypass
                // where doubling one letter ("fuuck") slipped past, since only 3+ repeats were collapsed.
                val hit = word.indices.all { k -> text[i + k].ch == word[k].ch && text[i + k].count >= word[k].count }
                if (!hit) continue
                val start = text[i].start
                val end = text[i + word.size - 1].end
                // SMART adds a word-boundary check on the ORIGINAL text so "ass" doesn't match in "class".
                if (!smart || isWordBoundary(message, start, end)) spans.add(intArrayOf(start, end))
            }
        }

        // EXACT: the word as a standalone token, letter for letter (case and leet look-alikes aside) — no
        // spacing or repeat tricks. It used to compare against the WHOLE message, so "you are bad" passed.
        private fun collectExact(message: String, word: String, leet: Boolean, spans: MutableList<IntArray>) {
            val target = canonical(word, leet)
            if (target.isEmpty()) return
            var i = 0
            while (i < message.length) {
                if (!isTokenChar(message[i])) {
                    i++
                    continue
                }
                var j = i
                while (j < message.length && isTokenChar(message[j])) j++
                if (canonical(message.substring(i, j), leet) == target) spans.add(intArrayOf(i, j))
                i = j
            }
        }

        // '@' and '$' count as word characters so "b@d" is one token, not "b" + "d".
        private fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || c == '@' || c == '$'

        private fun canonical(text: String, leet: Boolean): String = buildString {
            for (raw in text) {
                val c = raw.lowercaseChar()
                append(if (leet) REPLACEMENTS[c] ?: c else c)
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
