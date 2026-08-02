package es.alejandro.grandarbiter.data.remote.model

data class CRRuleModel(
    val ruleNumber: String,
    val ruleText: String,
    val fragment: String,
    val examples: List<String>? = null,
    val navigation: NavigationModel?
)

data class NavigationModel(
    val previousRule: String? = null,
    val nextRule: String? = null
)
