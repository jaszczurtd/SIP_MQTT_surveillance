package com.jaszczurtd.sipclient;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;

import androidx.annotation.NonNull;

public final class ZoomableVideoTextureView extends TextureView
        implements TextureView.SurfaceTextureListener {

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    // Matrix
    private final Matrix baseMatrix = new Matrix();
    private final Matrix userMatrix = new Matrix();  // pinch + pan
    private final Matrix drawMatrix = new Matrix();  // user * base

    private int videoW = 0, videoH = 0;
    private int videoRotation = 0; // 0/90/180/270

    // Zoom / pan
    private float minScale = 1.0f;
    private final float maxScale = 5.0f;
    private float currentScale = 1.0f;
    private float transX = 0f, transY = 0f;

    private SurfaceTextureListener forwardedListener;

    public ZoomableVideoTextureView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setSurfaceTextureListener(this);

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector d) {
                float s = d.getScaleFactor();
                float next = clamp(currentScale * s, minScale, maxScale);
                scaleAround(d.getFocusX(), d.getFocusY(), next / currentScale);
                currentScale = next;
                applyMatrix();
                return true;
            }
        });

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) { resetUserTransform(); return true; }
            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                translate(-dx, -dy);
                applyMatrix();
                return true;
            }
        });
    }

    public void onVideoSizeOrRotationChanged(int w, int h, int rotationDeg) {
        this.videoW = w; this.videoH = h; this.videoRotation = (rotationDeg % 360 + 360) % 360;
        recomputeBaseMatrix();
        applyMatrix();
    }

    private void recomputeBaseMatrix() {
        baseMatrix.reset();
        if (videoW == 0 || videoH == 0 || getWidth() == 0 || getHeight() == 0) return;

        boolean swap = (videoRotation % 180) != 0;
        float srcW = swap ? videoH : videoW;
        float srcH = swap ? videoW : videoH;

        float viewW = getWidth(), viewH = getHeight();
        float scale = Math.min(viewW / srcW, viewH / srcH);
        float dx = (viewW - srcW * scale) * 0.5f;
        float dy = (viewH - srcH * scale) * 0.5f;

        baseMatrix.postRotate(videoRotation, 0, 0);
        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate(dx, dy);

        minScale = 1.0f;
        clampTranslation();
    }

    private void scaleAround(float px, float py, float factor) {
        userMatrix.postScale(factor, factor, px, py);
        currentScale *= factor;
        clampTranslation();
    }

    private void translate(float dx, float dy) {
        userMatrix.postTranslate(dx, dy);
        transX += dx; transY += dy;
        clampTranslation();
    }

    private void clampTranslation() {
        RectF content = new RectF(0, 0, getWidth(), getHeight());
        Matrix tmp = new Matrix();
        tmp.set(baseMatrix);
        tmp.postConcat(userMatrix);
        tmp.mapRect(content, new RectF(0, 0, getWidth(), getHeight()));
    }

    private void resetUserTransform() {
        userMatrix.reset();
        currentScale = 1.0f;
        transX = transY = 0f;
        applyMatrix();
    }

    private void applyMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(userMatrix);
        setTransform(drawMatrix);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        boolean a = scaleDetector.onTouchEvent(e);
        boolean b = gestureDetector.onTouchEvent(e);
        return a || b || super.onTouchEvent(e);
    }

    public void setForwardedSurfaceTextureListener(SurfaceTextureListener l) {
        this.forwardedListener = l;
    }

    // SurfaceTextureListener
    @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {
        if (forwardedListener != null) forwardedListener.onSurfaceTextureAvailable(s, w, h);
        // Pierwsze dopasowanie
        recomputeBaseMatrix();
        applyMatrix();
    }
    @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {
        if (forwardedListener != null) forwardedListener.onSurfaceTextureSizeChanged(s, w, h);
        recomputeBaseMatrix();
        applyMatrix();
    }
    @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) {
        if (forwardedListener != null) return forwardedListener.onSurfaceTextureDestroyed(s);
        return true;
    }
    @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {
        if (forwardedListener != null) forwardedListener.onSurfaceTextureUpdated(s);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    public void resetToFitCenter(boolean animate) {
        if (animate) {
            final float startScale = currentScale;
            final float startTx = transX, startTy = transY;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(180);
            va.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float s = startScale + (1f - startScale) * t;
                float tx = startTx * (1f - t);
                float ty = startTy * (1f - t);

                userMatrix.reset();
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                userMatrix.postScale(s, s, cx, cy);
                userMatrix.postTranslate(tx, ty);

                applyMatrix();
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    currentScale = 1f;
                    transX = transY = 0f;
                    userMatrix.reset();
                    applyMatrix();
                }
            });
            va.start();
        } else {
            currentScale = 1f;
            transX = transY = 0f;
            userMatrix.reset();
            applyMatrix();
        }
    }

}
