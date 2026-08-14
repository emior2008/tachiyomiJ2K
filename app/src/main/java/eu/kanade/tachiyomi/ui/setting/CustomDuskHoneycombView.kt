package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.sqrt

class CustomDuskHoneycombView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private data class Cell(
            val x: Float,
            val y: Float,
            val radius: Float,
            val color: Int,
        )

        var onColorSelected: ((Int) -> Unit)? = null

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = resources.displayMetrics.density * 2f
            }
        private val path = Path()
        private val cells = mutableListOf<Cell>()
        private var selectedColor: Int? = null

        private val saturationValueRows =
            listOf(
                0.35f to 1.00f,
                0.62f to 1.00f,
                0.92f to 0.96f,
                0.90f to 0.74f,
                0.85f to 0.52f,
                0.78f to 0.34f,
            )

        init {
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        fun setSelectedColor(color: Int) {
            selectedColor = forceOpaque(color)
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val density = resources.displayMetrics.density
            val desiredHeight = (260f * density).toInt()
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(width, height)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildCells(w.toFloat(), h.toFloat())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            cells.forEach { cell ->
                buildHexPath(cell.x, cell.y, cell.radius)
                fillPaint.color = cell.color
                canvas.drawPath(path, fillPaint)

                if (selectedColor == cell.color) {
                    strokePaint.color =
                        if (ColorUtils.calculateLuminance(cell.color) > 0.45) Color.BLACK else Color.WHITE
                    strokePaint.strokeWidth = resources.displayMetrics.density * 3f
                    canvas.drawPath(path, strokePaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true

            val hit =
                cells
                    .minByOrNull { cell ->
                        val dx = event.x - cell.x
                        val dy = event.y - cell.y
                        dx * dx + dy * dy
                    }?.takeIf { cell ->
                        val dx = event.x - cell.x
                        val dy = event.y - cell.y
                        val distanceSquared = dx * dx + dy * dy
                        val hitRadius = cell.radius * 0.98f
                        distanceSquared <= hitRadius * hitRadius
                    }

            if (hit != null) {
                selectedColor = hit.color
                invalidate()
                onColorSelected?.invoke(hit.color)
                performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun rebuildCells(
            width: Float,
            height: Float,
        ) {
            cells.clear()
            if (width <= 0f || height <= 0f) return

            val columns = 10
            val rows = saturationValueRows.size
            val sqrtThree = sqrt(3f)

            val radiusFromWidth = width / (sqrtThree * (columns + 0.5f))
            val radiusFromHeight = height / (2f + 1.5f * (rows - 1))
            val radius = min(radiusFromWidth, radiusFromHeight) * 0.92f
            val hexWidth = sqrtThree * radius
            val gridWidth = hexWidth * (columns + 0.5f)
            val gridHeight = radius * (2f + 1.5f * (rows - 1))
            val originX = (width - gridWidth) / 2f
            val originY = (height - gridHeight) / 2f

            repeat(rows) { row ->
                val (saturation, value) = saturationValueRows[row]
                repeat(columns) { column ->
                    val offset = if (row % 2 == 1) 0.5f else 0f
                    val hue = ((column + offset) / columns.toFloat() * 360f) % 360f
                    val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
                    val x = originX + hexWidth / 2f + (column + offset) * hexWidth
                    val y = originY + radius + row * radius * 1.5f
                    cells += Cell(x, y, radius * 0.94f, color)
                }
            }
        }

        private fun buildHexPath(
            centerX: Float,
            centerY: Float,
            radius: Float,
        ) {
            path.reset()
            repeat(6) { index ->
                val angle = Math.toRadians((60.0 * index - 30.0))
                val x = centerX + radius * kotlin.math.cos(angle).toFloat()
                val y = centerY + radius * kotlin.math.sin(angle).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }

        private fun forceOpaque(color: Int): Int = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }
