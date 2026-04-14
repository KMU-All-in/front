package com.example.allin.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: Map<String, Int> = emptyMap()
    private var totalBudget: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    
    // 카테고리 색상들
    private val categoryColors = listOf(
        "#4F6FFF", "#FF6384", "#36A2EB", "#FFCE56", "#4BC0C0", "#9966FF", "#FF9F40", "#8BC34A"
    ).map { Color.parseColor(it) }
    
    // 남은 예산 색상 (연한 회색)
    private val remainingColor = Color.parseColor("#E0E0E0")

    fun setData(newData: Map<String, Int>, budget: Int) {
        data = newData
        totalBudget = budget
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 예산이 없거나 데이터가 모두 0이면 빈 원(회색) 그리기
        if (totalBudget <= 0) {
            drawEmptyCircle(canvas)
            return
        }

        val totalSpent = data.values.sum().toFloat()
        
        val width = width.toFloat()
        val height = height.toFloat()
        val size = if (width < height) width else height
        val margin = 20f
        rectF.set(margin, margin, size - margin, size - margin)

        var startAngle = -90f

        // 1. 소비한 카테고리별 아크 그리기
        data.values.forEachIndexed { index, value ->
            if (value > 0) {
                // 전체 예산 대비 이 카테고리가 차지하는 각도 계산
                val sweepAngle = (value.toFloat() / totalBudget.toFloat()) * 360f
                paint.color = categoryColors[index % categoryColors.size]
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                startAngle += sweepAngle
            }
        }

        // 2. 남은 예산 아크 그리기 (회색)
        val remainingBudget = totalBudget - totalSpent
        if (remainingBudget > 0) {
            val sweepAngle = (remainingBudget / totalBudget.toFloat()) * 360f
            paint.color = remainingColor
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
        } else if (totalSpent > totalBudget) {
            // 예산 초과 시 경고 색상 (선택 사항)
            // paint.color = Color.RED
            // canvas.drawArc(rectF, -90f, 360f, true, paint)
        }

        // 3. 중앙 구멍 뚫기 (도넛 효과)
        paint.color = Color.WHITE
        val centerX = width / 2
        val centerY = height / 2
        val innerRadius = (size / 2) * 0.6f
        canvas.drawCircle(centerX, centerY, innerRadius, paint)
        
        // 4. 중앙에 텍스트 추가 (선택 사항: 사용률 표시)
        paint.color = Color.BLACK
        paint.textSize = size * 0.1f
        paint.textAlign = Paint.Align.CENTER
        val usagePercent = ((totalSpent / totalBudget.toFloat()) * 100).toInt()
        val text = "${usagePercent}% 사용"
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, centerX, centerY + (textBounds.height() / 2), paint)
    }

    private fun drawEmptyCircle(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val size = if (width < height) width else height
        rectF.set(20f, 20f, size - 20f, size - 20f)
        paint.color = remainingColor
        canvas.drawArc(rectF, 0f, 360f, true, paint)
        
        paint.color = Color.WHITE
        canvas.drawCircle(width / 2, height / 2, (size / 2) * 0.6f, paint)
    }
}
