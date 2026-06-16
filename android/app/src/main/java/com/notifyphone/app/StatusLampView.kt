package com.notifyphone.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

class StatusLampView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var snapshot: LampSnapshot = LampSnapshot()
    private var pulse: Float = 1f
    private var scalePercent: Int = 100
    private var animator: ValueAnimator? = null

    fun setScalePercent(percent: Int) {
        scalePercent = percent.coerceIn(45, 145)
        requestLayout()
        invalidate()
    }

    fun update(snapshot: LampSnapshot) {
        if (this.snapshot.state != snapshot.state) {
            restartAnimator(snapshot.state)
        }
        this.snapshot = snapshot
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: 420
        setMeasuredDimension(availableWidth, availableWidth)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartAnimator(snapshot.state)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = scalePercent / 100f
        val maxOuterRadius = width.coerceAtMost(height) / 2f * 0.92f
        val safeBaseRadius = maxOuterRadius / (UiScale.MAX_SCALE_FACTOR * MAX_PULSE * HALO_SCALE)
        val radius = safeBaseRadius * scale * pulse
        paint.color = colorFor(snapshot.state)
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        paint.color = Color.argb(60, Color.red(paint.color), Color.green(paint.color), Color.blue(paint.color))
        canvas.drawCircle(width / 2f, height / 2f, radius * HALO_SCALE, paint)
    }

    private fun restartAnimator(state: LampState) {
        animator?.cancel()
        animator = null
        when (state) {
            LampState.RUNNING_GREEN -> {
                pulse = 1f
                invalidate()
            }
            LampState.ATTENTION_YELLOW -> startPulse(0.55f, 1f, 260L)
            LampState.IDLE_RED -> startPulse(0.72f, 1f, 1400L)
        }
    }

    private fun startPulse(from: Float, to: Float, duration: Long) {
        animator = ValueAnimator.ofFloat(from, to).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            this.duration = duration
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun colorFor(state: LampState): Int {
        return when (state) {
            LampState.RUNNING_GREEN -> Color.rgb(24, 180, 96)
            LampState.ATTENTION_YELLOW -> Color.rgb(245, 190, 36)
            LampState.IDLE_RED -> Color.rgb(220, 55, 55)
        }
    }

    private object UiScale {
        const val MAX_SCALE_FACTOR = 1.45f
    }

    companion object {
        private const val MAX_PULSE = 1f
        private const val HALO_SCALE = 1.18f
    }
}
