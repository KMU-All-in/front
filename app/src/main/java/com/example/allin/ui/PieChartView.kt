package com.example.allin.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: Map<String, Int> = emptyMap()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val colors = listOf(
        "#FF6384", "#36A2EB", "#FFCE56", "#4BC0C0", "#9966FF", "#FF9F40", "#8BC34A", "#F44336"
    ).map { Color.parseColor(it) }

    fun setData(newData: Map<String, Int>) {
        data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val total = data.values.sum().toFloat()
        if (total == 0f) return

        val width = width.toFloat()
        val height = height.toFloat()
        val size = if (width < height) width else height
        val margin = 20f
        rectF.set(margin, margin, size - margin, size - margin)

        var startAngle = -90f
        data.values.forEachIndexed { index, value ->
            val sweepAngle = (value / total) * 360f
            paint.color = colors[index % colors.size]
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }

        // Draw center circle for donut effect
        paint.color = Color.WHITE
        val centerMargin = size * 0.2f
        canvas.drawCircle(size / 2, size / 2, (size / 2) - centerMargin, paint)
    }
}
