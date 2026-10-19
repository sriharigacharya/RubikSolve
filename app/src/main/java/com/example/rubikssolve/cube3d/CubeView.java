package com.example.rubikssolve.cube3d;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Custom 3D Rubik's Cube View using Canvas 2D projection and painter's algorithm.
 *
 * Features:
 * - Free drag orbit (azimuth + elevation clamped at ±85°)
 * - Animated face turns with smooth AccelerateDecelerateInterpolator
 * - Move queue with speedup when taps pile up
 * - Clean sticker-on-black-plastic rendering without z-fighting
 * - Zero GC allocation in onDraw loop
 */
public class CubeView extends View {

    private static final float HALF_EXTENT = 0.5f;
    private static final float CAMERA_DIST = 6.0f;
    private static final float STICKER_SCALE = 0.86f;

    // Cube Model
    private CubeModel model;

    // Orbit angles in degrees
    private float azimuth = -35.0f;
    private float elevation = 25.0f;
    private float lastTouchX;
    private float lastTouchY;

    // Animation & Move Queue
    private final Deque<String> moveQueue = new ArrayDeque<>();
    private Move animMove = null;
    private float animProgress = 0f;
    private boolean isAnimating = false;
    private ValueAnimator currentAnimator = null;

    // Drawing resources (preallocated)
    private final Paint blackBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path drawPath = new Path();

    // Reusable Face projection data
    private static class FaceDraw {
        float depth;
        int stickerColor;
        final float[] p0 = new float[2];
        final float[] p1 = new float[2];
        final float[] p2 = new float[2];
        final float[] p3 = new float[2];
    }

    private final List<FaceDraw> facePool = new ArrayList<>(162);
    private final List<FaceDraw> visibleFaces = new ArrayList<>(162);
    private final Comparator<FaceDraw> depthComparator = (a, b) -> Float.compare(a.depth, b.depth);

    public CubeView(Context context) {
        super(context);
        init();
    }

    public CubeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CubeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        blackBodyPaint.setStyle(Paint.Style.FILL);
        blackBodyPaint.setColor(0xFF18181B); // Sleek dark cube body

        stickerPaint.setStyle(Paint.Style.FILL);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.0f);
        strokePaint.setColor(0x33000000);

        // Preallocate 162 faces (27 cubies * 6 faces)
        for (int i = 0; i < 162; i++) {
            facePool.add(new FaceDraw());
        }

        // Default to a solved cube if model is not set yet
        int[] defaultColors = {
            0xFFFFFFFF, // U (White)
            0xFFB71234, // R (Red)
            0xFF009B48, // F (Green)
            0xFFFFD500, // D (Yellow)
            0xFFFF5800, // L (Orange)
            0xFF0046AD  // B (Blue)
        };
        this.model = CubeModel.createSolved(defaultColors);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Set or reset the cube model state directly with no animation. */
    public void setCube(CubeModel m) {
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        moveQueue.clear();
        animMove = null;
        animProgress = 0f;
        isAnimating = false;
        this.model = m;
        invalidate();
    }

    /** Set cube from facelets 6x9 array. */
    public void setCubeColors(int[][] cubeColors) {
        setCube(new CubeModel(cubeColors));
    }

    /** Enqueue a move to be animated. */
    public void playMove(String moveStr) {
        if (moveStr == null || moveStr.trim().isEmpty()) return;
        moveQueue.add(moveStr.trim());
        if (!isAnimating) {
            startNextMove();
        }
    }

    /** Clear any pending animations. */
    public void cancelMoves() {
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        moveQueue.clear();
        animMove = null;
        animProgress = 0f;
        isAnimating = false;
        invalidate();
    }

    // -------------------------------------------------------------------------
    // Animation Driver
    // -------------------------------------------------------------------------

    private void startNextMove() {
        String nextMove = moveQueue.poll();
        if (nextMove == null) {
            isAnimating = false;
            animMove = null;
            animProgress = 0f;
            return;
        }

        isAnimating = true;
        animMove = Move.parse(nextMove);
        animProgress = 0f;

        // Speed up animation if backlog of taps exists
        long duration = moveQueue.isEmpty() ? 320 : 140;

        currentAnimator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator.setDuration(duration);
        currentAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        currentAnimator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (model != null) {
                    model.applyMove(nextMove); // Commit the move to integer state
                }
                animMove = null;
                animProgress = 0f;
                startNextMove();
            }
        });
        currentAnimator.start();
    }

    // -------------------------------------------------------------------------
    // Touch Handling (Free Orbit)
    // -------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                azimuth += dx * 0.45f;
                elevation += dy * 0.45f;
                elevation = Math.max(-85.0f, Math.min(85.0f, elevation));
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    // -------------------------------------------------------------------------
    // 3D Math & Projection
    // -------------------------------------------------------------------------

    private float[] toView(float[] p, float ca, float sa, float ce, float se) {
        float x1 = ca * p[0] + sa * p[2];
        float z1 = -sa * p[0] + ca * p[2];
        float y2 = ce * p[1] - se * z1;
        float z2 = se * p[1] + ce * z1;
        return new float[]{x1, y2, z2};
    }

    private float[] rotF(float[] v, int axis, float t) {
        float c = (float) Math.cos(t);
        float s = (float) Math.sin(t);
        switch (axis) {
            case 0:  return new float[]{v[0], c * v[1] - s * v[2], s * v[1] + c * v[2]};
            case 1:  return new float[]{c * v[0] + s * v[2], v[1], -s * v[0] + c * v[2]};
            default: return new float[]{c * v[0] - s * v[1], s * v[0] + c * v[1], v[2]};
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null) return;

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float cx = width / 2.0f;
        float cy = height / 2.0f;
        float unit = Math.min(width, height) / 6.0f;

        // View transformation trig
        float azRad = (float) Math.toRadians(azimuth);
        float elRad = (float) Math.toRadians(elevation);
        float ca = (float) Math.cos(azRad), sa = (float) Math.sin(azRad);
        float ce = (float) Math.cos(elRad), se = (float) Math.sin(elRad);

        // Animation angle
        float animAngleRad = 0f;
        if (animMove != null) {
            animAngleRad = (float) Math.toRadians(animMove.quarterTurns * 90.0f * animProgress);
        }

        visibleFaces.clear();
        int faceIndex = 0;

        List<CubeModel.Cubie> cubies = model.getCubies();
        for (CubeModel.Cubie cubie : cubies) {
            boolean active = (animMove != null && model.affects(animMove, cubie));

            float cX = cubie.pos[0];
            float cY = cubie.pos[1];
            float cZ = cubie.pos[2];

            // 6 faces per cubie
            for (int f = 0; f < 6; f++) {
                int[] n = CubeModel.NORMALS[f];
                float nX = n[0], nY = n[1], nZ = n[2];

                // Build 4 corners in 3D
                float[][] corners = buildCorners(f, cX, cY, cZ);

                // If active cubie, rotate corners and normal by animation rotation
                float rotNx = nX, rotNy = nY, rotNz = nZ;
                if (active) {
                    for (int i = 0; i < 4; i++) {
                        corners[i] = rotF(corners[i], animMove.axis, animAngleRad);
                    }
                    float[] rotN = rotF(new float[]{nX, nY, nZ}, animMove.axis, animAngleRad);
                    rotNx = rotN[0]; rotNy = rotN[1]; rotNz = rotN[2];
                }

                // Compute face center in view space
                float faceCenter3DX = cX + nX * HALF_EXTENT;
                float faceCenter3DY = cY + nY * HALF_EXTENT;
                float faceCenter3DZ = cZ + nZ * HALF_EXTENT;
                float[] fc = new float[]{faceCenter3DX, faceCenter3DY, faceCenter3DZ};
                if (active) {
                    fc = rotF(fc, animMove.axis, animAngleRad);
                }
                float[] viewCenter = toView(fc, ca, sa, ce, se);

                // Compute normal in view space
                float[] viewNormal = toView(new float[]{rotNx, rotNy, rotNz}, ca, sa, ce, se);

                // Backface culling: skip if facing away from camera at (0, 0, CAMERA_DIST)
                float toCamX = -viewCenter[0];
                float toCamY = -viewCenter[1];
                float toCamZ = CAMERA_DIST - viewCenter[2];
                float dot = viewNormal[0] * toCamX + viewNormal[1] * toCamY + viewNormal[2] * toCamZ;
                if (dot <= 0.001f) {
                    continue;
                }

                // Project 4 corners to 2D screen coordinates
                FaceDraw fd = facePool.get(faceIndex++);
                fd.depth = viewCenter[2];

                // Check sticker color for this cubie face
                fd.stickerColor = 0;
                for (CubeModel.Sticker s : cubie.stickers) {
                    if (s.n[0] == n[0] && s.n[1] == n[1] && s.n[2] == n[2]) {
                        fd.stickerColor = s.color;
                        break;
                    }
                }

                projectPoint(corners[0], ca, sa, ce, se, cx, cy, unit, fd.p0);
                projectPoint(corners[1], ca, sa, ce, se, cx, cy, unit, fd.p1);
                projectPoint(corners[2], ca, sa, ce, se, cx, cy, unit, fd.p2);
                projectPoint(corners[3], ca, sa, ce, se, cx, cy, unit, fd.p3);

                visibleFaces.add(fd);
            }
        }

        // Painter's algorithm: sort faces by depth ascending (far to near)
        Collections.sort(visibleFaces, depthComparator);

        // Draw each visible face
        for (FaceDraw fd : visibleFaces) {
            float scx = (fd.p0[0] + fd.p1[0] + fd.p2[0] + fd.p3[0]) * 0.25f;
            float scy = (fd.p0[1] + fd.p1[1] + fd.p2[1] + fd.p3[1]) * 0.25f;

            // 1. Black cubie face body
            drawPath.reset();
            drawPath.moveTo(fd.p0[0], fd.p0[1]);
            drawPath.lineTo(fd.p1[0], fd.p1[1]);
            drawPath.lineTo(fd.p2[0], fd.p2[1]);
            drawPath.lineTo(fd.p3[0], fd.p3[1]);
            drawPath.close();
            canvas.drawPath(drawPath, blackBodyPaint);

            // 2. Coloured sticker (inset from corners toward center)
            if (fd.stickerColor != 0) {
                stickerPaint.setColor(fd.stickerColor);

                drawPath.reset();
                drawPath.moveTo(scx + (fd.p0[0] - scx) * STICKER_SCALE, scy + (fd.p0[1] - scy) * STICKER_SCALE);
                drawPath.lineTo(scx + (fd.p1[0] - scx) * STICKER_SCALE, scy + (fd.p1[1] - scy) * STICKER_SCALE);
                drawPath.lineTo(scx + (fd.p2[0] - scx) * STICKER_SCALE, scy + (fd.p2[1] - scy) * STICKER_SCALE);
                drawPath.lineTo(scx + (fd.p3[0] - scx) * STICKER_SCALE, scy + (fd.p3[1] - scy) * STICKER_SCALE);
                drawPath.close();

                canvas.drawPath(drawPath, stickerPaint);
                canvas.drawPath(drawPath, strokePaint);
            }
        }
    }

    private void projectPoint(float[] p3d, float ca, float sa, float ce, float se,
                              float cx, float cy, float unit, float[] outScreen) {
        float[] v = toView(p3d, ca, sa, ce, se);
        float k = CAMERA_DIST / (CAMERA_DIST - v[2]);
        outScreen[0] = cx + v[0] * k * unit;
        outScreen[1] = cy - v[1] * k * unit;
    }

    private float[][] buildCorners(int f, float cX, float cY, float cZ) {
        float H = HALF_EXTENT;
        float[][] corners = new float[4][3];
        switch (f) {
            case CubeModel.U: // +Y
                corners[0] = new float[]{cX - H, cY + H, cZ - H};
                corners[1] = new float[]{cX + H, cY + H, cZ - H};
                corners[2] = new float[]{cX + H, cY + H, cZ + H};
                corners[3] = new float[]{cX - H, cY + H, cZ + H};
                break;
            case CubeModel.D: // -Y
                corners[0] = new float[]{cX - H, cY - H, cZ + H};
                corners[1] = new float[]{cX + H, cY - H, cZ + H};
                corners[2] = new float[]{cX + H, cY - H, cZ - H};
                corners[3] = new float[]{cX - H, cY - H, cZ - H};
                break;
            case CubeModel.R: // +X
                corners[0] = new float[]{cX + H, cY + H, cZ + H};
                corners[1] = new float[]{cX + H, cY + H, cZ - H};
                corners[2] = new float[]{cX + H, cY - H, cZ - H};
                corners[3] = new float[]{cX + H, cY - H, cZ + H};
                break;
            case CubeModel.L: // -X
                corners[0] = new float[]{cX - H, cY + H, cZ - H};
                corners[1] = new float[]{cX - H, cY + H, cZ + H};
                corners[2] = new float[]{cX - H, cY - H, cZ + H};
                corners[3] = new float[]{cX - H, cY - H, cZ - H};
                break;
            case CubeModel.F: // +Z
                corners[0] = new float[]{cX - H, cY + H, cZ + H};
                corners[1] = new float[]{cX + H, cY + H, cZ + H};
                corners[2] = new float[]{cX + H, cY - H, cZ + H};
                corners[3] = new float[]{cX - H, cY - H, cZ + H};
                break;
            case CubeModel.B: // -Z
            default:
                corners[0] = new float[]{cX + H, cY + H, cZ - H};
                corners[1] = new float[]{cX - H, cY + H, cZ - H};
                corners[2] = new float[]{cX - H, cY - H, cZ - H};
                corners[3] = new float[]{cX + H, cY - H, cZ - H};
                break;
        }
        return corners;
    }
}
