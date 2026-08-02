package es.alejandro.grandarbiter.data.remote.model

data class MtrResponse(
    val effectiveDate: String,
    val content: List<MtrChunk>
)

data class MtrChunk(
    val section: Int?,
    val subsection: Int?,
    val title: String,
    val content: String?
)
