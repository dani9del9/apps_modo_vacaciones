package com.dani.modovacaciones;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Logo animado de la cabecera: la campanita.
 * - Al activar el modo vacaciones cierra los ojos y salen unas "z" que suben y se desvanecen.
 * - Al desactivarlo abre los ojos y se sacude como si sonara.
 * Se dibuja en un lienzo de 110 x 110 unidades que se escala al tamaño de la vista.
 */
public class BellView extends View {

    private static final int BG = 0xFF7ED6C1;
    private static final int BELL = 0xFFFFF6E5;
    private static final int EYE_OPEN = 0xFF17332E;
    private static final int EYE_CLOSED = 0xFF5CC0AB;
    private static final int ZZZ = 0xFFF2683A;

    private static final long SLEEP_MS = 2600;
    private static final long WAKE_MS = 900;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeOpenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeClosedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bellPath = new Path();
    private final Path tilePath = new Path();
    private final Path closedEyes = new Path();
    private final RectF rect = new RectF();

    private float eyeOpen = 1f;        // 0 = cerrados, 1 = abiertos
    private float shake = 0f;          // grados de giro de la campana
    private final float[] zProgress = {-1f, -1f, -1f}; // -1 = no se ve

    private boolean sleeping = false;
    private boolean initialized = false;
    private ValueAnimator anim;

    public BellView(Context context) {
        this(context, null);
    }

    public BellView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bgPaint.setColor(BG);
        bellPaint.setColor(BELL);
        eyeOpenPaint.setColor(EYE_OPEN);

        eyeClosedPaint.setColor(EYE_CLOSED);
        eyeClosedPaint.setStyle(Paint.Style.STROKE);
        eyeClosedPaint.setStrokeWidth(2.5f);
        eyeClosedPaint.setStrokeCap(Paint.Cap.ROUND);

        zPaint.setColor(ZZZ);
        zPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        bellPath.moveTo(55, 28);
        bellPath.cubicTo(42, 28, 36, 38, 36, 50);
        bellPath.lineTo(36, 64);
        bellPath.lineTo(30, 72);
        bellPath.lineTo(80, 72);
        bellPath.lineTo(74, 64);
        bellPath.lineTo(74, 50);
        bellPath.cubicTo(74, 38, 68, 28, 55, 28);
        bellPath.close();

        tilePath.addRoundRect(new RectF(0, 0, 110, 110), 26, 26, Path.Direction.CW);

        closedEyes.moveTo(44, 53);
        closedEyes.quadTo(48, 57, 52, 53);
        closedEyes.moveTo(58, 53);
        closedEyes.quadTo(62, 57, 66, 53);

        setContentDescription("Modo vacaciones");
    }

    /** Cambia el estado. Si ya estaba inicializada y animate es true, lo hace con animación. */
    public void setSleeping(boolean sleep, boolean animate) {
        if (initialized && sleep == sleeping) return;
        boolean wasInitialized = initialized;
        initialized = true;
        sleeping = sleep;
        if (anim != null) anim.cancel();

        if (!wasInitialized || !animate) {
            eyeOpen = sleep ? 0f : 1f;
            shake = 0f;
            clearZ();
            invalidate();
            return;
        }
        if (sleep) animateSleep();
        else animateWake();
    }

    private void clearZ() {
        for (int i = 0; i < zProgress.length; i++) zProgress[i] = -1f;
    }

    private void animateSleep() {
        shake = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SLEEP_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction() * SLEEP_MS;
            eyeOpen = clamp(1f - t / 450f);
            for (int i = 0; i < zProgress.length; i++) {
                float p = (t - 400f - i * 380f) / 1400f;
                zProgress[i] = (p < 0f || p > 1f) ? -1f : p;
            }
            invalidate();
        });
        anim.start();
    }

    private void animateWake() {
        clearZ();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(WAKE_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();
            float t = f * WAKE_MS;
            eyeOpen = clamp(t / 250f);
            shake = (float) (Math.sin(f * Math.PI * 6) * 16 * (1f - f));
            invalidate();
        });
        anim.start();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float s = size / 110f;

        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(s, s);
        canvas.clipPath(tilePath);
        canvas.drawRect(0, 0, 110, 110, bgPaint);

        // Campana (gira desde arriba cuando "suena")
        canvas.save();
        canvas.rotate(shake, 55, 24);
        canvas.drawPath(bellPath, bellPaint);
        canvas.drawCircle(55, 79, 6, bellPaint);
        if (eyeOpen > 0.12f) {
            float ry = 3.4f * eyeOpen;
            rect.set(45, 52 - ry, 51, 52 + ry);
            canvas.drawOval(rect, eyeOpenPaint);
            rect.set(59, 52 - ry, 65, 52 + ry);
            canvas.drawOval(rect, eyeOpenPaint);
        } else {
            canvas.drawPath(closedEyes, eyeClosedPaint);
        }
        canvas.restore();

        // Zzz que suben y se desvanecen
        for (int i = 0; i < zProgress.length; i++) {
            float p = zProgress[i];
            if (p < 0f) continue;
            float alpha = p < 0.2f ? p / 0.2f : 1f - (p - 0.2f) / 0.8f;
            zPaint.setAlpha((int) (255 * clamp(alpha)));
            zPaint.setTextSize(10f + i * 3f + p * 5f);
            float x = 70f + i * 6f + p * 14f;
            float y = 42f - i * 8f - p * 24f;
            canvas.drawText("z", x, y, zPaint);
        }
        canvas.restore();
    }
}
