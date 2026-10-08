package es.alejandro.grandarbiter.ui.reader

/**
 * Parses the bundle's inline markup (see RulesBundle.kt) into styled spans:
 *   **strong**   *emphasis*   [label](ref:ipg/2.1 | https://…)   \* \[ \] \\ escapes
 * Pure Kotlin so it's unit-tested; the Compose layer turns spans into an AnnotatedString.
 */
sealed interface InlineSpan {
    data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false) : InlineSpan
    data class Link(val text: String, val target: String, val bold: Boolean = false) : InlineSpan {
        /** "ref:cr/603.1" -> "cr/603.1"; null for web links. */
        val sectionId: String? get() = target.removePrefix("ref:").takeIf { target.startsWith("ref:") }
    }
}

object InlineMarkup {
    private val ESCAPABLE = setOf('*', '[', ']', '\\')

    fun parse(markup: String): List<InlineSpan> {
        val out = mutableListOf<InlineSpan>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        var i = 0

        fun flush() {
            if (buf.isNotEmpty()) {
                out += InlineSpan.Text(buf.toString(), bold, italic)
                buf.clear()
            }
        }

        while (i < markup.length) {
            val c = markup[i]
            when {
                c == '\\' && i + 1 < markup.length && markup[i + 1] in ESCAPABLE -> {
                    buf.append(markup[i + 1]); i += 2
                }
                markup.startsWith("**", i) -> {
                    flush(); bold = !bold; i += 2
                }
                c == '*' -> {
                    flush(); italic = !italic; i += 1
                }
                c == '[' -> {
                    val link = readLink(markup, i)
                    if (link != null) {
                        flush()
                        out += InlineSpan.Link(unescape(link.label), link.target, bold)
                        i = link.end
                    } else {
                        buf.append(c); i += 1
                    }
                }
                else -> {
                    buf.append(c); i += 1
                }
            }
        }
        flush()
        return mergeAdjacent(out)
    }

    /** Text with markup removed, e.g. for accessibility or copying. */
    fun plainText(markup: String): String = parse(markup).joinToString("") {
        when (it) {
            is InlineSpan.Text -> it.text
            is InlineSpan.Link -> it.text
        }
    }

    private class RawLink(val label: String, val target: String, val end: Int)

    private fun readLink(s: String, start: Int): RawLink? {
        var j = start + 1
        while (j < s.length && s[j] != ']') {
            j += if (s[j] == '\\' && j + 1 < s.length) 2 else 1
        }
        if (j >= s.length || j + 1 >= s.length || s[j + 1] != '(') return null
        val close = s.indexOf(')', j + 2)
        if (close < 0) return null
        val target = s.substring(j + 2, close)
        if (target.isBlank() || target.any { it.isWhitespace() }) return null
        return RawLink(label = s.substring(start + 1, j), target = target, end = close + 1)
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) {
                sb.append(s[i + 1]); i += 2
            } else {
                sb.append(s[i]); i += 1
            }
        }
        return sb.toString()
    }

    private fun mergeAdjacent(spans: List<InlineSpan>): List<InlineSpan> {
        val out = mutableListOf<InlineSpan>()
        for (s in spans) {
            val last = out.lastOrNull()
            if (s is InlineSpan.Text && last is InlineSpan.Text && last.bold == s.bold && last.italic == s.italic) {
                out[out.lastIndex] = last.copy(text = last.text + s.text)
            } else {
                out += s
            }
        }
        return out
    }
}
