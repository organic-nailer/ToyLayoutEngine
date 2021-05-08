import org.jetbrains.skija.Color

class CSSParser(
    private var pos: Int,
    private val input: String
)  {
    companion object {
        fun parse(source: String): StyleSheet {
            val parser = CSSParser(0, source)
            return StyleSheet(
                parser.parseRules()
            )
        }
    }

    data class StyleSheet(
        val rules: List<Rule>
    )
    data class Rule(
        val selectors: List<Selector>,
        val declarations: List<Declaration>
    )
    data class Specificity(
        val a: Int, val b: Int, val c: Int
    ): Comparable<Specificity> {
        override fun compareTo(other: Specificity): Int {
            var res = 0
            res += this.a.compareTo(other.a)
            res += this.b.compareTo(other.b)
            res += this.c.compareTo(other.c)
            return res
        }

        override fun toString(): String {
            return "Spec($a,$b,$c)"
        }
    }
    interface Selector {
        fun specificity(): Specificity
    }
    data class SimpleSelector(
        var tagName: String?,
        var id: String?,
        val classes: MutableList<String>
    ): Selector {
        override fun specificity(): Specificity {
            return Specificity(
                id?.length ?: 0,
                classes.size,
                tagName?.length ?: 0
            )
        }

        override fun toString(): String {
            return "sSelector($tagName,$id,$classes)"
        }
    }
    data class Declaration(
        val name: String,
        val value: CssValue
    )
    interface CssValue {
        fun toPx(): Float
    }
    data class Keyword(val value: String): CssValue {
        override fun toPx(): Float {
            TODO("Not yet implemented")
        }
    }
    data class Length(val value: Float, val unit: CssUnit): CssValue {
        override fun toPx(): Float {
            return when(unit) {
                CssUnit.Px -> value
            }
        }
    }
    enum class CssUnit {
        Px
    }
    data class CssColor(
        val r: Short, val g: Short, val b: Short,
        val a: Short = 255
    ): CssValue {
        override fun toPx(): Float {
            TODO("Not yet implemented")
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

    private fun parseRules(): List<Rule> {
        val rules = mutableListOf<Rule>()
        while(true) {
            consumeWhiteSpace()
            if(eof()) break
            rules.add(parseRule())
        }
        return rules
    }

    private fun parseSimpleSelector(): SimpleSelector {
        val selector = SimpleSelector(null,null, mutableListOf())
        while (!eof()) {
            val c = nextChar()
            when {
                c == '#' -> {
                    consumeChar()
                    selector.id = parseIdentifier()
                }
                c == '.' -> {
                    consumeChar()
                    selector.classes.add(parseIdentifier())
                }
                c == '*' -> {
                    consumeChar()
                }
                c.isLetterOrDigit()
                        || c == '-'
                        || c == '_' -> {
                    selector.tagName = parseIdentifier()
                }
                else -> break
            }
        }
        return selector
    }
    fun parseRule(): Rule {
        return Rule(
            parseSelectors(),
            parseDeclarations()
        )
    }
    private fun parseSelectors(): List<Selector> {
        val selectors = mutableListOf<Selector>()
        while(true) {
            selectors.add(parseSimpleSelector())
            consumeWhiteSpace()
            when(val c = nextChar()) {
                ',' -> {
                    consumeChar()
                    consumeWhiteSpace()
                }
                '{' -> break
                else -> {
                    throw Exception("Unexpected Character: $c")
                }
            }
        }
        selectors.sortBy { it.specificity() }
        return selectors
    }
    private fun parseDeclarations(): List<Declaration> {
        assert(consumeChar() == '{')
        val declarations = mutableListOf<Declaration>()
        while(true) {
            consumeWhiteSpace()
            if(nextChar() == '}') {
                consumeChar()
                break
            }
            declarations.add(parseDeclaration())
        }
        return declarations
    }
    private fun parseDeclaration(): Declaration {
        val propertyName = parseIdentifier()
        consumeWhiteSpace()
        assert(consumeChar() == ':')
        consumeWhiteSpace()
        val value = parseValue()
        consumeWhiteSpace()
        assert(consumeChar() == ';')

        return Declaration(propertyName,value)
    }
    private fun parseValue(): CssValue {
        val c = nextChar()
        return when {
            c.isDigit() -> parseLength()
            c == '#' -> parseColor()
            else -> Keyword(parseIdentifier())
        }
    }
    private fun parseLength(): Length {
        return Length(parseFloat(), parseUnit())
    }
    private fun parseFloat(): Float {
        val s = consumeWhile { it.isDigit() || it == '.' }
        return s.toFloat()
    }
    private fun parseUnit(): CssUnit {
        return when(val c = parseIdentifier().toLowerCase()) {
            "px" -> CssUnit.Px
            else -> throw Exception("NotImplemented Unit: $c")
        }
    }
    private fun parseColor(): CssColor {
        assert(consumeChar() == '#')
        return CssColor(
            parseHexPair(),
            parseHexPair(),
            parseHexPair(),
        )
    }
    private fun parseHexPair(): Short {
        val s = input.substring(pos until pos + 2)
        pos += 2
        return s.toShort(16)
    }
    private fun parseIdentifier(): String {
        return consumeWhile {
            it.isLetterOrDigit() || it == '-' || it == '_'
        }
    }
}
