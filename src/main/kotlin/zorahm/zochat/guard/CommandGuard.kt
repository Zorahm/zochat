package zorahm.zochat.guard

/**
 * Pure command-name matching for the blocked-command guard, extracted from the Bukkit
 * listener so it can be unit-tested without a running server.
 */
class CommandGuard(blocked: Collection<String>, private val blockNamespaced: Boolean) {

    // Pre-normalized once so the hot path (every command a player types) is an O(1) set lookup.
    private val blockedNames: Set<String> = blocked.mapNotNull { normalize(it, blockNamespaced) }.toSet()

    val isEmpty: Boolean get() = blockedNames.isEmpty()

    /**
     * @param commandLine the raw line as typed, with or without the leading '/'
     *                    (e.g. "/minecraft:op Zorahm", or a bare "op" from a tab-complete list)
     * @param namesOf     every name of the command a label actually runs (its name plus aliases). Without
     *                    it an alias sidesteps the list: "reload" is blocked, yet "/rl" runs the same command.
     */
    fun isBlocked(commandLine: String, namesOf: (String) -> Collection<String> = { emptyList() }): Boolean {
        val name = normalize(commandLine, blockNamespaced) ?: return false
        if (name in blockedNames) return true
        // The label keeps its namespace here: "bukkit:rl" is how the command map knows that form.
        val label = normalize(commandLine, stripNamespace = false) ?: return false
        return namesOf(label).any { normalize(it, blockNamespaced) in blockedNames }
    }

    companion object {
        /**
         * Reduce a raw command line to the bare command name used for matching: drop a leading
         * '/', take the first whitespace-delimited token, lowercase it, and — when [stripNamespace]
         * — drop a namespace prefix so `/minecraft:op` still matches `op` (the trivial way to
         * bypass a name-only filter). Returns null for a blank line.
         */
        @JvmStatic
        fun normalize(input: String, stripNamespace: Boolean): String? {
            val token = input.trim()
                .removePrefix("/")
                .takeWhile { !it.isWhitespace() }
                .lowercase()
            if (token.isEmpty()) return null
            if (!stripNamespace) return token
            val colon = token.indexOf(':')
            return if (colon >= 0) token.substring(colon + 1) else token
        }
    }
}
