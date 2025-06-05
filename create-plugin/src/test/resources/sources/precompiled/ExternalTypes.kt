package sources.precompiled

data class MyExternalType(
    val externalOptionalField: String?,
    val externalRequiredField: String,
    val externalOptionalWithInitializer: String = "default",
    val externalOptionalWithInitializerFromOther: String = externalRequiredField,
){

    val externalDerivedProperty: String
        get() = externalRequiredField + externalOptionalWithInitializer
}