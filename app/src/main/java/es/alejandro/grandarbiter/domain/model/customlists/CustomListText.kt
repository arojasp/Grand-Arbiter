package es.alejandro.grandarbiter.domain.model.customlists

/** A pasted line read as a card: [name] still has to be found among the cards. */
data class PastedLine(val line: String, val name: String, val status: ListStatus)

/**
 * The plain-text form of a custom list, used both to paste and to share. Pure, unit-tested.
 *
 * One card per line:
 *  - `3 Card name`, `Card name 3`, `Card name: 3`, `Card name (3 points)` → 3 points
 *  - `Card name banned`, `Card name - restricted` → that status
 *  - `Card name` alone → Banned
 * Blank lines and lines starting with `#` are skipped: the shared text puts the list's name and
 * note there, so pasting it back adds only the cards. Bullets (`-`, `*`, `•`) are ignored.
 */
object CustomListText {

    private val bullet = Regex("""^[-*•]\s+""")
    private val leadingPoints = Regex("""^(\d{1,2})\s*(?:points?|pts?\.?)?\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val trailingPoints = Regex("""^(.+?)\s*[-–:(]?\s*(?<![\d,.])(\d{1,2})\s*(?:points?|pts?\.?)?\)?$""", RegexOption.IGNORE_CASE)
    private val trailingStatus = Regex("""^(.+?)\s*[-–:(]?\s*(banned|restricted)\)?$""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<PastedLine> = text.lines().mapNotNull(::parseLine)

    fun parseLine(raw: String): PastedLine? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val body = line.replace(bullet, "").trim()
        trailingStatus.matchEntire(body)?.let { m ->
            val status = if (m.groupValues[2].equals("restricted", ignoreCase = true)) ListStatus.Restricted else ListStatus.Banned
            return PastedLine(line, m.groupValues[1].trim(), status)
        }
        val points = leadingPoints.matchEntire(body)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: trailingPoints.matchEntire(body)?.let { it.groupValues[2] to it.groupValues[1] }
        val value = points?.first?.toInt()
        if (points != null && value != null && value in ListStatus.POINTS_RANGE) {
            return PastedLine(line, points.second.trim(), ListStatus.Points(value))
        }
        return PastedLine(line, body, ListStatus.Banned)
    }

    /** The shared text: name and note as `#` lines, then the cards in display order. */
    fun format(list: CustomList): String = buildString {
        appendLine("# ${list.name.trim()}")
        list.note.trim().lines().filter { it.isNotBlank() }.forEach { appendLine("# ${it.trim()}") }
        appendLine()
        list.sorted.forEach { entry ->
            appendLine(
                when (val s = entry.status) {
                    is ListStatus.Points -> "${s.value} ${entry.name}"
                    ListStatus.Banned -> "${entry.name} banned"
                    ListStatus.Restricted -> "${entry.name} restricted"
                }
            )
        }
    }.trimEnd() + "\n"
}
