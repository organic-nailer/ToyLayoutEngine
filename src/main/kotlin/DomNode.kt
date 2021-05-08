data class DomNode(
    val type: NodeType,
    val tagName: String? = null,
    val text: String? = null,
    val attributes: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<DomNode> = mutableListOf()
) {
    enum class NodeType {
        Element, Text, Comment
    }

    companion object {
        fun text(data: String) = DomNode(
            type = NodeType.Text, text = data
        )
        fun elem(name: String, attrs: Map<String, String>, children: List<DomNode>) = DomNode(
            type = NodeType.Element, tagName = name,
            attributes = attrs.toMutableMap(),
            children = children.toMutableList()
        )
        fun comment(data: String) = DomNode(
            type = NodeType.Comment, text = data
        )
    }

    fun getId(): String? {
        assert(type == NodeType.Element)
        return attributes["id"]
    }

    fun getClasses(): Set<String> {
        assert(type == NodeType.Element)
        return attributes["class"]?.split(" ")?.toSet() ?: setOf()
    }

    fun print(indent: String) {
        when(type) {
            NodeType.Element -> {
                println("${indent}elem $tagName($attributes):")
                children.forEach { it.print("$indent  ") }
            }
            NodeType.Text -> {
                println("${indent}text: $text")
            }
            NodeType.Comment -> {
                println("${indent}comment: $text")
            }
        }
    }
}
