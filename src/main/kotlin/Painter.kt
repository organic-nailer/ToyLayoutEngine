class Painter {
    sealed class DisplayCommand {
        data class SolidColor(
            val color: CSSParser.CssColor,
            val rect: LayoutTree.Rect
        ): DisplayCommand()
    }

    fun buildDisplayList(layoutRoot: LayoutTree.LayoutBox): List<Painter.DisplayCommand> {
        val list = mutableListOf<DisplayCommand>()
        renderLayoutBox(list, layoutRoot)
        return list
    }

    private fun renderLayoutBox(list: MutableList<DisplayCommand>, layoutBox: LayoutTree.LayoutBox) {
        renderBackground(list, layoutBox)
        renderBorders(list, layoutBox)
        //TODO render text

        for(child in layoutBox.children) {
            renderLayoutBox(list, child)
        }
    }

    private fun renderBackground(list: MutableList<DisplayCommand>, layoutBox: LayoutTree.LayoutBox) {
        val color = getColor(layoutBox, "background") ?: return
        list.add(DisplayCommand.SolidColor(color, layoutBox.dimensions.borderBox()))
    }

    private fun getColor(layoutBox: LayoutTree.LayoutBox, name: String): CSSParser.CssColor? {
        when(layoutBox.boxType) {
            LayoutTree.BoxType.BlockNode,
            LayoutTree.BoxType.InlineNode -> {
                val c = layoutBox.getStyleNode().value(name)
                if(c is CSSParser.CssColor) return c
                return null
            }
            LayoutTree.BoxType.AnonymousBlock -> return null
        }
    }

    private fun renderBorders(list: MutableList<DisplayCommand>, layoutBox: LayoutTree.LayoutBox) {
        val color = getColor(layoutBox, "border-color") ?: return
        val d = layoutBox.dimensions
        val borderBox = d.borderBox()

        //left
        list.add(
            DisplayCommand.SolidColor(
                color, LayoutTree.Rect(
                    borderBox.x, borderBox.y, d.border.left, borderBox.height
                )
            )
        )
        //right
        list.add(
            DisplayCommand.SolidColor(
                color, LayoutTree.Rect(
                    borderBox.x + borderBox.width - d.border.right,
                    borderBox.y, d.border.right, borderBox.height
                )
            )
        )
        //top
        list.add(
            DisplayCommand.SolidColor(
                color, LayoutTree.Rect(
                    borderBox.x, borderBox.y, borderBox.width, d.border.top
                )
            )
        )
        //bottom
        list.add(
            DisplayCommand.SolidColor(
                color, LayoutTree.Rect(
                    borderBox.x,
                    borderBox.y + borderBox.height - d.border.bottom,
                    borderBox.width, d.border.bottom
                )
            )
        )
    }
}
