package com.example.pdr_zjj.UI

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

class TrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pdrTrackPoints = mutableListOf<Pair<Double, Double>>()
    private val gpsTrackPoints = mutableListOf<Pair<Double, Double>>()
    private var showGpsTrack = true

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val pdrTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val gpsTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13A8A8")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val pdrArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 5.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val gpsArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13A8A8")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pdrArrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private val gpsArrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13A8A8")
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7C93")
        textSize = 26f
    }

    private val scaleLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2D3D")
        textSize = 28f
    }

    private val scaleBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2D3D")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val scalePanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var userScale = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = userScale
                val newScale = (oldScale * detector.scaleFactor).coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
                if (abs(newScale - oldScale) < 1e-4f) return true

                val ratio = newScale / oldScale
                val centerX = width / 2f
                val centerY = height / 2f
                val relativeFocusX = detector.focusX - centerX
                val relativeFocusY = detector.focusY - centerY

                panX = relativeFocusX - (relativeFocusX - panX) * ratio
                panY = relativeFocusY - (relativeFocusY - panY) * ratio
                userScale = newScale
                invalidate()
                return true
            }
        }
    )

    fun reset() {
        pdrTrackPoints.clear()
        gpsTrackPoints.clear()
        resetViewport()
        invalidate()
    }

    fun resetViewport() {
        userScale = 1f
        panX = 0f
        panY = 0f
    }

    fun addPoint(eastMeter: Double, northMeter: Double) {
        pdrTrackPoints.add(Pair(eastMeter, northMeter))
        invalidate()
    }

    fun addGpsPoint(eastMeter: Double, northMeter: Double) {
        gpsTrackPoints.add(Pair(eastMeter, northMeter))
        invalidate()
    }

    fun setShowGpsTrack(show: Boolean) {
        if (showGpsTrack == show) return
        showGpsTrack = show
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    panX += dx
                    panY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val margin = 60f
        val cx = w / 2f
        val cy = h / 2f

        canvas.drawColor(Color.WHITE)

        val baseScale = computeBaseScale(w, h, margin)
        val displayScale = (baseScale * userScale).coerceAtLeast(1f)
        val originX = cx + panX
        val originY = cy + panY
        val gridStepMeter = chooseGridStepMeter(displayScale)

        drawGrid(canvas, w, h, originX, originY, displayScale, gridStepMeter)
        drawAxes(canvas, w, h, originX, originY, margin)

        drawTrack(canvas, pdrTrackPoints, originX, originY, displayScale, pdrTrackPaint)
        if (showGpsTrack) {
            drawTrack(canvas, gpsTrackPoints, originX, originY, displayScale, gpsTrackPaint)
        }

        drawDirectionMarkers(
            canvas = canvas,
            points = pdrTrackPoints,
            originX = originX,
            originY = originY,
            scale = displayScale,
            strokePaint = pdrArrowPaint,
            fillPaint = pdrArrowFillPaint
        )

        if (showGpsTrack) {
            drawDirectionMarkers(
                canvas = canvas,
                points = gpsTrackPoints,
                originX = originX,
                originY = originY,
                scale = displayScale,
                strokePaint = gpsArrowPaint,
                fillPaint = gpsArrowFillPaint
            )
        }

        drawTrackEndpoints(canvas, pdrTrackPoints, originX, originY, displayScale, "起点", "终点")
        if (showGpsTrack) {
            drawGpsEndpoint(canvas, gpsTrackPoints, originX, originY, displayScale)
        }
        drawScaleLegend(canvas, w, displayScale, gridStepMeter)

        canvas.drawText("双指缩放，单指拖动", 18f, h - 18f, hintPaint)
    }

    private fun computeBaseScale(w: Float, h: Float, margin: Float): Float {
        val pointsToDisplay = if (showGpsTrack) pdrTrackPoints + gpsTrackPoints else pdrTrackPoints
        if (pointsToDisplay.isEmpty()) {
            return DEFAULT_BASE_SCALE
        }

        var maxEast = 1.0
        var maxNorth = 1.0
        for ((e, n) in pointsToDisplay) {
            maxEast = max(maxEast, abs(e))
            maxNorth = max(maxNorth, abs(n))
        }

        val scaleX = (w / 2f - margin) / maxEast.toFloat()
        val scaleY = (h / 2f - margin) / maxNorth.toFloat()
        return minOf(scaleX, scaleY).coerceAtLeast(1f)
    }

    private fun drawGrid(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        originX: Float,
        originY: Float,
        pixelsPerMeter: Float,
        gridStepMeter: Float
    ) {
        val pixelStep = gridStepMeter * pixelsPerMeter
        if (pixelStep <= 1e-3f) return

        val minVerticalIndex = floor((0f - originX) / pixelStep).toInt()
        val maxVerticalIndex = ceil((widthPx - originX) / pixelStep).toInt()
        for (i in minVerticalIndex..maxVerticalIndex) {
            val x = originX + i * pixelStep
            canvas.drawLine(x, 0f, x, heightPx, gridPaint)
        }

        val minHorizontalIndex = floor((0f - originY) / pixelStep).toInt()
        val maxHorizontalIndex = ceil((heightPx - originY) / pixelStep).toInt()
        for (i in minHorizontalIndex..maxHorizontalIndex) {
            val y = originY + i * pixelStep
            canvas.drawLine(0f, y, widthPx, y, gridPaint)
        }
    }

    private fun drawAxes(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        originX: Float,
        originY: Float,
        margin: Float
    ) {
        canvas.drawLine(originX, margin, originX, heightPx - margin, axisPaint)
        canvas.drawLine(margin, originY, widthPx - margin, originY, axisPaint)
        canvas.drawText("N+", originX + 10f, margin + 30f, textPaint)
        canvas.drawText("E+", widthPx - margin - 50f, originY - 10f, textPaint)
        canvas.drawText("O", originX + 10f, originY - 10f, textPaint)
    }

    private fun drawTrack(
        canvas: Canvas,
        points: List<Pair<Double, Double>>,
        originX: Float,
        originY: Float,
        scale: Float,
        paint: Paint
    ) {
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]

            val x0 = originX + (p0.first * scale).toFloat()
            val y0 = originY - (p0.second * scale).toFloat()
            val x1 = originX + (p1.first * scale).toFloat()
            val y1 = originY - (p1.second * scale).toFloat()

            canvas.drawLine(x0, y0, x1, y1, paint)
        }
    }

    private fun drawDirectionMarkers(
        canvas: Canvas,
        points: List<Pair<Double, Double>>,
        originX: Float,
        originY: Float,
        scale: Float,
        strokePaint: Paint,
        fillPaint: Paint
    ) {
        val fractions = listOf(0.45, 0.85)
        for (fraction in fractions) {
            val segment = findSegmentAtProgress(points, fraction) ?: continue
            val fromX = originX + (segment.from.first * scale).toFloat()
            val fromY = originY - (segment.from.second * scale).toFloat()
            val toX = originX + (segment.to.first * scale).toFloat()
            val toY = originY - (segment.to.second * scale).toFloat()
            drawArrowMarker(canvas, fromX, fromY, toX, toY, strokePaint, fillPaint)
        }
    }

    private fun drawArrowMarker(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        strokePaint: Paint,
        fillPaint: Paint
    ) {
        val dx = toX - fromX
        val dy = toY - fromY
        if (abs(dx) < 1e-4f && abs(dy) < 1e-4f) return

        val angle = atan2(dy, dx)
        val arrowLength = 28f
        val headLength = 24f
        val arrowAngle = Math.toRadians(28.0)

        val tailX = toX - (arrowLength * cos(angle)).toFloat()
        val tailY = toY - (arrowLength * sin(angle)).toFloat()
        canvas.drawLine(tailX, tailY, toX, toY, strokePaint)

        val x2 = toX - (headLength * cos(angle - arrowAngle)).toFloat()
        val y2 = toY - (headLength * sin(angle - arrowAngle)).toFloat()
        val x3 = toX - (headLength * cos(angle + arrowAngle)).toFloat()
        val y3 = toY - (headLength * sin(angle + arrowAngle)).toFloat()

        val path = Path().apply {
            moveTo(toX, toY)
            lineTo(x2, y2)
            lineTo(x3, y3)
            close()
        }
        canvas.drawPath(path, fillPaint)
    }

    private fun drawTrackEndpoints(
        canvas: Canvas,
        points: List<Pair<Double, Double>>,
        originX: Float,
        originY: Float,
        scale: Float,
        startLabel: String,
        endLabel: String
    ) {
        if (points.isEmpty()) return

        val start = points.first()
        val startX = originX + (start.first * scale).toFloat()
        val startY = originY - (start.second * scale).toFloat()
        canvas.drawCircle(startX, startY, 10f, pointPaint)
        canvas.drawText(startLabel, startX + 12f, startY - 12f, textPaint)

        val end = points.last()
        val endX = originX + (end.first * scale).toFloat()
        val endY = originY - (end.second * scale).toFloat()
        canvas.drawCircle(endX, endY, 12f, pointPaint)
        canvas.drawText(endLabel, endX + 12f, endY - 12f, textPaint)
    }

    private fun drawGpsEndpoint(
        canvas: Canvas,
        points: List<Pair<Double, Double>>,
        originX: Float,
        originY: Float,
        scale: Float
    ) {
        if (points.isEmpty()) return
        val gpsEnd = points.last()
        val gpsEndX = originX + (gpsEnd.first * scale).toFloat()
        val gpsEndY = originY - (gpsEnd.second * scale).toFloat()
        canvas.drawCircle(gpsEndX, gpsEndY, 10f, gpsTrackPaint)
        canvas.drawText("GPS", gpsEndX + 12f, gpsEndY - 12f, textPaint)
    }

    private fun drawScaleLegend(
        canvas: Canvas,
        widthPx: Float,
        pixelsPerMeter: Float,
        gridStepMeter: Float
    ) {
        val barLengthPx = gridStepMeter * pixelsPerMeter
        if (barLengthPx <= 1e-3f) return

        val label = "1格 = ${formatDistance(gridStepMeter)} m"
        val textWidth = scaleLabelPaint.measureText(label)
        val contentWidth = maxOf(textWidth, barLengthPx) + 36f
        val panelHeight = 88f
        val left = widthPx - contentWidth - 20f
        val top = 20f
        val right = widthPx - 20f
        val bottom = top + panelHeight

        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, scalePanelPaint)

        val textX = left + 18f
        val textY = top + 32f
        canvas.drawText(label, textX, textY, scaleLabelPaint)

        val barLeft = left + 18f
        val barRight = barLeft + barLengthPx
        val barY = bottom - 24f
        canvas.drawLine(barLeft, barY, barRight, barY, scaleBarPaint)
        canvas.drawLine(barLeft, barY - 10f, barLeft, barY + 10f, scaleBarPaint)
        canvas.drawLine(barRight, barY - 10f, barRight, barY + 10f, scaleBarPaint)
    }

    private fun formatDistance(distanceMeter: Float): String {
        return if (distanceMeter >= 10f || distanceMeter % 1f == 0f) {
            distanceMeter.toInt().toString()
        } else {
            String.format("%.1f", distanceMeter)
        }
    }

    private fun chooseGridStepMeter(pixelsPerMeter: Float): Float {
        val candidates = floatArrayOf(0.5f, 1f, 2f, 5f, 10f, 20f, 50f)
        for (candidate in candidates) {
            if (candidate * pixelsPerMeter >= 70f) {
                return candidate
            }
        }
        return candidates.last()
    }

    private fun findSegmentAtProgress(
        points: List<Pair<Double, Double>>,
        fraction: Double
    ): Segment? {
        if (points.size < 2) return null

        val segments = mutableListOf<Segment>()
        var totalLength = 0.0

        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val dx = to.first - from.first
            val dy = to.second - from.second
            val length = kotlin.math.hypot(dx, dy)
            if (length <= 1e-6) continue
            segments.add(Segment(from, to, length))
            totalLength += length
        }

        if (segments.isEmpty()) return null
        val targetLength = totalLength * fraction.coerceIn(0.0, 1.0)

        var accumulated = 0.0
        for (segment in segments) {
            if (accumulated + segment.length >= targetLength) {
                return segment
            }
            accumulated += segment.length
        }

        return segments.last()
    }

    private data class Segment(
        val from: Pair<Double, Double>,
        val to: Pair<Double, Double>,
        val length: Double
    )

    companion object {
        private const val DEFAULT_BASE_SCALE = 45f
        private const val MIN_USER_SCALE = 0.5f
        private const val MAX_USER_SCALE = 8f
    }
}
