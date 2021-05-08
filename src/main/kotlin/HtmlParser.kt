class HtmlParser(
    private var pos: Int,
    private val input: String
) {
    companion object {
        fun parse(source: String): DomNode {
            val nodes = HtmlParser(0, source).parseNodes()
            return if(nodes.size == 1) {
                nodes[0]
            } else {
                DomNode.elem(
                    "html", mapOf(), nodes
                )
            }
        }
    }

    private fun nextChar(): Char {
        return input[pos]
    }
    private fun startsWith(s: String): Boolean {
        return input.startsWith(s, pos)
    }
    private fun eof(): Boolean {
        return pos + 1 >= input.length
    }
    private fun consumeChar(): Char {
        return input[pos++]
    }
    private fun consumeWhile(test: (Char) -> Boolean): String {
        val result = mutableListOf<Char>()
        while(!eof() && test(nextChar())) {
            result.add(consumeChar())
        }
        return result.joinToString("")
    }
    private fun consumeWhiteSpace() {
        consumeWhile { it.isWhitespace() }
    }
    private fun parseTagName(): String {
        return consumeWhile { c -> c.isLetterOrDigit() }
    }

    private fun parseNode(): DomNode {
        return when(nextChar()) {
            '<' -> parseElement()
            else -> parseText()
        }
    }
    private fun parseText(): DomNode {
        return DomNode.text(consumeWhile { it != '<' })
    }
    private fun parseElement(): DomNode {
        assert(consumeChar() == '<')
        val tagName = parseTagName()
        val attrs = parseAttributes()
        assert(consumeChar() == '>')

        val children = parseNodes()

        assert(consumeChar() == '<')
        assert(consumeChar() == '/')
        assert(parseTagName() == tagName)
        assert(consumeChar() == '>')

        return DomNode.elem(
            tagName, attrs, children
        )
    }
    private fun parseAttr(): Pair<String,String> {
        val name = parseTagName()
        assert(consumeChar() == '=')
        val value = parseAttrValue()
        return name to value
    }
    private fun parseAttrValue(): String {
        val openQuote = consumeChar()
        assert(openQuote == '"' || openQuote == '\'')
        val value = consumeWhile { it != openQuote }
        assert(consumeChar() == openQuote)
        return value
    }
    private fun parseAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        while(true) {
            consumeWhiteSpace()
            if(nextChar() == '>') break
            val (name, value) = parseAttr()
            attributes[name] = value
        }
        return attributes
    }
    fun parseNodes(): List<DomNode> {
        val nodes = mutableListOf<DomNode>()
        while (true) {
            consumeWhiteSpace()
            if(eof() || startsWith("</")) break
            nodes.add(parseNode())
        }
        return nodes
    }
}