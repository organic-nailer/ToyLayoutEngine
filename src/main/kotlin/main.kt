import org.jetbrains.skija.*
import org.jetbrains.skija.impl.Library
import org.jetbrains.skija.impl.Stats
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import kotlin.math.max

fun main() {
    val ui = Window()
    ui.start()
    ui.setPaintData(listOf(
        Painter.DisplayCommand.SolidColor(
            CSSParser.CssColor(255,0,0),
            LayoutTree.Rect(10f,10f,200f,200f)
        ),
        Painter.DisplayCommand.SolidColor(
            CSSParser.CssColor(255,128,0),
            LayoutTree.Rect(20f,20f,180f,180f)
        ),
    ))
    val domNode = HtmlParser.parse("""
        <div class="a">
          <div class="b">
            <div class="c">
              <div class="d">
                <div class="e">
                  <div class="f">
                    <div class="g">
                    </div>
                  </div>
                  <div class="c">
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
    """.trimIndent())
    domNode.print("")
    val styleSheet = CSSParser.parse("""
        * { display: block; padding: 12px; }
        .a { background: #ff0000; }
        .b { background: #ffa500; }
        .c { background: #ffff00; }
        .d { background: #008000; }
        .e { background: #0000ff; }
        .f { background: #4b0082; }
        .g { background: #800080; }
    """.trimIndent())
    val styled = StyleGenerator().styleTree(domNode, styleSheet)
    println(styled)
    val box = LayoutTree().generate(styled, LayoutTree.Dimensions(
        LayoutTree.Rect(100f,100f,960f,640f),
        LayoutTree.EdgeSizes.default(),
        LayoutTree.EdgeSizes.default(),
        LayoutTree.EdgeSizes.default(),
    ))
    println(box)
    val commands = Painter().buildDisplayList(box)
    //println(commands)
    Thread.sleep(1000)
    ui.setPaintData(commands)
}

class Window: Thread() {
    var window: Long = 0
    var width: Int = 0
    var height: Int = 0
    var dpi = 1f
    var xPos = 0
    var yPos = 0
    private val vsync = true
    private val stats = true
    private var refreshRates = listOf<Int>()
    private val os = System.getProperty("os.name").toLowerCase()

    val paintData = mutableListOf<Painter.DisplayCommand>()
    var shouldRepaint = true

    fun setPaintData(newData: List<Painter.DisplayCommand>) {
        paintData.clear()
        paintData.addAll(newData)
        shouldRepaint = true
    }

    private fun getRefreshRates(): List<Int> {
        val monitors = glfwGetMonitors()!!
        val res = mutableListOf<Int>()
        for(i in 0 until monitors.capacity()) {
            res.add(glfwGetVideoMode(monitors[i])?.refreshRate() ?: 0)
        }
        return res
    }

    override fun run() {
        super.run()
        GLFWErrorCallback.createPrint(System.err).set()
        if(!glfwInit()) throw Exception("Unable to init GLFW")

        val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        val width = (vidMode.width() * 0.75).toInt()
        val height = (vidMode.height() * 0.75).toInt()
        val bounds = IRect.makeXYWH(
            max(0, (vidMode.width() - width) / 2),
            max(0, (vidMode.height() - width) / 2),
            width, height
        )
        refreshRates = getRefreshRates()
        createWindow(bounds)
        loop()
        dispose()
    }

    private fun dispose() {
        glfwFreeCallbacks(window)
        glfwDestroyWindow(window)
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }

//    fun run(bounds: IRect) {
//        refreshRates = getRefreshRates()
//        createWindow(bounds)
//        loop()
//        glfwFreeCallbacks(window)
//        glfwDestroyWindow(window)
//        glfwTerminate()
//        glfwSetErrorCallback(null)?.free()
//    }

    private fun updateDimensions() {
        val newWidth = IntArray(1)
        val newHeight = IntArray(1)
        glfwGetFramebufferSize(window, newWidth, newHeight)

        val xScale = FloatArray(1)
        val yScale = FloatArray(1)
        glfwGetWindowContentScale(window, xScale, yScale)

        width = (newWidth[0] / xScale[0]).toInt()
        height = (newHeight[0] / yScale[0]).toInt()
        dpi = xScale[0]
        println("FrameBufferSize ${newWidth[0]}x${newHeight[0]},scale $dpi, window ${width}x${height}")
    }

    private fun createWindow(bounds: IRect) {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        window = glfwCreateWindow(
            bounds.width, bounds.height, "ToyLayoutEngine",
            NULL, NULL
        )
        if(window == NULL) {
            throw Exception("Failed to create the GLFW window")
        }
        glfwSetKeyCallback(window) { w, key, _, action, _ ->
            if(key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(w, true)
            }
        }
        glfwSetWindowPos(window, bounds.left, bounds.top)
        updateDimensions()
        xPos = width / 2
        yPos = width / 2

        glfwMakeContextCurrent(window)
        glfwSwapInterval(if(vsync) 1 else 0)
        glfwShowWindow(window)
    }

    private lateinit var context: DirectContext
    private var renderTarget: BackendRenderTarget? = null
    private var surface: Surface? = null
    private lateinit var canvas: Canvas

    private fun initSkia() {
        Stats.enabled = true
        surface?.close()
        renderTarget?.close()

        renderTarget = BackendRenderTarget.makeGL(
            (width * dpi).toInt(),
            (height * dpi).toInt(),
            0, 8, 0,
            FramebufferFormat.GR_GL_RGBA8
        )

        surface = Surface.makeFromBackendRenderTarget(
            context,
            renderTarget!!,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.getDisplayP3(),
            SurfaceProps(PixelGeometry.RGB_H)
        )

        canvas = surface!!.canvas
    }

    private fun draw() {
        //Scenes.draw(canvas, width, height, dpi, xPos, yPos)
        if(shouldRepaint) {
            canvas.clear(0xFF8080FF.toInt())
            drawCommands()
            context.flush()
            glfwSwapBuffers(window)
            shouldRepaint = false
        }
    }

    private fun drawCommands() {
        if(paintData.isEmpty()) return
        paintData.forEach { command ->
            when(command) {
                is Painter.DisplayCommand.SolidColor -> {
                    val r = command.rect
                    val c = command.color
                    canvas.drawRect(
                        Rect.makeXYWH(r.x,r.y,r.width,r.height),
                        Paint().setColor(Color.makeARGB(
                            c.a.toInt(),c.r.toInt(),c.g.toInt(),c.b.toInt()
                        ))
                    )
                }
            }
        }
    }

    private fun drawSample() {
        canvas.clear(0xFF8080FF.toInt())

        canvas.drawTriangles(
            arrayOf(
                Point(320f, 70f),
                Point(194f, 287f),
                Point(446f, 287f)
            ), intArrayOf(-0x10000, -0xff0100, -0xffff01),
            Paint()
        )

        val path: Path = Path().moveTo(253f, 216f)
            .cubicTo(283f, 163.5f, 358f, 163.5f, 388f, 216f)
            .cubicTo(358f, 268.5f, 283f, 268.5f, 253f, 216f)
            .closePath()
        canvas.drawPath(path, Paint().setColor(-0x1))

        canvas.drawCircle(320f, 217f, 16f, Paint().setColor(-0x1000000))
    }

    private fun loop() {
        GL.createCapabilities()
        if("false".equals(System.getProperty("skija.staticLoad"))) {
            Library.load()
        }
        context = DirectContext.makeGL()

        org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback(window) { _,_,_ ->
            shouldRepaint = true
            updateDimensions()
            initSkia()
            draw()
        }

        glfwSetCursorPosCallback(window) { _, x,y ->
            if(os.contains("mac") || os.contains("darwin")) {
                xPos = x.toInt()
                yPos = y.toInt()
            }
            else {
                xPos = (x / dpi).toInt()
                yPos = (y / dpi).toInt()
            }
        }

        initSkia()

        while(!glfwWindowShouldClose(window)) {
            draw()
            glfwPollEvents()
        }
    }
}
