class LayoutTree {
    data class Dimensions(
        val content: Rect,
        val padding: EdgeSizes,
        val border: EdgeSizes,
        val margin: EdgeSizes,
    ) {
        companion object {
            fun default(): Dimensions = Dimensions(
                Rect.default(), EdgeSizes.default(),
                EdgeSizes.default(), EdgeSizes.default()
            )
        }

        override fun toString(): String {
            return "Dim(c=$content,p=$padding,b=$border,m=$margin)"
        }

        fun paddingBox() = content.expandedBy(padding)
        fun borderBox() = paddingBox().expandedBy(border)
        fun marginBox() = borderBox().expandedBy(margin)

        fun clone(): Dimensions = Dimensions(
            content.copy(), padding.copy(),
            border.copy(), margin.copy()
        )
    }
    data class Rect(
        var x: Float,
        var y: Float,
        var width: Float,
        var height: Float
    ) {
        companion object {
            fun default(): Rect = Rect(
                0f,0f,0f,0f
            )
        }

        override fun toString(): String {
            return "R($x,$y,$width,$height)"
        }

        //edge分だけ箱を拡張する
        fun expandedBy(edge: EdgeSizes): Rect {
            return Rect(
                x - edge.left,
                y - edge.top,
                width + edge.left + edge.right,
                height + edge.top + edge.bottom
            )
        }
    }
    data class EdgeSizes(
        var left: Float,
        var right: Float,
        var top: Float,
        var bottom: Float,
    ) {
        companion object {
            fun default(): EdgeSizes = EdgeSizes(
                0f,0f,0f,0f
            )
        }

        override fun toString(): String {
            return "E($left,$right,$top,$bottom)"
        }
    }

    data class LayoutBox(
        val dimensions: Dimensions,
        val boxType: BoxType,
        val node: StyleGenerator.StyledNode?,
        val children: MutableList<LayoutBox>
    ) {
        companion object {
            fun new(boxType: BoxType, node: StyleGenerator.StyledNode?): LayoutBox = LayoutBox(
                Dimensions.default(), boxType, node,
                mutableListOf()
            )
        }

        fun getInlineContainer(): LayoutBox {
            return when(boxType) {
                BoxType.InlineNode,
                BoxType.AnonymousBlock -> this
                BoxType.BlockNode -> {
                    val c = children.last()
                    when (c.boxType) {
                        BoxType.AnonymousBlock -> { }
                        else -> children.add(LayoutBox.new(BoxType.AnonymousBlock, null))
                    }
                    children.last()
                }
            }
        }

        fun getStyleNode(): StyleGenerator.StyledNode {
            return when(boxType) {
                BoxType.BlockNode,
                BoxType.InlineNode -> node!!
                BoxType.AnonymousBlock -> throw Exception("Anonymous block box has no style node")
            }
        }

        fun layout(containingBlock: Dimensions) {
            when(boxType) {
                BoxType.BlockNode -> layoutBlock(containingBlock)
                BoxType.InlineNode -> { }//TODO implement
                BoxType.AnonymousBlock -> { }//TODO implement
            }
        }

        private fun layoutBlock(containingBlock: Dimensions) {
            println("layoutBlock: ${node?.node?.attributes?.get("class")}")
            println("block: $containingBlock")
            calculateBlockWidth(containingBlock)
            calculateBlockPosition(containingBlock)
            println("positioned: $containingBlock")
            layoutBlockChildren()
            calculateBlockHeight()
        }

        private fun calculateBlockWidth(containingBlock: Dimensions) {
            println("contained: $containingBlock")
            val style = getStyleNode()
            val auto = CSSParser.Keyword("auto")
            var width = style.value("width") ?: auto
            val zero = CSSParser.Length(0f, CSSParser.CssUnit.Px)

            var marginLeft = style.lookup("margin-left", "margin", zero)
            var marginRight = style.lookup("margin-right", "margin", zero)
            val borderLeft = style.lookup("border-left-width", "border-width", zero)
            val borderRight = style.lookup("border-right-width", "border-width", zero)
            val paddingLeft = style.lookup("padding-left", "padding", zero)
            val paddingRight = style.lookup("padding-right", "padding", zero)

            val total = listOf(
                marginLeft, marginRight, borderLeft, borderRight,
                paddingLeft, paddingRight
            ).sumByDouble { it.toPx().toDouble() }.toFloat()

            if(width != auto && total > containingBlock.content.width) {
                if(marginLeft == auto) {
                    marginLeft = CSSParser.Length(0f, CSSParser.CssUnit.Px)
                }
                if(marginRight == auto) {
                    marginRight = CSSParser.Length(0f, CSSParser.CssUnit.Px)
                }
            }

            val underflow = containingBlock.content.width - total

            val wIsAuto = width == auto
            val mLeftIsAuto = marginLeft == auto
            val mRightIsAuto = marginRight == auto
            if(!wIsAuto && !mLeftIsAuto && !mRightIsAuto) {
                //autoがない場合は右マージンで調整
                marginRight = CSSParser.Length(marginRight.toPx() + underflow, CSSParser.CssUnit.Px)
            } //ひとつだけautoの場合はそれをあまりの長さにして調整
            else if(!wIsAuto && !mLeftIsAuto && mRightIsAuto) {
                marginRight = CSSParser.Length(underflow, CSSParser.CssUnit.Px)
            }
            else if(!wIsAuto && mLeftIsAuto && !mRightIsAuto) {
                marginLeft = CSSParser.Length(underflow, CSSParser.CssUnit.Px)
            }
            else if(wIsAuto) {
                //幅がAutoの場合は他のautoを0pxにする
                if(mLeftIsAuto) marginLeft = CSSParser.Length(0f, CSSParser.CssUnit.Px)
                if(mRightIsAuto) marginRight = CSSParser.Length(0f, CSSParser.CssUnit.Px)

                if(underflow >= 0f) { //あまりを幅にする
                    width = CSSParser.Length(underflow, CSSParser.CssUnit.Px)
                }
                else { //オーバーフローの場合は幅を0にして右マージンをへらす
                    width = CSSParser.Length(0f, CSSParser.CssUnit.Px)
                    marginRight = CSSParser.Length(marginRight.toPx() + underflow, CSSParser.CssUnit.Px)
                }
            }
            else if(!wIsAuto && mLeftIsAuto && mRightIsAuto) {
                //左右マージンがautoの場合は均等に振る
                marginLeft = CSSParser.Length(underflow / 2f, CSSParser.CssUnit.Px)
                marginRight = CSSParser.Length(underflow / 2f, CSSParser.CssUnit.Px)
            }

            val d = dimensions
            d.content.width = width.toPx()
            d.padding.left = paddingLeft.toPx()
            d.padding.right = paddingRight.toPx()
            d.border.left = borderLeft.toPx()
            d.border.right = borderRight.toPx()
            d.margin.left = marginLeft.toPx()
            d.margin.right = marginRight.toPx()
        }

        private fun calculateBlockPosition(containingBlock: Dimensions) {
            val style = getStyleNode()
            val d = dimensions
            val zero = CSSParser.Length(0f, CSSParser.CssUnit.Px)

            d.margin.top = style.lookup("margin-top", "margin", zero).toPx()
            d.margin.bottom = style.lookup("margin-bottom", "margin", zero).toPx()
            d.border.top = style.lookup("border-top-width", "border-width", zero).toPx()
            d.border.bottom = style.lookup("border-bottom-width", "border-width", zero).toPx()
            d.padding.top = style.lookup("padding-top", "padding", zero).toPx()
            d.padding.bottom = style.lookup("padding-bottom", "padding", zero).toPx()

            d.content.x = containingBlock.content.x + d.margin.left + d.border.left + d.padding.left
            d.content.y = containingBlock.content.height + containingBlock.content.y + d.margin.top + d.border.top + d.padding.top
        }

        private fun layoutBlockChildren() {
            //マージンの折りたたみは未実装
            val d = dimensions
            for (child in children) {
                child.layout(d.clone())
                d.content.height = d.content.height + child.dimensions.marginBox().height
            }
        }

        private fun calculateBlockHeight() {
            val height = getStyleNode().value("height")
            if(height is CSSParser.Length) {
                dimensions.content.height = height.value
            }
        }
    }
    enum class BoxType {
        BlockNode, InlineNode, AnonymousBlock
    }
    enum class Display {
        Inline, Block, None
    }

    fun generate(node: StyleGenerator.StyledNode, containingBlock: Dimensions): LayoutBox {
        containingBlock.content.height = 0f

        val rootBox = buildLayoutTree(node)
        rootBox.layout(containingBlock)
        return rootBox
    }

    private fun buildLayoutTree(styleNode: StyleGenerator.StyledNode): LayoutBox {
        val root = LayoutBox.new(when (styleNode.display()) {
            Display.Block -> BoxType.BlockNode
            Display.Inline -> BoxType.InlineNode
            Display.None -> throw Exception("Root node has display: none.")
        }, styleNode)

        for (child in styleNode.children) {
            when(child.display()) {
                Display.Block -> root.children.add(buildLayoutTree(child))
                Display.Inline -> {
                    root.getInlineContainer().children.add(buildLayoutTree(child))
                }
                Display.None -> {}
            }
        }
        return root
    }
}