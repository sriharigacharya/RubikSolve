package com.example.rubikssolve.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the CameraX lifecycle for the face-scan screen.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Bind {@code Preview} + {@code ImageAnalysis} use cases to the given
 *       {@link LifecycleOwner} and {@link PreviewView}.</li>
 *   <li>Analyse frames at ~5 fps: crop the center-square grid area, run
 *       {@link CubeColorClassifier} on all 9 cells, and push results to the
 *       overlay and the UI callback.</li>
 *   <li>On {@link #captureCurrentFace(int)} freeze the latest detected colors
 *       into the caller's {@code cubeColors} array.</li>
 *   <li>Unbind the camera on {@link #shutdown()}.</li>
 * </ul>
 */
public class CameraScanController {

    private static final String TAG = "CameraScanController";

    /** Frames per second to run color analysis (lower = less CPU). */
    private static final int ANALYSIS_FPS = 5;
    private static final long ANALYSIS_INTERVAL_MS = 1000L / ANALYSIS_FPS;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final LifecycleOwner     lifecycleOwner;
    private final ScanOverlayView    overlayView;
    private final AnalysisCallback   analysisCallback;

    private ProcessCameraProvider    cameraProvider;
    private final ExecutorService    analysisExecutor = Executors.newSingleThreadExecutor();
    private final Handler            mainHandler      = new Handler(Looper.getMainLooper());

    // ── State ─────────────────────────────────────────────────────────────────

    /** Latest detected colors from the analysis thread (9-element array, row-major). */
    private volatile int[] latestColors = new int[9];

    /** Timestamp of the last frame analysed (to throttle to ANALYSIS_FPS). */
    private long lastAnalysisTime = 0;

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * Called on the main thread whenever a new frame has been analysed.
     */
    public interface AnalysisCallback {
        /**
         * @param colors 9-element array of Rubik's color ints, row-major.
         */
        void onColorsDetected(int[] colors);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public CameraScanController(@NonNull LifecycleOwner lifecycleOwner,
                                @NonNull ScanOverlayView overlayView,
                                @NonNull AnalysisCallback callback) {
        this.lifecycleOwner   = lifecycleOwner;
        this.overlayView      = overlayView;
        this.analysisCallback = callback;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start the camera and bind to the given {@link PreviewView}.
     * Must be called on the main thread.
     */
    public void startCamera(@NonNull PreviewView previewView) {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(previewView.getContext());

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases(cameraProvider, previewView);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "CameraProvider failed", e);
            }
        }, ContextCompat.getMainExecutor(previewView.getContext()));
    }

    /**
     * Freeze the latest detected colors into the given face slot.
     *
     * @param faceIndex 0–5 (FACE_U / FACE_R / FACE_F / FACE_D / FACE_L / FACE_B)
     * @param target    The caller's cubeColors[faceIndex] array to write into.
     */
    public void captureCurrentFace(int faceIndex, int[] target) {
        int[] snap = latestColors; // volatile read — safe to read length
        if (snap != null && target != null && target.length >= 9) {
            System.arraycopy(snap, 0, target, 0, 9);
        }
    }

    /**
     * Stop the camera and release all resources.
     * Safe to call multiple times.
     */
    public void shutdown() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        analysisExecutor.shutdownNow();
    }

    // ── CameraX binding ───────────────────────────────────────────────────────

    private void bindUseCases(@NonNull ProcessCameraProvider provider,
                              @NonNull PreviewView previewView) {
        provider.unbindAll();

        // Preview use-case — renders the camera feed into PreviewView
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Analysis use-case — receives frames for color detection
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

        // Use back camera
        androidx.camera.core.CameraSelector selector =
                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use-case binding failed", e);
        }
    }

    // ── Frame analysis ────────────────────────────────────────────────────────

    @ExperimentalGetImage
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        try {
            // Throttle to ANALYSIS_FPS
            long now = System.currentTimeMillis();
            if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) return;
            lastAnalysisTime = now;

            // Convert to Bitmap
            Bitmap bitmap = toBitmap(imageProxy);
            if (bitmap == null) return;

            // Rotate to correct device orientation
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                Bitmap rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                bitmap = rotated;
            }

            // Crop to the center square — mirrors what ScanOverlayView draws
            Bitmap gridBitmap = cropCenterSquare(bitmap);
            bitmap.recycle();

            // Classify all 9 cells
            final int[] colors = CubeColorClassifier.classifyAllCells(gridBitmap);
            gridBitmap.recycle();

            latestColors = colors;

            // Post results back to the main thread
            mainHandler.post(() -> {
                overlayView.setDetectedColors(colors);
                analysisCallback.onColorsDetected(colors);
            });

        } finally {
            imageProxy.close();
        }
    }

    // ── Bitmap helpers ────────────────────────────────────────────────────────

    /**
     * Convert a CameraX {@link ImageProxy} (YUV_420_888) to an ARGB {@link Bitmap}.
     * Uses {@link YuvImage} for compatibility down to API 21.
     */
    @ExperimentalGetImage
    private static Bitmap toBitmap(@NonNull ImageProxy imageProxy) {
        android.media.Image image = imageProxy.getImage();
        if (image == null) return null;
        if (image.getFormat() != ImageFormat.YUV_420_888) return null;

        int width  = image.getWidth();
        int height = image.getHeight();

        android.media.Image.Plane[] planes = image.getPlanes();

        // Build NV21 byte array from YUV_420_888 planes
        ByteBuffer yBuffer  = planes[0].getBuffer();
        ByteBuffer uBuffer  = planes[1].getBuffer();
        ByteBuffer vBuffer  = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        // CameraX returns YUV_420_888 with V before U in NV21 order
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80, out);
        byte[] jpeg = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
    }

    /**
     * Crop a center square from the bitmap, matching the fraction used by
     * {@link ScanOverlayView#GRID_SIZE_FRACTION} (72% of min dimension).
     */
    private static Bitmap cropCenterSquare(@NonNull Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();

        // Use same fraction as ScanOverlayView.GRID_SIZE_FRACTION = 0.72
        int size = (int)(Math.min(w, h) * 0.72f);
        int x = (w - size) / 2;
        int y = (h - size) / 2;

        return Bitmap.createBitmap(src, x, y, size, size);
    }
}
