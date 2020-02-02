package com.mmnuradityo.imagezoomable.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.OverScroller
import android.widget.Scroller

/**
 * Source Code from :
 * pookie13/ZoomImage
 *
 * github :
 * pookie13
 *
 */
@Suppress("ANNOTATION_TARGETS_NON_EXISTENT_ACCESSOR", "NAME_SHADOWING")
@SuppressLint("AppCompatCustomView")
class ImageZoomable : ImageView {

    companion object {
        private val SUPER_MIN_MULTIPLIER = .25f
        private val SUPER_MAX_MULTIPLIER = 5f
    }

    private var normalizedScale = 0f
    private var setMatrix: Matrix? = null
    private var prevMatrix: Matrix? = null

    private enum class State {
        NONE, DRAG, ZOOM, FLING, ANIMATE_ZOOM
    }

    private var state: State? = null
    private var minScale = 0f
    private var maxScale = 0f
    private var superMinScale = 0f
    private var superMaxScale = 0f
    private var m: FloatArray? = null
    private var setContext: Context? = null
    private var fling: Fling? = null
    private var mScaleType: ScaleType? = null
    private var imageRenderedAtLeastOnce = false
    private var onDrawReady = false
    private var delayedZoomVariables: ZoomVariables? = null
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var prevViewWidth: Int = 0
    private var prevViewHeight: Int = 0
    private var matchViewWidth = 0f
    private var matchViewHeight = 0f
    private var prevMatchViewWidth = 0f
    private var prevMatchViewHeight = 0f
    private var mScaleDetector: ScaleGestureDetector? = null
    private var mGestureDetector: GestureDetector? = null
    private var doubleTapListener: OnDoubleTapListener? = null
    private var userTouchListener: OnTouchListener? = null
    private var touchImageViewListener: OnTouchImageViewListener? = null

    constructor(context: Context?) : super(context) {
        sharedConstructing(context!!)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?
    ) : super(context, attrs) {
        sharedConstructing(context!!)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle) {
        sharedConstructing(context!!)
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun sharedConstructing(context: Context) {
        super.setClickable(true)
        setContext = context
        mScaleDetector =
            ScaleGestureDetector(context, ScaleListener())
        mGestureDetector = GestureDetector(context, GestureListener())
        setMatrix = Matrix()
        prevMatrix = Matrix()
        m = FloatArray(9)
        normalizedScale = 1f
        if (mScaleType == null) {
            mScaleType = ScaleType.FIT_CENTER
        }
        minScale = 1f
        maxScale = 5f
        superMinScale = SUPER_MIN_MULTIPLIER * minScale
        superMaxScale = SUPER_MAX_MULTIPLIER * maxScale
        imageMatrix = setMatrix
        setScaleType(ScaleType.MATRIX)
        setState(State.NONE)
        onDrawReady = false
        super.setOnTouchListener(PrivateOnTouchListener())
    }

    override fun setOnTouchListener(l: OnTouchListener?) {
        userTouchListener = l
    }

    fun setOnTouchImageViewListener(l: OnTouchImageViewListener) {
        touchImageViewListener = l
    }

    fun setOnDoubleTapListener(l: OnDoubleTapListener) {
        doubleTapListener = l
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        savePreviousImageValues()
        fitImageToView()
    }

    override fun setScaleType(type: ScaleType) {
        if (type == ScaleType.FIT_START || type == ScaleType.FIT_END) {
            throw UnsupportedOperationException("TouchImageView does not support FIT_START or FIT_END")
        }
        if (type == ScaleType.MATRIX) {
            super.setScaleType(ScaleType.MATRIX)
        } else {
            mScaleType = type
            if (onDrawReady) {
                setZoom(this)
            }
        }
    }


    override fun getScaleType(): ScaleType? {
        return mScaleType
    }

    fun isZoomed(): Boolean {
        return normalizedScale != 1f
    }

    fun getZoomedRect(): RectF? {
        if (mScaleType == ScaleType.FIT_XY) {
            throw java.lang.UnsupportedOperationException("getZoomedRect() not supported with FIT_XY")
        }
        val topLeft: PointF = transformCoordTouchToBitmap(0f, 0f, true)!!
        val bottomRight: PointF =
            transformCoordTouchToBitmap(viewWidth.toFloat(), viewHeight.toFloat(), true)!!
        val w = drawable.intrinsicWidth.toFloat()
        val h = drawable.intrinsicHeight.toFloat()
        return RectF(topLeft.x / w, topLeft.y / h, bottomRight.x / w, bottomRight.y / h)
    }

    private fun savePreviousImageValues() {
        if (setMatrix != null && viewHeight != 0 && viewWidth != 0) {
            setMatrix!!.getValues(m)
            prevMatrix!!.setValues(m)
            prevMatchViewHeight = matchViewHeight
            prevMatchViewWidth = matchViewWidth
            prevViewHeight = viewHeight
            prevViewWidth = viewWidth
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("instanceState", super.onSaveInstanceState())
        bundle.putFloat("saveScale", normalizedScale)
        bundle.putFloat("matchViewHeight", matchViewHeight)
        bundle.putFloat("matchViewWidth", matchViewWidth)
        bundle.putInt("viewWidth", viewWidth)
        bundle.putInt("viewHeight", viewHeight)
        setMatrix!!.getValues(m)
        bundle.putFloatArray("matrix", m)
        bundle.putBoolean("imageRendered", imageRenderedAtLeastOnce)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val bundle = state
            normalizedScale = bundle.getFloat("saveScale")
            m = bundle.getFloatArray("matrix")
            prevMatrix!!.setValues(m)
            prevMatchViewHeight = bundle.getFloat("matchViewHeight")
            prevMatchViewWidth = bundle.getFloat("matchViewWidth")
            prevViewHeight = bundle.getInt("viewHeight")
            prevViewWidth = bundle.getInt("viewWidth")
            imageRenderedAtLeastOnce = bundle.getBoolean("imageRendered")
            super.onRestoreInstanceState(bundle.getParcelable("instanceState"))
            return
        }
        super.onRestoreInstanceState(state)
    }

    override fun onDraw(canvas: Canvas?) {
        onDrawReady = true
        imageRenderedAtLeastOnce = true
        if (delayedZoomVariables != null) {
            setZoom(
                delayedZoomVariables!!.scale,
                delayedZoomVariables!!.focusX,
                delayedZoomVariables!!.focusY,
                delayedZoomVariables!!.scaleType
            )
            delayedZoomVariables = null
        }
        super.onDraw(canvas)
    }


    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        savePreviousImageValues()
    }

    fun getMaxZoom(): Float {
        return maxScale
    }

    fun setMaxZoom(max: Float) {
        maxScale = max
        superMaxScale = SUPER_MAX_MULTIPLIER * maxScale
    }

    fun getMinZoom(): Float {
        return minScale
    }

    fun getCurrentZoom(): Float {
        return normalizedScale
    }

    fun setMinZoom(min: Float) {
        minScale = min
        superMinScale = SUPER_MIN_MULTIPLIER * minScale
    }

    fun resetZoom() {
        normalizedScale = 1f
        fitImageToView()
    }

    fun setZoom(scale: Float) {
        setZoom(scale, 0.5f, 0.5f)
    }

    fun setZoom(scale: Float, focusX: Float, focusY: Float) {
        setZoom(scale, focusX, focusY, mScaleType!!)
    }

    fun setZoom(
        scale: Float,
        focusX: Float,
        focusY: Float,
        scaleType: ScaleType
    ) {
        if (!onDrawReady) {
            delayedZoomVariables = ZoomVariables(scale, focusX, focusY, scaleType)
            return
        }
        if (scaleType != mScaleType) {
            setScaleType(scaleType)
        }
        resetZoom()
        scaleImage(scale.toDouble(), viewWidth / 2.toFloat(), viewHeight / 2.toFloat(), true)
        setMatrix!!.getValues(m)
        m!![Matrix.MTRANS_X] = -(focusX * getImageWidth() - viewWidth * 0.5f)
        m!![Matrix.MTRANS_Y] =
            -(focusY * getImageHeight() - viewHeight * 0.5f)
        setMatrix!!.setValues(m)
        fixTrans()
        imageMatrix = setMatrix
    }

    fun setZoom(img: ImageZoomable) {
        val center = img.getScrollPosition()
        setZoom(img.getScrollPosition().toString().toFloat(), center!!.x, center.y, img.scaleType!!)
    }

    fun getScrollPosition(): PointF? {
        val drawable = drawable ?: return null
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val point: PointF =
            transformCoordTouchToBitmap(viewWidth / 2.toFloat(), viewHeight / 2.toFloat(), true)!!
        point.x /= drawableWidth.toFloat()
        point.y /= drawableHeight.toFloat()
        return point
    }

    fun setScrollPosition(focusX: Float, focusY: Float) {
        setZoom(normalizedScale, focusX, focusY)
    }

    private fun fixTrans() {
        setMatrix!!.getValues(m)
        val transX = m!![Matrix.MTRANS_X]
        val transY = m!![Matrix.MTRANS_Y]
        val fixTransX: Float = getFixTrans(transX, viewWidth.toFloat(), getImageWidth())
        val fixTransY: Float = getFixTrans(transY, viewHeight.toFloat(), getImageHeight())
        if (fixTransX != 0f || fixTransY != 0f) {
            setMatrix!!.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun fixScaleTrans() {
        fixTrans()
        setMatrix!!.getValues(m)
        if (getImageWidth() < viewWidth) {
            m!![Matrix.MTRANS_X] = (viewWidth - getImageWidth()) / 2
        }
        if (getImageHeight() < viewHeight) {
            m!![Matrix.MTRANS_Y] = (viewHeight - getImageHeight()) / 2
        }
        setMatrix!!.setValues(m)
    }

    private fun getFixTrans(
        trans: Float,
        viewSize: Float,
        contentSize: Float
    ): Float {
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return -trans + minTrans
        return if (trans > maxTrans) -trans + maxTrans else 0f
    }

    private fun getFixDragTrans(
        delta: Float,
        viewSize: Float,
        contentSize: Float
    ): Float {
        return if (contentSize <= viewSize) {
            0f
        } else delta
    }

    private fun getImageWidth(): Float {
        return matchViewWidth * normalizedScale
    }

    private fun getImageHeight(): Float {
        return matchViewHeight * normalizedScale
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val drawable = drawable
        if (drawable == null || drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) {
            setMeasuredDimension(0, 0)
            return
        }
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        viewWidth = setViewSize(widthMode, widthSize, drawableWidth)
        viewHeight = setViewSize(heightMode, heightSize, drawableHeight)

        setMeasuredDimension(viewWidth, viewHeight)

        fitImageToView()
    }

    private fun fitImageToView() {
        val drawable = drawable
        if (drawable == null || drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) {
            return
        }
        if (setMatrix == null || prevMatrix == null) {
            return
        }
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        var scaleX = viewWidth.toFloat() / drawableWidth
        var scaleY = viewHeight.toFloat() / drawableHeight
        when (mScaleType) {
            ScaleType.CENTER -> {
                scaleY = 1f
                scaleX = scaleY
            }
            ScaleType.CENTER_CROP -> {
                scaleY = Math.max(scaleX, scaleY)
                scaleX = scaleY
            }
            ScaleType.CENTER_INSIDE -> {
                run {
                    scaleY = Math.min(1f, Math.min(scaleX, scaleY))
                    scaleX = scaleY
                }
                run {
                    scaleY = Math.min(scaleX, scaleY)
                    scaleX = scaleY
                }
            }
            ScaleType.FIT_CENTER -> {
                scaleY = Math.min(scaleX, scaleY)
                scaleX = scaleY
            }
            ScaleType.FIT_XY -> {
            }
            else -> throw java.lang.UnsupportedOperationException("TouchImageView does not support FIT_START or FIT_END")
        }

        val redundantXSpace = viewWidth - scaleX * drawableWidth
        val redundantYSpace = viewHeight - scaleY * drawableHeight
        matchViewWidth = viewWidth - redundantXSpace
        matchViewHeight = viewHeight - redundantYSpace
        if (!isZoomed() && !imageRenderedAtLeastOnce) { //

            setMatrix!!.setScale(scaleX, scaleY)
            setMatrix!!.postTranslate(redundantXSpace / 2, redundantYSpace / 2)
            normalizedScale = 1f
        } else {
            if (prevMatchViewWidth == 0f || prevMatchViewHeight == 0f) {
                savePreviousImageValues()
            }
            prevMatrix!!.getValues(m)

            m!![Matrix.MSCALE_X] =
                matchViewWidth / drawableWidth * normalizedScale
            m!![Matrix.MSCALE_Y] =
                matchViewHeight / drawableHeight * normalizedScale

            val transX = m!![Matrix.MTRANS_X]
            val transY = m!![Matrix.MTRANS_Y]

            val prevActualWidth = prevMatchViewWidth * normalizedScale
            val actualWidth = getImageWidth()
            translateMatrixAfterRotate(
                Matrix.MTRANS_X,
                transX,
                prevActualWidth,
                actualWidth,
                prevViewWidth,
                viewWidth,
                drawableWidth
            )

            val prevActualHeight = prevMatchViewHeight * normalizedScale
            val actualHeight = getImageHeight()
            translateMatrixAfterRotate(
                Matrix.MTRANS_Y,
                transY,
                prevActualHeight,
                actualHeight,
                prevViewHeight,
                viewHeight,
                drawableHeight
            )

            setMatrix!!.setValues(m)
        }
        fixTrans()
        imageMatrix = setMatrix
    }

    private fun setViewSize(mode: Int, size: Int, drawableWidth: Int): Int {
        return when (mode) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> Math.min(drawableWidth, size)
            MeasureSpec.UNSPECIFIED -> drawableWidth
            else -> size
        }
    }

    private fun translateMatrixAfterRotate(
        axis: Int,
        trans: Float,
        prevImageSize: Float,
        imageSize: Float,
        prevViewSize: Int,
        viewSize: Int,
        drawableSize: Int
    ) {
        when {
            imageSize < viewSize -> {
                m!![axis] =
                    (viewSize - drawableSize * m!![Matrix.MSCALE_X]) * 0.5f
            }
            trans > 0 -> {
                m!![axis] = -((imageSize - viewSize) * 0.5f)
            }
            else -> {
                val percentage =
                    (Math.abs(trans) + 0.5f * prevViewSize) / prevImageSize
                m!![axis] = -(percentage * imageSize - viewSize * 0.5f)
            }
        }
    }

    private fun setState(state: State) {
        this.state = state
    }

    fun canScrollHorizontallyFroyo(direction: Int): Boolean {
        return canScrollHorizontally(direction)
    }


    override fun canScrollHorizontally(direction: Int): Boolean {
        setMatrix!!.getValues(m)
        val x = m!![Matrix.MTRANS_X]
        if (getImageWidth() < viewWidth) {
            return false
        } else if (x >= -1 && direction < 0) {
            return false
        } else if (Math.abs(x) + viewWidth + 1 >= getImageWidth() && direction > 0) {
            return false
        }
        return true
    }

    inner class GestureListener : SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return if (doubleTapListener != null) {
                doubleTapListener!!.onSingleTapConfirmed(e)
            } else performClick()
        }

        override fun onLongPress(e: MotionEvent) {
            performLongClick()
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (fling != null) {
                fling!!.cancelFling()
            }
            fling = Fling(velocityX.toInt(), velocityY.toInt())
            compatPostOnAnimation(fling!!)
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            var consumed = false
            if (doubleTapListener != null) {
                consumed = doubleTapListener!!.onDoubleTap(e)
            }
            if (state == State.NONE) {
                val targetZoom: Float =
                    if (normalizedScale == minScale) maxScale else minScale
                val doubleTap = DoubleTapZoom(targetZoom, e.x, e.y, false)
                compatPostOnAnimation(doubleTap)
                consumed = true
            }
            return consumed
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            return if (doubleTapListener != null) {
                doubleTapListener!!.onDoubleTapEvent(e)
            } else false
        }
    }

    interface OnTouchImageViewListener {
        fun onMove()
    }

    inner class PrivateOnTouchListener : OnTouchListener {

        private val last = PointF()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            mScaleDetector!!.onTouchEvent(event)
            mGestureDetector!!.onTouchEvent(event)
            val curr = PointF(event.x, event.y)
            if (state == State.NONE || state == State.DRAG || state == State.FLING) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        last.set(curr)
                        if (fling != null) fling!!.cancelFling()
                        setState(State.DRAG)
                    }
                    MotionEvent.ACTION_MOVE -> if (state == State.DRAG) {
                        val deltaX = curr.x - last.x
                        val deltaY = curr.y - last.y
                        val fixTransX: Float =
                            getFixDragTrans(deltaX, viewWidth.toFloat(), getImageWidth())
                        val fixTransY: Float =
                            getFixDragTrans(deltaY, viewHeight.toFloat(), getImageHeight())
                        setMatrix!!.postTranslate(fixTransX, fixTransY)
                        fixTrans()
                        last[curr.x] = curr.y
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> setState(State.NONE)
                }
            }
            imageMatrix = setMatrix
            if (userTouchListener != null) {
                userTouchListener!!.onTouch(v, event)
            }
            if (touchImageViewListener != null) {
                touchImageViewListener!!.onMove()
            }
            return true
        }
    }

    inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            setState(State.ZOOM)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleImage(
                detector.scaleFactor.toDouble(),
                detector.focusX,
                detector.focusY,
                true
            )

            if (touchImageViewListener != null) {
                touchImageViewListener!!.onMove()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            setState(State.NONE)
            var animateToZoomBoundary = false
            var targetZoom: Float = normalizedScale
            if (normalizedScale > maxScale) {
                targetZoom = maxScale
                animateToZoomBoundary = true
            } else if (normalizedScale < minScale) {
                targetZoom = minScale
                animateToZoomBoundary = true
            }
            if (animateToZoomBoundary) {
                val doubleTap =
                    DoubleTapZoom(
                        targetZoom,
                        (viewWidth / 2).toFloat(),
                        (viewHeight / 2).toFloat(),
                        true
                    )
                compatPostOnAnimation(doubleTap)
            }
        }
    }

    private fun scaleImage(
        deltaScale: Double,
        focusX: Float,
        focusY: Float,
        stretchImageToSuper: Boolean
    ) {
        var deltaScale = deltaScale
        val lowerScale: Float
        val upperScale: Float
        if (stretchImageToSuper) {
            lowerScale = superMinScale
            upperScale = superMaxScale
        } else {
            lowerScale = minScale
            upperScale = maxScale
        }
        val origScale = normalizedScale
        normalizedScale *= deltaScale.toFloat()
        if (normalizedScale > upperScale) {
            normalizedScale = upperScale
            deltaScale = upperScale / origScale.toDouble()
        } else if (normalizedScale < lowerScale) {
            normalizedScale = lowerScale
            deltaScale = lowerScale / origScale.toDouble()
        }
        setMatrix!!.postScale(deltaScale.toFloat(), deltaScale.toFloat(), focusX, focusY)
        fixScaleTrans()
    }

    inner class DoubleTapZoom(
        targetZoom: Float,
        focusX: Float,
        focusY: Float,
        stretchImageToSuper: Boolean
    ) :
        Runnable {
        private val startTime: Long
        private val startZoom: Float
        private val targetZoom: Float
        private val bitmapX: Float
        private val bitmapY: Float
        private val stretchImageToSuper: Boolean
        private val interpolator =
            AccelerateDecelerateInterpolator()
        private val startTouch: PointF
        private val endTouch: PointF
        override fun run() {
            val t = interpolate()
            val deltaScale = calculateDeltaScale(t)
            scaleImage(deltaScale, bitmapX, bitmapY, stretchImageToSuper)
            translateImageToCenterTouchPosition(t)
            fixScaleTrans()
            imageMatrix = setMatrix

            if (touchImageViewListener != null) {
                touchImageViewListener!!.onMove()
            }
            if (t < 1f) {
                compatPostOnAnimation(this)
            } else {
                setState(State.NONE)
            }
        }

        private fun translateImageToCenterTouchPosition(t: Float) {
            val targetX = startTouch.x + t * (endTouch.x - startTouch.x)
            val targetY = startTouch.y + t * (endTouch.y - startTouch.y)
            val curr: PointF? = transformCoordBitmapToTouch(bitmapX, bitmapY)
            setMatrix!!.postTranslate(targetX - curr!!.x, targetY - curr.y)
        }

        private fun interpolate(): Float {
            val currTime = System.currentTimeMillis()
            var elapsed = (currTime - startTime) / ZOOM_TIME
            elapsed = Math.min(1f, elapsed)
            return interpolator.getInterpolation(elapsed)
        }

        private fun calculateDeltaScale(t: Float): Double {
            val zoom = startZoom + t * (targetZoom - startZoom).toDouble()
            return zoom / normalizedScale
        }

        private val ZOOM_TIME = 500f

        init {
            setState(State.ANIMATE_ZOOM)
            startTime = System.currentTimeMillis()
            startZoom = normalizedScale
            this.targetZoom = targetZoom
            this.stretchImageToSuper = stretchImageToSuper
            val bitmapPoint: PointF? = transformCoordTouchToBitmap(focusX, focusY, false)
            bitmapX = bitmapPoint!!.x
            bitmapY = bitmapPoint.y
            startTouch = transformCoordBitmapToTouch(bitmapX, bitmapY)!!
            endTouch = PointF((viewWidth / 2).toFloat(), (viewHeight / 2).toFloat())
        }
    }

    private fun transformCoordTouchToBitmap(
        x: Float,
        y: Float,
        clipToBitmap: Boolean
    ): PointF? {
        setMatrix!!.getValues(m)
        val origW = drawable.intrinsicWidth.toFloat()
        val origH = drawable.intrinsicHeight.toFloat()
        val transX = m!![Matrix.MTRANS_X]
        val transY = m!![Matrix.MTRANS_Y]
        var finalX = (x - transX) * origW / getImageWidth()
        var finalY = (y - transY) * origH / getImageHeight()
        if (clipToBitmap) {
            finalX = Math.min(Math.max(finalX, 0f), origW)
            finalY = Math.min(Math.max(finalY, 0f), origH)
        }
        return PointF(finalX, finalY)
    }

    private fun transformCoordBitmapToTouch(bx: Float, by: Float): PointF? {
        setMatrix!!.getValues(m)
        val origW = drawable.intrinsicWidth.toFloat()
        val origH = drawable.intrinsicHeight.toFloat()
        val px = bx / origW
        val py = by / origH
        val finalX = m!![Matrix.MTRANS_X] + getImageWidth() * px
        val finalY = m!![Matrix.MTRANS_Y] + getImageHeight() * py
        return PointF(finalX, finalY)
    }

    inner class Fling constructor(velocityX: Int, velocityY: Int) : Runnable {
        private var scroller: CompatScroller?
        var currX: Int
        var currY: Int
        fun cancelFling() {
            if (scroller != null) {
                setState(State.NONE)
                scroller!!.forceFinished(true)
            }
        }

        override fun run() {

            if (touchImageViewListener != null) {
                touchImageViewListener!!.onMove()
            }
            if (scroller!!.isFinished) {
                scroller = null
                return
            }
            if (scroller!!.computeScrollOffset()) {
                val newX: Int = scroller!!.currX
                val newY: Int = scroller!!.currY
                val transX = newX - currX
                val transY = newY - currY
                currX = newX
                currY = newY
                setMatrix!!.postTranslate(transX.toFloat(), transY.toFloat())
                fixTrans()
                imageMatrix = setMatrix
                compatPostOnAnimation(this)
            }
        }

        init {
            setState(State.FLING)
            scroller = CompatScroller(setContext)
            setMatrix!!.getValues(m)
            val startX = m!![Matrix.MTRANS_X].toInt()
            val startY = m!![Matrix.MTRANS_Y].toInt()
            val minX: Int
            val maxX: Int
            val minY: Int
            val maxY: Int
            if (getImageWidth() > viewWidth) {
                minX = viewWidth - getImageWidth().toInt()
                maxX = 0
            } else {
                maxX = startX
                minX = maxX
            }
            if (getImageHeight() > viewHeight) {
                minY = viewHeight - getImageHeight().toInt()
                maxY = 0
            } else {
                maxY = startY
                minY = maxY
            }
            scroller!!.fling(
                startX, startY, velocityX, velocityY, minX,
                maxX, minY, maxY
            )
            currX = startX
            currY = startY
        }
    }

    private class CompatScroller(context: Context?) {
        var scroller: Scroller? = null
        var overScroller: OverScroller? = null
        fun fling(
            startX: Int,
            startY: Int,
            velocityX: Int,
            velocityY: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int
        ) {
            overScroller!!.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
        }

        fun forceFinished(finished: Boolean) {
            overScroller!!.forceFinished(finished)
        }

        val isFinished: Boolean
            get() = overScroller!!.isFinished

        fun computeScrollOffset(): Boolean {
            return overScroller!!.computeScrollOffset()
        }

        val currX: Int
            get() = overScroller!!.currX

        val currY: Int
            get() = overScroller!!.currY

        init {
            overScroller = OverScroller(context)
        }
    }

    private fun compatPostOnAnimation(runnable: Runnable) {
        postOnAnimation(runnable)

    }

    private class ZoomVariables(
        var scale: Float,
        var focusX: Float,
        var focusY: Float,
        var scaleType: ScaleType
    )

}
