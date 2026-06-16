package com.example.basketballtrainer.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Size
import android.view.View
import com.example.basketballtrainer.model.DribbleHeight
import com.example.basketballtrainer.model.TrainingMode

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    @Volatile private var landmarks: FloatArray? = null
    @Volatile private var ballBox: RectF? = null
    @Volatile private var guidelineY: Float? = null
    @Volatile private var roi: RectF? = null
    @Volatile private var mode: TrainingMode = TrainingMode.Dribble(DribbleHeight.HIP)
    @Volatile private var isFrontCamera: Boolean = false

    private var sourceSize: Size = Size(0, 0)
    private val srcToView = Matrix()
    private val tmpRect = RectF()
    private val tmpPts = FloatArray(2)

    private val bonePaint = paint(color = 0xFFFF3B3B.toInt(), strokeWidth = 5f, style = Paint.Style.STROKE).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val jointPaint = paint(color = 0xFFFF3B3B.toInt(), style = Paint.Style.FILL)
    private val wristPaint = paint(color = 0xFFFF0000.toInt(), style = Paint.Style.FILL_AND_STROKE).apply {
        strokeWidth = 2f
    }
    private val ballPaint  = paint(color = 0xFFEF9F27.toInt(), strokeWidth = 4f, style = Paint.Style.STROKE)
    private val roiPaint   = paint(color = 0xFFEF9F27.toInt(), strokeWidth = 3f, style = Paint.Style.STROKE).apply {
        pathEffect = DashPathEffect(floatArrayOf(12f, 9f), 0f)
    }
    private val guidePaint = paint(color = 0xFFFAC775.toInt(), strokeWidth = 3f, style = Paint.Style.STROKE).apply {
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }
    private val labelPaint = paint(color = 0xFFFAC775.toInt(), textSize = 28f, style = Paint.Style.FILL).apply {
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(2f, 1f, 1f, 0xAA000000.toInt())
    }

    // ---------------- public API ----------------

    fun setSourceSize(sourceWidth: Int, sourceHeight: Int) {
        if (sourceWidth == sourceSize.width && sourceHeight == sourceSize.height) return
        sourceSize = Size(sourceWidth, sourceHeight)
        rebuildMatrix()
    }

    fun setMode(mode: TrainingMode) { this.mode = mode }

    fun setFrontCamera(front: Boolean) {
        if (isFrontCamera == front) return
        isFrontCamera = front
        rebuildMatrix()
    }

    fun pushFrame(
        normalizedLandmarks: FloatArray?,
        ballBoxPx: RectF?,
        guidelineYpx: Float?,
        roiPx: RectF?,
    ) {
        this.landmarks  = normalizedLandmarks
        this.ballBox    = ballBoxPx
        this.guidelineY = guidelineYpx
        this.roi        = roiPx
        postInvalidateOnAnimation()
    }

    // ---------------- lifecycle ----------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildMatrix()
    }

    private fun rebuildMatrix() {
        if (width == 0 || height == 0 || sourceSize.width == 0 || sourceSize.height == 0) return

        val sw = sourceSize.width.toFloat()
        val sh = sourceSize.height.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()

        // PreviewView FILL_CENTER 와 동일: 가로/세로 중 큰 쪽 기준으로 스케일
        val scaleX = vw / sw
        val scaleY = vh / sh
        val scale  = maxOf(scaleX, scaleY)

        // 중앙 정렬 오프셋
        val dx = (vw - sw * scale) / 2f
        val dy = (vh - sh * scale) / 2f

        srcToView.reset()
        srcToView.postScale(scale, scale)
        srcToView.postTranslate(dx, dy)

        // 전면 카메라 수평 반전
        if (isFrontCamera) {
            srcToView.postScale(-1f, 1f, vw / 2f, vh / 2f)
        }

        postInvalidateOnAnimation()
    }

    // ---------------- draw ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceSize.width == 0) return
        drawRoi(canvas)
        drawGuideline(canvas)
        drawSkeleton(canvas)
        drawBall(canvas)
    }

    private fun drawRoi(canvas: Canvas) {
        val r = roi ?: return
        if (mode !is TrainingMode.Shoot) return
        tmpRect.set(r)
        srcToView.mapRect(tmpRect)
        canvas.drawRoundRect(tmpRect, 6f, 6f, roiPaint)
        canvas.drawText("GOAL ROI", tmpRect.left, tmpRect.top - 8f, labelPaint)
    }

    private fun drawGuideline(canvas: Canvas) {
        val y = guidelineY ?: return
        if (mode !is TrainingMode.Dribble) return
        tmpPts[0] = 0f; tmpPts[1] = y
        srcToView.mapPoints(tmpPts)
        val ly = tmpPts[1]
        canvas.drawLine(20f, ly, width - 20f, ly, guidePaint)
        canvas.drawText(
            (mode as TrainingMode.Dribble).limit.koLabel + " 기준선",
            24f, ly - 10f, labelPaint
        )
    }

    private fun drawSkeleton(canvas: Canvas) {
        val lm = landmarks ?: return
        val sw = sourceSize.width.toFloat()
        val sh = sourceSize.height.toFloat()

        for ((a, b) in POSE_BONES) {
            val (ax, ay) = landmarkAt(lm, a, sw, sh) ?: continue
            val (bx, by) = landmarkAt(lm, b, sw, sh) ?: continue
            tmpPts[0] = ax; tmpPts[1] = ay; srcToView.mapPoints(tmpPts)
            val x0 = tmpPts[0]; val y0 = tmpPts[1]
            tmpPts[0] = bx; tmpPts[1] = by; srcToView.mapPoints(tmpPts)
            canvas.drawLine(x0, y0, tmpPts[0], tmpPts[1], bonePaint)
        }

        for (i in 0 until lm.size / 2) {
            val (px, py) = landmarkAt(lm, i, sw, sh) ?: continue
            tmpPts[0] = px; tmpPts[1] = py; srcToView.mapPoints(tmpPts)
            val isWrist = i == LEFT_WRIST || i == RIGHT_WRIST
            canvas.drawCircle(
                tmpPts[0], tmpPts[1],
                if (isWrist) 8f else 5f,
                if (isWrist) wristPaint else jointPaint
            )
        }
    }

    private fun drawBall(canvas: Canvas) {
        val b = ballBox ?: return
        tmpRect.set(b)
        srcToView.mapRect(tmpRect)
        canvas.drawRoundRect(tmpRect, 4f, 4f, ballPaint)
    }

    private fun landmarkAt(lm: FloatArray, idx: Int, sw: Float, sh: Float): Pair<Float, Float>? {
        val xi = idx * 2
        if (xi + 1 >= lm.size) return null
        val x = lm[xi]; val y = lm[xi + 1]
        if (x.isNaN() || y.isNaN()) return null
        return (x * sw) to (y * sh)
    }

    private fun paint(
        color: Int,
        strokeWidth: Float = 0f,
        style: Paint.Style = Paint.Style.FILL,
        textSize: Float = 0f,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = color
        it.style = style
        if (strokeWidth > 0f) it.strokeWidth = strokeWidth
        if (textSize > 0f) it.textSize = textSize
    }

    companion object {
        const val LEFT_SHOULDER  = 11; const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW     = 13; const val RIGHT_ELBOW    = 14
        const val LEFT_WRIST     = 15; const val RIGHT_WRIST    = 16
        const val LEFT_HIP       = 23; const val RIGHT_HIP      = 24
        const val LEFT_KNEE      = 25; const val RIGHT_KNEE     = 26
        const val LEFT_ANKLE     = 27; const val RIGHT_ANKLE    = 28

        private val POSE_BONES = listOf(
            LEFT_SHOULDER  to RIGHT_SHOULDER,
            LEFT_SHOULDER  to LEFT_ELBOW,    LEFT_ELBOW  to LEFT_WRIST,
            RIGHT_SHOULDER to RIGHT_ELBOW,   RIGHT_ELBOW to RIGHT_WRIST,
            LEFT_SHOULDER  to LEFT_HIP,      RIGHT_SHOULDER to RIGHT_HIP,
            LEFT_HIP       to RIGHT_HIP,
            LEFT_HIP       to LEFT_KNEE,     LEFT_KNEE  to LEFT_ANKLE,
            RIGHT_HIP      to RIGHT_KNEE,    RIGHT_KNEE to RIGHT_ANKLE,
        )
    }
}