package sources.requests

data class SimpleRequest(
    val string: String,
    val integer: Int,
    val float: Float
)

data class PrivateBodyRequest(
    val visible: Boolean,
    private val invisible: String,
    @Transient
    val transientFieldInvisible: Int,
)

data class NestedRequest(
    val nestedObject: SimpleRequest
)

data class ComplexRequest(
    val evenMore: Int?,
    val list: List<String>,
    val nestedList: List<List<String>>,
    val nestedMutableList: MutableList<List<List<List<String>>>>,
    val complexList: List<ComplexMapValue>,
    val complexNestedList: List<List<ComplexMapValue>>,
    val complexListStringMap: Map<String, List<String>>,
    val complexListMap: Map<String, List<ComplexMapValue>>,
    val complexNestedListMap: Map<String, List<List<ComplexMapValue>>>,
    val stringMap: Map<String, String>,
    val intValueMap: Map<String, Int>,
    val complexValueMap: Map<String, ComplexMapValue>,
    val enumValueMap: Map<String, MyEnum>,
    val complexEnumValueMap: Map<String, List<MyEnum>>,
    val complexMapValueMap: Map<String, Map<String, ComplexMapValue>>
)

data class NullableCollections(
    val myList: List<String>?,
    val myMap: Map<String, String>?,
    val mySet: Set<String>?,
    val myArray: Array<String>?,
)

data class NullableCollectionsWithDefaults(
    val myList: List<String>? = emptyList(),
    val myMap: Map<String, String>? = emptyMap(),
    val mySet: Set<String>? = emptySet(),
    val myArray: Array<String>? = emptyArray(),
)

data class MembersWithDefaults(
    val myList: List<String> = emptyList(),
    val myMap: Map<String, String> = emptyMap(),
    val mySet: Set<String> = emptySet(),
    val myArray: Array<String> = emptyArray(),
)

data class ComplexMapValue(
    val something: String
)

data class ComplexMapKey(
    val something: Int
)

enum class MyEnum {
    ONE,
    TWO,
    THREE
}

enum class PolyDiscriminator {
    IMPL1,
    IMPL2
}

data class SimplePolymorphicRequest(
    val polyParam: PolyRequest
)

interface PolyRequest
