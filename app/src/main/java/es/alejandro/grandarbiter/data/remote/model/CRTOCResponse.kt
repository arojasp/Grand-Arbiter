package es.alejandro.grandarbiter.data.remote.model

data class CRTOCItem(
    val number: String? = null,
    val title: String? = null,
    val subsections: List<CRTOCSubItem>? = emptyList()
)


data class CRTOCSubItem(
    val number: String? = null,
    val title: String? = null
)