package es.alejandro.grandarbiter.domain

import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RulesDocumentInfoTest {
    private fun doc(url: String) = RulesDocumentInfo(DocumentId.CR, "t", LocalDate.of(2026, 9, 25), url)

    @Test fun crTxt_opensTheSameNamedPdf() = assertEquals(
        "https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf",
        doc("https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt").pdfUrl,
    )

    @Test fun pdfSource_isOpenedAsIs() = assertEquals(
        "https://media.wizards.com/ContentResources/WPN/MTG_IPG_2024Sep23_EN.pdf",
        doc("https://media.wizards.com/ContentResources/WPN/MTG_IPG_2024Sep23_EN.pdf").pdfUrl,
    )
}
