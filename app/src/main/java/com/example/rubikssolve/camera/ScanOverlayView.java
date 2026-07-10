package com.example.rubikssolve.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Transparent overlay drawn on top of the CameraX {@code PreviewView}.
 *
 * <p>Draws:</p>
 * <ul>
 *   <li>A semi-transparent dark vignette <em>outside</em> the 3×3 grid area.</li>
 *   <li>White grid lines dividing the capture area into 9 cells.</li>
 *   <li>Rounded corner brackets at the grid corners for alignment guidance.</li>
 *   <li>Small coloured dots in the centre of each cell showing the live
 *       detected colour (updated via {@link #setDetectedColors(int[])}).</li>
 * </ul>
 */
public class ScanOverlayView extends View {

    // ── Grid geometry ─────────────────────────────────────────────────────────
    /** Grid occupies this fraction of the view's smaller dimension. */
    private static final float GRID_SIZE_FRACTION = 0.72f;

    private final RectF gridRect  = new RectF();
    private final RectF cellRect  = new RectF(); // reused scratch

    // ── Detected colors (9 cells, row-major) ─────────────────────────────────
    private final int[] detectedColors = new int[9];

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint vignettePaint;
    private final Paint clearPaint;
    private final Paint gridPaint;
    private final Paint bracketPaint;
    private final Paint dotPaint;
    private final Paint dotBorderPaint;

    // ── Constructors ──────────────────────────────────────────────────────────

    public ScanOverlayView(Context context) {
        this(context, null);
    }

    public ScanOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScanOverlayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Must draw to an offscreen layer so PorterDuff CLEAR works correctly
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Translucent dark vignette
        vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vignettePaint.setColor(Color.argb(170, 0, 0, 0));
        vignettePaint.setStyle(Paint.Style.FILL);

        // Punch-through: clears the grid area in the vignette
        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // White grid lines
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2.5f);

        // Thicker white bracket marks at corners
        bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bracketPaint.setColor(Color.WHITE);
        bracketPaint.setStyle(Paint.Style.STROKE);
        bracketPaint.setStrokeWidth(5f);
        bracketPaint.setStrokeCap(Paint.Cap.SQUARE);

        // Colour dot fill
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);

        // Dot border
        dotBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotBorderPaint.setColor(Color.argb(80, 0, 0, 0));
        dotBorderPaint.setStyle(Paint.Style.STROKE);
        dotBorderPaint.setStrokeWidth(1.5f);

        // Start with "unassigned" colour dots
        for (int i = 0; i < 9; i++) {
            detectedColors[i] = Color.argb(100, 44, 44, 53); // rubik_unassigned-ish
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Update the live detected colours for all 9 cells.
     *
     * @param colors Array of 9 colour ints (row-major). Must not be null.
     */
    public void setDetectedColors(int[] colors) {
        if (colors == null || colors.length != 9) return;
        System.arraycopy(colors, 0, detectedColors, 0, 9);
        postInvalidate(); // safe to call from any thread
    }

    /**
     * Return the grid {@link RectF} in this View's coordinate space.
     * Useful for mapping overlay coords to camera analysis coords.
     */
    public RectF getGridRect() {
        return new RectF(gridRect);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        updateGridRect(w, h);
    }

    private void updateGridRect(int viewW, int viewH) {
        int size = (int)(Math.min(viewW, viewH) * GRID_SIZE_FRACTION);
        float left = (viewW - size) / 2f;
        float top  = (viewH - size) / 2f;
        gridRect.set(left, top, left + size, top + size);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (gridRect.isEmpty()) return;

        // 1. Dark vignette covering the whole view
        canvas.drawRect(0, 0, getWidth(), getHeight(), vignettePaint);

        // 2. Punch a transparent hole where the grid is
        canvas.drawRect(gridRect, clearPaint);

        // 3. Draw the 3×3 grid lines inside the punched area
        drawGrid(canvas);

        // 4. Corner bracket marks
        drawCornerBrackets(canvas);

        // 5. Colour dots in each cell
        drawDetectedDots(canvas);
    }

    private void drawGrid(Canvas canvas) {
        float cellW = gridRect.width()  / 3f;
        float cellH = gridRect.height() / 3f;

        // Outer border
        canvas.drawRect(gridRect, gridPaint);

        // Vertical dividers
        for (int col = 1; col <= 2; col++) {
            float x = gridRect.left + col * cellW;
            canvas.drawLine(x, gridRect.top, x, gridRect.bottom, gridPaint);
        }
        // Horizontal dividers
        for (int row = 1; row <= 2; row++) {
            float y = gridRect.top + row * cellH;
            canvas.drawLine(gridRect.left, y, gridRect.right, y, gridPaint);
        }
    }

    private void drawCornerBrackets(Canvas canvas) {
        float arm = gridRect.width() * 0.08f; // bracket arm length
        float l = gridRect.left,  r = gridRect.right;
        float t = gridRect.top,   b = gridRect.bottom;

        Path path = new Path();

        // Top-left
        path.moveTo(l, t + arm);
        path.lineTo(l, t);
        path.lineTo(l + arm, t);

        // Top-right
        path.moveTo(r - arm, t);
        path.lineTo(r, t);
        path.lineTo(r, t + arm);

        // Bottom-right
        path.moveTo(r, b - arm);
        path.lineTo(r, b);
        path.lineTo(r - arm, b);

        // Bottom-left
        path.moveTo(l + arm, b);
        path.lineTo(l, b);
        path.lineTo(l, b - arm);

        canvas.drawPath(path, bracketPaint);
    }

    private void drawDetectedDots(Canvas canvas) {
        float cellW = gridRect.width()  / 3f;
        float cellH = gridRect.height() / 3f;
        float dotR  = Math.min(cellW, cellH) * 0.18f;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float cx = gridRect.left + col * cellW + cellW / 2f;
                float cy = gridRect.top  + row * cellH + cellH / 2f;

                int color = detectedColors[row * 3 + col];

                dotPaint.setColor(color);
                canvas.drawCircle(cx, cy, dotR, dotPaint);
                canvas.drawCircle(cx, cy, dotR, dotBorderPaint);
            }
        }
    }
}
