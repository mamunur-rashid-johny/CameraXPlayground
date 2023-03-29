package com.example.cameraxplayground.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.cameraxplayground.R

class RectangleOverlayView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    View(context, attrs, defStyleAttr) {

    private var srcPaint: Paint? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    fun ratio(widthFromActivity: Int, heightFromActivity: Int) {
        widthFromActivityRatio = widthFromActivity
        heightFromActivityRatio = heightFromActivity
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width
        val height = height

        val ratioConstraint = getWidth() / 36



        val widthRatio: Int = widthFromActivityRatio * ratioConstraint
        val heightRatio: Int = heightFromActivityRatio * ratioConstraint

        val leftX1 = getWidth() / 2 - widthRatio
        val topY1 = getHeight() / 2 - heightRatio
        val rightX2 = getWidth() / 2 + widthRatio
        val bottomY2 = getHeight() / 2 + heightRatio

        val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val layer = Canvas(background)

        val cameraBgColor = if (context.isDarkThemeOn()) R.color.charleston_green else R.color.base_black_40
        layer.drawColor(ContextCompat.getColor(context, cameraBgColor))
        val rectF = RectF(
            leftX1.toFloat(),
            topY1.toFloat(),
            rightX2.toFloat(),
            bottomY2.toFloat()
        )
        layer.drawRect(rectF, srcPaint!!)
        canvas.drawBitmap(background, 0f, 0f, null)
    }

    private fun init() {
        srcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        srcPaint!!.color = Color.WHITE
        srcPaint!!.style = Paint.Style.FILL
        srcPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    companion object {
        private var  widthFromActivityRatio: Int = 0
        private var heightFromActivityRatio: Int = 0
    }

    init {
        init()
    }

}