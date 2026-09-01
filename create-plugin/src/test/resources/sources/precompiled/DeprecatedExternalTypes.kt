package sources.precompiled

@Deprecated("Use ReplacementExternalType instead")
data class DeprecatedExternalType(
    val id: String,
    @Deprecated("Use id instead")
    val legacyId: String?
)
