package com.example.rubikssolve.camera;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * HSV-based color classifier for Rubik's Cube facelets.
 *
 * <p>Given a Bitmap that represents the 3×3 grid area captured by the camera,
 * this class samples the inner 40% of each cell (to avoid edge reflections /
 * sticker borders), averages the pixel colors, converts to HSV, and finds the
 * closest of the 6 Rubik's colors using a weighted Euclidean distance where
 * Hue is weighted more heavily than Saturation or Value.</p>
 *
 * <p>No Android framework dependencies beyond {@link android.graphics}.</p>
 */
public class CubeColorClassifier {

    // ── Rubik's color constants (matches MainActivity) ────────────────────────
    public static final int COLOR_WHITE  = 0xFFFFFFFF;
    public static final int COLOR_RED    = 0xFFB71234;
    public static final int COLOR_GREEN  = 0xFF009B48;
    public static final int COLOR_YELLOW = 0xFFFFD500;
    public static final int COLOR_ORANGE = 0xFFFF5800;
    public static final int COLOR_BLUE   = 0xFF0046AD;

    private static final int[] RUBIK_COLORS = {
        COLOR_WHITE, COLOR_YELLOW, COLOR_GREEN, COLOR_RED, COLOR_ORANGE, COLOR_BLUE
    };

    /**
     * Reference HSV for each color above (same index order as RUBIK_COLORS).
     * [Hue 0–360, Saturation 0–1, Value 0–1]
     *
     * <p>These are empirical midpoints tuned for a standard 3×3 Rubik's cube
     * under normal indoor lighting (~3000–5000 K, ~200–500 lux).</p>
     */
    private static final float[][] REFERENCE_HSV = {
        //  H      S      V
        {   0f, 0.05f, 0.90f },  // White  — near-zero saturation
        {  52f, 0.90f, 0.90f },  // Yellow — H ≈ 52°
        { 140f, 0.80f, 0.60f },  // Green  — H ≈ 140°
        {   5f, 0.85f, 0.72f },  // Red    — H ≈ 0–10° (wraps)
        {  20f, 0.92f, 0.80f },  // Orange — H ≈ 18–25°
        { 220f, 0.85f, 0.55f },  // Blue   — H ≈ 215–230°
    };

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Classify all 9 cells of the grid bitmap at once.
     *
     * @param gridBitmap Bitmap whose bounds represent exactly the 3×3 grid area.
     * @return int[9] of Rubik's color constants, row-major (index 0 = top-left,
     *         index 8 = bottom-right).
     */
    public static int[] classifyAllCells(Bitmap gridBitmap) {
        int[] result = new int[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                result[row * 3 + col] = classifyCell(gridBitmap, row, col);
            }
        }
        return result;
    }

    /**
     * Classify the color of a single cell.
     *
     * @param gridBitmap Full 3×3 grid bitmap.
     * @param row        0 (top) – 2 (bottom)
     * @param col        0 (left) – 2 (right)
     * @return One of the {@code COLOR_*} constants.
     */
    public static int classifyCell(Bitmap gridBitmap, int row, int col) {
        int bw = gridBitmap.getWidth();
        int bh = gridBitmap.getHeight();

        int cellW = bw / 3;
        int cellH = bh / 3;

        int cellLeft = col * cellW;
        int cellTop  = row * cellH;

        // Sample the inner 30%–70% of each cell to avoid sticker borders
        int sampleLeft  = cellLeft + (int)(cellW * 0.30f);
        int sampleTop   = cellTop  + (int)(cellH * 0.30f);
        int sampleRight = cellLeft + (int)(cellW * 0.70f);
        int sampleBot   = cellTop  + (int)(cellH * 0.70f);

        // Clamp to bitmap bounds
        sampleLeft  = Math.max(0, Math.min(sampleLeft,  bw - 1));
        sampleTop   = Math.max(0, Math.min(sampleTop,   bh - 1));
        sampleRight = Math.max(0, Math.min(sampleRight, bw));
        sampleBot   = Math.max(0, Math.min(sampleBot,   bh));

        // Average pixel colors in the sample region
        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        // Step by 2 pixels for performance (still accurate enough)
        for (int y = sampleTop; y < sampleBot; y += 2) {
            for (int x = sampleLeft; x < sampleRight; x += 2) {
                int pixel = gridBitmap.getPixel(x, y);
                sumR += Color.red(pixel);
                sumG += Color.green(pixel);
                sumB += Color.blue(pixel);
                count++;
            }
        }

        if (count == 0) return COLOR_WHITE; // safe fallback

        int avgR = (int)(sumR / count);
        int avgG = (int)(sumG / count);
        int avgB = (int)(sumB / count);

        float[] hsv = new float[3];
        Color.RGBToHSV(avgR, avgG, avgB, hsv);

        return findClosestRubikColor(hsv);
    }

    // ── Classification internals ──────────────────────────────────────────────

    private static int findClosestRubikColor(float[] hsv) {
        float h = hsv[0]; // 0–360
        float s = hsv[1]; // 0–1
        float v = hsv[2]; // 0–1

        // White: very low saturation, regardless of hue
        if (s < 0.20f && v > 0.60f) return COLOR_WHITE;

        // Very dark pixels (shadow / underexposed) → skip Hue, use nearest by S/V
        // fall through to distance comparison below

        float bestDist = Float.MAX_VALUE;
        int   bestColor = COLOR_WHITE;

        for (int i = 0; i < RUBIK_COLORS.length; i++) {
            float refH = REFERENCE_HSV[i][0];
            float refS = REFERENCE_HSV[i][1];
            float refV = REFERENCE_HSV[i][2];

            // Circular hue distance (0–180)
            float dH = Math.abs(h - refH);
            if (dH > 180f) dH = 360f - dH;

            float dS = Math.abs(s - refS);
            float dV = Math.abs(v - refV);

            // Weighted distance — Hue most important, Value least
            float dist = (dH / 180f) * 2.0f
                       + dS          * 1.0f
                       + dV          * 0.5f;

            if (dist < bestDist) {
                bestDist  = dist;
                bestColor = RUBIK_COLORS[i];
            }
        }

        return bestColor;
    }
}
