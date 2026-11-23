package com.dani.modovacaciones;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
 * - Al activar el modo vacaciones cierra los ojos y se queda "roncando" en bucle:
 *   la campana se hincha y deshincha como si respirase y van saliendo "z" que suben y se desvanecen.
 * - Al desactivarlo abre los ojos y se sacude como si sonara.
 * Se dibuja en un lienzo de 110 x 110 unidades que se escala al tamaño de la vista.
 */
public class BellView extends View {

    private static final int BG = 0xFF7ED6C1;
    private static final int BELL = 0xFFFFF6E5;
    private static final int EYE_OPEN = 0xFF17332E;
    private static final int EYE_CLOSED = 0xFF5CC0AB;
    private static final int ZZZ = 0xFFF2683A;

    private static final long CLOSE_MS = 450;
    private static final long SNORE_MS = 3200;  // una respiración completa
    private static final long WAKE_MS = 900;
    private static final int Z_COUNT = 3;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeOpenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeClosedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bellPath = new Path();
    private final Path tilePath = new Path();
    private final Path closedEyes = new Path();
    private final RectF rect = new RectF();

    private float eyeOpen = 1f;     // 0 = cerrados, 1 = abiertos
    private float shake = 0f;       // grados de giro (al despertar)
    private float breath = 0f;      // 0 = deshinchada, 1 = hinchada
    private float zFade = 0f;       // aparición suave de las z al empezar a roncar
    private float snorePhase = -1f; // fase del ronquido 0..1 (-1 = sin ronquido)

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

    /** Cambia el estado. La primera vez (o con animate = false) se pinta directamente sin transición. */
    public void setSleeping(boolean sleep, boolean animate) {
        if (initialized && sleep == sleeping) return;
        boolean wasInitialized = initialized;
        initialized = true;
        sleeping = sleep;
        stopAnim();

        if (!wasInitialized || !animate) {
            shake = 0f;
            if (sleep) {
                eyeOpen = 0f;
                startSnoring(false);
            } else {
                eyeOpen = 1f;
                breath = 0f;
                snorePhase = -1f;
            }
            invalidate();
            return;
        }
        if (sleep) animateFallAsleep();
        else animateWake();
    }

    private void stopAnim() {
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
            anim = null;
        }
    }

    // ---------------------------------------------------------------- animaciones

    private void animateFallAsleep() {
        shake = 0f;
        snorePhase = -1f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(CLOSE_MS);
        anim.addUpdateListener(a -> {
            eyeOpen = 1f - a.getAnimatedFraction();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (sleeping) startSnoring(true);
            }
        });
        anim.start();
    }

    /** Bucle infinito: respira y suelta z. */
    private void startSnoring(boolean fadeIn) {
        stopAnim();
        eyeOpen = 0f;
        zFade = fadeIn ? 0f : 1f;
        final long start = System.currentTimeMillis();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SNORE_MS);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();
            snorePhase = f;
            // Inspira despacio (60 %) y espira más rápido (40 %), como un ronquido
            if (f < 0.6f) breath = smooth(f / 0.6f);
            else breath = 1f - smooth((f - 0.6f) / 0.4f);
            if (zFade < 1f) zFade = clamp((System.currentTimeMillis() - start) / 800f);
            invalidate();
        });
        anim.start();
    }

    private void animateWake() {
        snorePhase = -1f;
        final float startBreath = breath;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(WAKE_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();
            float t = f * WAKE_MS;
            eyeOpen = clamp(t / 250f);
            breath = startBreath * (1f - clamp(t / 200f));
            shake = (float) (Math.sin(f * Math.PI * 6) * 16 * (1f - f));
            invalidate();
        });
        anim.start();
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3f - 2f * x);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (anim == null) return;
        if (visibility == VISIBLE) anim.resume();
        else anim.pause(); // no gastar batería con la app en segundo plano
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (initialized && sleeping && anim == null) startSnoring(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- dibujo

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

        // Campana: gira al despertar y se hincha al respirar (desde la base)
        canvas.save();
        canvas.rotate(shake, 55, 24);
        float sx = 1f + 0.07f * breath;
        float sy = 1f + 0.04f * breath;
        canvas.scale(sx, sy, 55, 72);
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

        // Zzz en bucle: cada z recorre su camino escalonada respecto a las demás
        if (snorePhase >= 0f) {
            for (int i = 0; i < Z_COUNT; i++) {
                float p = snorePhase + (float) i / Z_COUNT;
                p -= (float) Math.floor(p);
                float alpha = p < 0.2f ? p / 0.2f : 1f - (p - 0.2f) / 0.8f;
                zPaint.setAlpha((int) (255 * clamp(alpha) * zFade));
                zPaint.setTextSize(9f + p * 9f);
                float x = 70f + p * 22f;
                float y = 44f - p * 34f;
                canvas.drawText("z", x, y, zPaint);
            }
        }
        canvas.restore();
    }
}
