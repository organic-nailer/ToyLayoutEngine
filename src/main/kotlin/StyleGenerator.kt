class StyleGenerator {
    fun styleTree(root: DomNode, styleSheet: CSSParser.StyleSheet): StyledNode {
        return StyledNode(
            root,
            when(root.type) {
                DomNode.NodeType.Element -> specifiedValues(root, styleSheet)
                DomNode.NodeType.Text -> mapOf()
                else -> throw NotImplementedError()
            }.toMutableMap(),
            root.children.map { styleTree(it, styleSheet) }
        )
    }

    data class StyledNode(
        val node: DomNode,
        val specifiedValues: MutableMap<String, CSSParser.CssValue>,
        val children: List<StyledNode>
    ) {
        fun value(name: String): CSSParser.CssValue? {
            return specifiedValues[name]
        }

        fun display(): LayoutTree.Display {
            val display = value("display") ?: return LayoutTree.Display.Inline
            return when((display as CSSParser.Keyword).value) {
                "block" -> LayoutTree.Display.Block
                "none" -> LayoutTree.Display.None
                else -> LayoutTree.Display.Inline
            }
        }

        fun lookup(first: String, second: String, default: CSSParser.CssValue): CSSParser.CssValue {
            return specifiedValues[first]
                ?: specifiedValues[second]
                ?: default
        }
    }
    data class MatchedRule(
        val specificity: CSSParser.Specificity,
        val rule: CSSParser.Rule
    )

    private fun matches(elem: DomNode, selector: CSSParser.Selector): Boolean {
        assert(elem.type == DomNode.NodeType.Element)
        return when(selector) {
            is CSSParser.SimpleSelector -> matchesSimpleSelector(elem, selector)
            else -> throw NotImplementedError()
        }
    }

    private fun matchesSimpleSelector(elem: DomNode, selector: CSSParser.SimpleSelector): Boolean {
        if(selector.tagName == null
            && selector.id == null
            && selector.classes.isEmpty()) return true
        if(selector.tagName != null && selector.tagName == elem.tagName) return true
        if(selector.id != null && selector.id == elem.getId()) return true
        if(selector.classes.any { elem.getClasses().contains(it) }) {
            return true
        }
        return false
    }

    private fun matchRule(elem: DomNode, rule: CSSParser.Rule): MatchedRule? {
        return rule.selectors.find { matches(elem,it) }
            ?.let { MatchedRule(it.specificity(), rule) }
    }

    private fun matchingRules(elem: DomNode, styleSheet: CSSParser.StyleSheet): List<MatchedRule> {
        return styleSheet.rules.mapNotNull {
            matchRule(elem, it)
        }
    }

    private fun specifiedValues(elem: DomNode, styleSheet: CSSParser.StyleSheet): Map<String, CSSParser.CssValue> {
        val values = mutableMapOf<String, CSSParser.CssValue>()
        val rules = matchingRules(elem, styleSheet).toMutableList()
        rules.sortBy { it.specificity }
        println("rules = $rules")
        for (rule in rules) {
            for (declaration in rule.rule.declarations) {
                values[declaration.name] = declaration.value
            }
        }
        return values
    }
}
