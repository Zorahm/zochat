package zorahm.zochat.chat

import zorahm.zochat.config.ChatConfig
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Banned-words filter v2.
 *
 * Key idea: normalize the message (lowercase, strip separators, collapse repeats,
 * leet/Cyrillic look-alikes) while keeping a position map from each normalized
 * character back to the original. That lets detection and censoring share ONE set
 * of matches — fixing the old version where detection ran on the normalized text
 * but censoring ran literally on the original (so bypassed words were detected yet
 * never censored).
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

        // Normalized text plus a map of each normalized char back to its original index range.
        private class Norm(val text: String, val starts: IntArray, val ends: IntArray)

        @JvmStatic
        @JvmOverloads
        fun normalize(text: String, leet: Boolean = true): String = buildNormalized(text, leet).text

        private fun buildNormalized(original: String, leet: Boolean): Norm {
            val sb = StringBuilder()
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            var i = 0
            val n = original.length
            while (i < n) {
                val c = original[i].lowercaseChar()
                if (isSeparator(c)) {
                    i++
                    continue
                }
                // Take the maximal run of the same (case-insensitive) character.
                var j = i
                while (j < n && original[j].lowercaseChar() == c) j++
                val runLen = j - i
                val sub = if (leet) REPLACEMENTS[c] ?: c else c
                if (runLen >= 3) {
                    // Collapse a run of 3+ into one char that still spans the whole original run.
                    sb.append(sub); starts.add(i); ends.add(j)
                } else {
                    for (k in 0 until runLen) {
                        sb.append(sub); starts.add(i + k); ends.add(i + k + 1)
                    }
                }
                i = j
            }
            return Norm(sb.toString(), starts.toIntArray(), ends.toIntArray())
        }

        @JvmStatic
        fun apply(message: String, bannedWords: List<String>, mode: Mode, action: Action, leet: Boolean): FilterResult {
            val norm = buildNormalized(message, leet)
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
                val nw = normalize(raw, leet)
                if (nw.isEmpty()) continue
                val before = spans.size
                collectWord(message, norm, nw, mode, spans)
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

        private fun collectWord(message: String, norm: Norm, nw: String, mode: Mode, spans: MutableList<IntArray>) {
            when (mode) {
                Mode.EXACT -> if (norm.text == nw && norm.starts.isNotEmpty()) {
                    spans.add(intArrayOf(norm.starts.first(), norm.ends.last()))
                }
                Mode.CONTAINS, Mode.SMART -> {
                    var from = 0
                    while (true) {
                        val idx = norm.text.indexOf(nw, from)
                        if (idx < 0) break
                        val origStart = norm.starts[idx]
                        val origEnd = norm.ends[idx + nw.length - 1]
                        // SMART adds a word-boundary check on the ORIGINAL text so that
                        // "ass" does not match inside "class".
                        if (mode == Mode.CONTAINS || isWordBoundary(message, origStart, origEnd)) {
                            spans.add(intArrayOf(origStart, origEnd))
                        }
                        from = idx + 1
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
