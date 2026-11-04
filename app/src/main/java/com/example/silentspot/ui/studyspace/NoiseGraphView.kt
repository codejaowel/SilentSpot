package com.example.silentspot.ui.studyspace

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.silentspot.R

class NoiseGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<Float> = emptyList()
    private var threshold = 60f
    private val minDb = 30f
    private val maxDb = 100f

    private val density = resources.displayMetrics.density
    private val lineColor = ContextCompat.getColor(context, R.color.colorPrimary)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor; style = Paint.Style.STROKE; strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorDivider); strokeWidth = density
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorNoisy); strokeWidth = 1.5f * density
        style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorTextMuted); textSize = 10 * density
    }
    private val linePath = Path()
    private val fillPath = Path()

    fun setData(values: List<Float>, threshold: Int) {
        points = values
        this.threshold = threshold.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val left = 28 * density
        val top = 8 * density
        val right = width - 4 * density
        val bottom = height - 8 * density
        fun y(db: Float) = bottom - (db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb) * (bottom - top)

        // Grid + labels
        for (db in listOf(40f, 60f, 80f, 100f)) {
            canvas.drawLine(left, y(db), right, y(db), gridPaint)
            canvas.drawText(db.toInt().toString(), 0f, y(db) + 4 * density, labelPaint)
        }
        canvas.drawLine(left, y(threshold), right, y(threshold), thresholdPaint)

        if (points.size < 2) {
            canvas.drawText("Waiting for readings...", left + 8 * density, (top + bottom) / 2, labelPaint)
            return
        }

        val step = (right - left) / (points.size - 1)
        linePath.reset()
        points.forEachIndexed { i, v ->
            val x = left + i * step
            if (i == 0) linePath.moveTo(x, y(v)) else linePath.lineTo(x, y(v))
        }
        fillPath.reset()
        fillPath.set(linePath)
        fillPath.lineTo(right, bottom)
        fillPath.lineTo(left, bottom)
        fillPath.close()

        fillPaint.shader = LinearGradient(0f, top, 0f, bottom,
            (lineColor and 0x00FFFFFF) or 0x55000000, lineColor and 0x00FFFFFF, Shader.TileMode.CLAMP)

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
