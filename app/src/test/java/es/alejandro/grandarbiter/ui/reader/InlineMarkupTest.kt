package es.alejandro.grandarbiter.ui.reader

import es.alejandro.grandarbiter.ui.reader.InlineSpan.Link
import es.alejandro.grandarbiter.ui.reader.InlineSpan.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkupTest {

    @Test fun plainText() =
        assertEquals(listOf(Text("A player draws.")), InlineMarkup.parse("A player draws."))

    @Test fun bold() = assertEquals(
        listOf(Text("The "), Text("required", bold = true), Text(" minimum")),
        InlineMarkup.parse("The **required** minimum"),
    )

    @Test fun italic() = assertEquals(
        listOf(Text("the "), Text("Magic", italic = true), Text(" rules")),
        InlineMarkup.parse("the *Magic* rules"),
    )

    @Test fun link() {
        val spans = InlineMarkup.parse("See rule [603.1](ref:cr/603.1).")
        assertEquals(listOf(Text("See rule "), Link("603.1", "ref:cr/603.1"), Text(".")), spans)
        assertEquals("cr/603.1", (spans[1] as Link).sectionId)
    }

    @Test fun webLinkHasNoSectionId() =
        assertEquals(null, (InlineMarkup.parse("[site](https://example.com)")[0] as Link).sectionId)

    @Test fun linkInsideBold() = assertEquals(
        listOf(Link("5.2", "ref:mtr/5.2", bold = true)),
        InlineMarkup.parse("**[5.2](ref:mtr/5.2)**"),
    )

    @Test fun escapes() {
        assertEquals(listOf(Text("a */* creature, cost [+1]")), InlineMarkup.parse("""a \*/\* creature, cost \[+1\]"""))
        assertEquals(listOf(Text("6*3 = 18")), InlineMarkup.parse("""6\*3 = 18"""))
        assertEquals(listOf(Text("""a \ b""")), InlineMarkup.parse("""a \\ b"""))
    }

    @Test fun malformedLinkStaysText() {
        assertEquals(listOf(Text("[not a link")), InlineMarkup.parse("[not a link"))
        assertEquals(listOf(Text("[x] (y)")), InlineMarkup.parse("[x] (y)"))
    }

    @Test fun upgradeLeadIn() = assertEquals(
        listOf(Text("Upgrade", bold = true), Text(": If the offense…")),
        InlineMarkup.parse("**Upgrade**: If the offense…"),
    )

    @Test fun plainTextHelper() =
        assertEquals("See 603.1 and this */*", InlineMarkup.plainText("""See [603.1](ref:cr/603.1) and **this** \*/\*"""))
}
