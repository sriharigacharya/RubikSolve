package com.example.rubikssolve;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.example.rubikssolve.camera.CameraScanController;
import com.example.rubikssolve.camera.CubeColorClassifier;
import com.example.rubikssolve.camera.ScanOverlayView;
import com.example.rubikssolve.solver.Search;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Views
    private View viewHome;
    private View viewMenu;
    private View viewColorInput;
    private View viewSolveGuide;
    private View viewMoveNotationGuide;
    private View viewCameraScan;

    // Camera scan
    private CameraScanController cameraScanController;
    private View[]   camFaceCircles  = new View[6];      // colored circles in cam screen
    private View[]   camCheckMarks   = new View[6];      // ✓ indicators per face
    private View[]   detectedCells   = new View[9];      // mini 3×3 preview
    private int      camCurrentFace  = 2;                // defaults to FACE_F
    private boolean  anyCaptured     = false;
    private final boolean[] faceCaptured = new boolean[6];

    // Camera permission launcher (must be registered before onCreate returns)
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // Solver Service
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Search solver;

    // Cube state colors representation
    // Order of faces: U (Up), R (Right), F (Front), D (Down), L (Left), B (Back)
    // 6 faces, 9 cells per face
    private final int[][] cubeColors = new int[6][9];
    
    // UI mapping values
    private static final int FACE_U = 0;
    private static final int FACE_R = 1;
    private static final int FACE_F = 2;
    private static final int FACE_D = 3;
    private static final int FACE_L = 4;
    private static final int FACE_B = 5;

    // Actual RGB Colors
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_RED = 0xFFB71234;
    private static final int COLOR_GREEN = 0xFF009B48;
    private static final int COLOR_YELLOW = 0xFFFFD500;
    private static final int COLOR_ORANGE = 0xFFFF5800;
    private static final int COLOR_BLUE = 0xFF0046AD;
    private static final int COLOR_UNASSIGNED = 0xFF2C2C35;

    private static final int[] PALETTE_COLORS = {
        COLOR_WHITE, COLOR_ORANGE, COLOR_GREEN, COLOR_RED, COLOR_BLUE, COLOR_YELLOW
    };

    // Center colors of faces are fixed
    private static final int[] CENTER_COLORS = {
        COLOR_WHITE,  // U
        COLOR_RED,    // R
        COLOR_GREEN,  // F
        COLOR_YELLOW, // D
        COLOR_ORANGE, // L
        COLOR_BLUE    // B
    };

    private static final String[] FACE_NAMES = {
        "UP (White Center)", "RIGHT (Red Center)", "FRONT (Green Center)",
        "DOWN (Yellow Center)", "LEFT (Orange Center)", "BACK (Blue Center)"
    };

    // Paint State
    private int selectedPaintColor = COLOR_WHITE;
    private int currentFaceIndex = FACE_F; // Start with Front face for color entry

    // Orientation adjacency table: for each face index, {topFace, bottomFace, leftFace, rightFace}
    // Cube held with Green (F) facing user, White (U) on top.
    // Indices: U=0, R=1, F=2, D=3, L=4, B=5
    private static final int[][] FACE_ADJACENCY = {
        // {top,    bottom, left,   right}
        {FACE_B, FACE_F, FACE_L, FACE_R},  // U (White) - top is Back, bottom is Front
        {FACE_U, FACE_D, FACE_F, FACE_B},  // R (Red)   - top is Up, left is Front
        {FACE_U, FACE_D, FACE_L, FACE_R},  // F (Green) - top is Up, left is Left
        {FACE_F, FACE_B, FACE_L, FACE_R},  // D (Yellow)- top is Front, bottom is Back
        {FACE_U, FACE_D, FACE_B, FACE_F},  // L (Orange)- top is Up, right is Front
        {FACE_U, FACE_D, FACE_R, FACE_L},  // B (Blue)  - top is Up, left is Right (mirrored)
    };

    private static final String[] COLOR_NAMES = {
        "White", "Red", "Green", "Yellow", "Orange", "Blue"
    };

    // UI Input Components
    //
    // Face circles are laid out in the order: U, L, F, R, B, D
    // But the face *constants* are:           U=0, R=1, F=2, D=3, L=4, B=5
    // These two arrays provide the bidirectional mapping so selection
    // highlight and click listeners always refer to the right face.
    //
    // BUTTON_TO_FACE[buttonPos] → face constant for that circle
    private static final int[] BUTTON_TO_FACE = {
        FACE_U, FACE_L, FACE_F, FACE_R, FACE_B, FACE_D  // positions 0-5 in layout
    };
    // FACE_TO_BUTTON[faceConstant] → button position in the circle row
    private static final int[] FACE_TO_BUTTON = {
        0,  // FACE_U=0  → button pos 0
        3,  // FACE_R=1  → button pos 3
        2,  // FACE_F=2  → button pos 2
        5,  // FACE_D=3  → button pos 5
        1,  // FACE_L=4  → button pos 1
        4   // FACE_B=5  → button pos 4
    };

    private View[] faceCircles = new View[6];
    private View[] cellViews = new View[9];
    private ImageButton[] paletteButtons = new ImageButton[6];
    private TextView tvCurrentFaceName;
    private MaterialButton btnSolveRun;

    // Orientation compass views
    private View orientTopColor, orientBottomColor, orientLeftColor, orientRightColor;
    private TextView orientTopName, orientBottomName, orientLeftName, orientRightName;

    // Solve Guide State
    private List<String> moves = new ArrayList<>();
    private int currentStep = 0;

    // UI Solve Guide Components
    private TextView tvGuideStep;
    private LinearProgressIndicator progressSolveSteps;
    private TextView tvGuideMove;
    private TextView tvGuideDescription;
    private TextView tvVisualTitle;
    private ImageView imgTurnArrow;
    private LinearLayout llFullSequenceContainer;
    private MaterialButton btnGuidePrev;
    private MaterialButton btnGuideNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind root layout views
        viewHome = findViewById(R.id.view_home);
        viewMenu = findViewById(R.id.view_menu);
        viewColorInput = findViewById(R.id.view_color_input);
        viewSolveGuide = findViewById(R.id.view_solve_guide);
        viewMoveNotationGuide = findViewById(R.id.view_move_notation_guide);
        viewCameraScan = findViewById(R.id.view_camera_scan);

        // Register camera permission launcher (must be before onStart)
        cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    showView(viewCameraScan);
                    startCameraPreview();
                } else {
                    showValidationDialog("Camera Permission Required",
                        "Camera access is needed to scan your cube's colors. " +
                        "Please grant the permission in Settings, or use \"Enter Colors Manually\".");
                }
            });

        // Pre-initialize Kociemba tables in background to keep UI snappy
        executorService.execute(() -> {
            solver = new Search();
        });

        // Initialize state arrays
        resetCubeColors();

        // Initialize Home Screen
        setupHomeScreen();

        // Initialize Menu Screen
        setupMenuScreen();

        // Initialize Color Input Screen
        setupColorInputScreen();

        // Initialize Solve Guide Screen
        setupSolveGuideScreen();

        // Initialize Move Notation Guide Screen
        setupMoveNotationGuideScreen();

        // Initialize Camera Scan Screen
        setupCameraScanScreen();

        // Override system back button for all screens.
        // Since the app swaps views inside a single Activity the OS back stack
        // doesn't know about our screens, so we handle it manually here.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewSolveGuide.getVisibility() == View.VISIBLE) {
                    showView(viewColorInput);
                } else if (viewColorInput.getVisibility() == View.VISIBLE) {
                    showView(viewMenu);
                } else if (viewMoveNotationGuide.getVisibility() == View.VISIBLE) {
                    showView(viewMenu);
                } else if (viewCameraScan.getVisibility() == View.VISIBLE) {
                    showView(viewMenu); // showView() also stops the camera
                } else if (viewMenu.getVisibility() == View.VISIBLE) {
                    showView(viewHome);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void resetCubeColors() {
        for (int f = 0; f < 6; f++) {
            for (int c = 0; c < 9; c++) {
                if (c == 4) {
                    cubeColors[f][c] = CENTER_COLORS[f]; // Center is fixed
                } else {
                    cubeColors[f][c] = COLOR_UNASSIGNED; // Clear other cells
                }
            }
        }
    }

    private void showView(View viewToShow) {
        viewHome.setVisibility(viewToShow == viewHome ? View.VISIBLE : View.GONE);
        viewMenu.setVisibility(viewToShow == viewMenu ? View.VISIBLE : View.GONE);
        viewColorInput.setVisibility(viewToShow == viewColorInput ? View.VISIBLE : View.GONE);
        viewSolveGuide.setVisibility(viewToShow == viewSolveGuide ? View.VISIBLE : View.GONE);
        viewMoveNotationGuide.setVisibility(viewToShow == viewMoveNotationGuide ? View.VISIBLE : View.GONE);
        viewCameraScan.setVisibility(viewToShow == viewCameraScan ? View.VISIBLE : View.GONE);
        // Stop camera when leaving the camera screen
        if (viewToShow != viewCameraScan && cameraScanController != null) {
            cameraScanController.shutdown();
            cameraScanController = null;
        }
    }

    // --- HOME SCREEN SETUP ---
    private void setupHomeScreen() {
        MaterialButton btnSolveStart = findViewById(R.id.btn_solve_start);
        // Home → Menu (user picks how they want to solve)
        btnSolveStart.setOnClickListener(v -> showView(viewMenu));
    }

    // --- MENU SCREEN SETUP ---
    private void setupMenuScreen() {
        // Back to home
        MaterialButton btnMenuBack = findViewById(R.id.btn_menu_back);
        btnMenuBack.setOnClickListener(v -> showView(viewHome));

        // Option 1: Manual color entry — fully implemented
        View cardManual = findViewById(R.id.card_manual_colors);
        cardManual.setOnClickListener(v -> {
            resetCubeColors();
            currentFaceIndex = FACE_F;
            selectedPaintColor = COLOR_WHITE;
            updatePaletteUI();
            updateGridUI();
            updateFaceSelectorUI();
            showView(viewColorInput);
        });

        // Option 2: Camera scan — now implemented
        View cardCamera = findViewById(R.id.card_camera_solve);
        cardCamera.setOnClickListener(v -> {
            // Check / request permission, then open camera
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.CAMERA)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showView(viewCameraScan);
                startCameraPreview();
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });

        // Option 3: Move notation guide — now implemented
        View cardMoveGuide = findViewById(R.id.card_move_guide);
        cardMoveGuide.setOnClickListener(v -> showView(viewMoveNotationGuide));
    }

    // ── CAMERA SCAN SCREEN SETUP ─────────────────────────────────────────

    // Mapping: button position (U/L/F/R/B/D layout order) → face constant
    // Same ordering as the manual color-input screen's BUTTON_TO_FACE table.
    private static final int[] CAM_BUTTON_TO_FACE = { FACE_U, FACE_L, FACE_F, FACE_R, FACE_B, FACE_D };

    private void setupCameraScanScreen() {
        // Back button
        MaterialButton btnBack = viewCameraScan.findViewById(R.id.btn_cam_back);
        btnBack.setOnClickListener(v -> showView(viewMenu));

        // Face selector circles
        int[] circleIds = { R.id.cam_face_circle_u, R.id.cam_face_circle_l,
                            R.id.cam_face_circle_f, R.id.cam_face_circle_r,
                            R.id.cam_face_circle_b, R.id.cam_face_circle_d };
        int[] checkIds  = { R.id.cam_check_u, R.id.cam_check_l, R.id.cam_check_f,
                            R.id.cam_check_r, R.id.cam_check_b, R.id.cam_check_d };

        for (int i = 0; i < 6; i++) {
            camFaceCircles[i] = viewCameraScan.findViewById(circleIds[i]);
            camCheckMarks[i]  = viewCameraScan.findViewById(checkIds[i]);
            final int faceIndex = CAM_BUTTON_TO_FACE[i];
            final int btnPos    = i;
            camFaceCircles[i].setOnClickListener(v -> {
                camCurrentFace = faceIndex;
                updateCamFaceSelectorUI();
            });
        }

        // Mini detected-color preview cells
        int[] detIds = { R.id.det_cell_0, R.id.det_cell_1, R.id.det_cell_2,
                         R.id.det_cell_3, R.id.det_cell_4, R.id.det_cell_5,
                         R.id.det_cell_6, R.id.det_cell_7, R.id.det_cell_8 };
        for (int i = 0; i < 9; i++) {
            detectedCells[i] = viewCameraScan.findViewById(detIds[i]);
        }

        // Capture button
        MaterialButton btnCapture = viewCameraScan.findViewById(R.id.btn_capture_face);
        btnCapture.setOnClickListener(v -> captureCameraFace());

        // Done button (hidden until at least 1 face captured)
        MaterialButton btnDone = viewCameraScan.findViewById(R.id.btn_cam_done);
        btnDone.setOnClickListener(v -> finishCameraScanning());

        // Initialise selector UI
        updateCamFaceSelectorUI();
    }

    /** Called when the camera screen becomes visible — starts the camera preview. */
    private void startCameraPreview() {
        PreviewView previewView = viewCameraScan.findViewById(R.id.preview_view);
        ScanOverlayView overlay = viewCameraScan.findViewById(R.id.scan_overlay);

        cameraScanController = new CameraScanController(this, overlay, colors -> {
            // Main-thread callback — update the mini detected-color grid
            for (int i = 0; i < 9 && i < detectedCells.length; i++) {
                setCellVisualColor(detectedCells[i], colors[i]);
            }
        });
        cameraScanController.startCamera(previewView);
    }

    /** Freezes the current live detection into cubeColors for the selected face. */
    private void captureCameraFace() {
        if (cameraScanController == null) return;
        cameraScanController.captureCurrentFace(camCurrentFace, cubeColors[camCurrentFace]);
        // Always override center with known center color
        cubeColors[camCurrentFace][4] = CENTER_COLORS[camCurrentFace];

        // Mark this face as captured
        faceCaptured[camCurrentFace] = true;
        anyCaptured = true;

        // Show checkmark for this face
        for (int i = 0; i < 6; i++) {
            if (CAM_BUTTON_TO_FACE[i] == camCurrentFace) {
                camCheckMarks[i].setVisibility(View.VISIBLE);
                break;
            }
        }

        // Show Done button once at least one face has been captured
        viewCameraScan.findViewById(R.id.btn_cam_done).setVisibility(View.VISIBLE);

        // Brief visual feedback
        Toast.makeText(this, FACE_NAMES[camCurrentFace] + " captured!", Toast.LENGTH_SHORT).show();
    }

    /** Transfers scanned colors to the manual color-input screen for review. */
    private void finishCameraScanning() {
        // cubeColors already has the scanned faces; unscanned faces remain as
        // COLOR_UNASSIGNED so the user can see what still needs to be filled.
        // Force center cells to be correct on all faces.
        for (int f = 0; f < 6; f++) {
            cubeColors[f][4] = CENTER_COLORS[f];
        }
        currentFaceIndex = FACE_F;
        selectedPaintColor = COLOR_WHITE;
        updateGridUI();
        updateFaceSelectorUI();
        updatePaletteUI();
        showView(viewColorInput); // showView also stops the camera
    }

    /** Redraws the face-circle selector in the camera scan screen. */
    private void updateCamFaceSelectorUI() {
        TextView tvFaceName = viewCameraScan.findViewById(R.id.tv_cam_face_name);
        tvFaceName.setText(FACE_NAMES[camCurrentFace]);

        for (int i = 0; i < 6; i++) {
            int faceIdx = CAM_BUTTON_TO_FACE[i];
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(CENTER_COLORS[faceIdx]);
            if (faceIdx == camCurrentFace) {
                int strokeColor = (CENTER_COLORS[faceIdx] == COLOR_WHITE) ? Color.DKGRAY : Color.WHITE;
                shape.setStroke(dpToPx(4), strokeColor);
            } else {
                shape.setStroke(dpToPx(1), 0x55FFFFFF);
            }
            camFaceCircles[i].setBackground(shape);
        }
    }

    // ── MOVE NOTATION GUIDE SCREEN SETUP ────────────────────────────────────
    //
    // Data for all 18 standard moves: face × {CW, CCW, 180°}.
    // Each entry: { notation, label, description, diagramResId, diagramTintColor }
    //
    private void setupMoveNotationGuideScreen() {
        MaterialButton btnBack = viewMoveNotationGuide.findViewById(R.id.btn_notation_back);
        btnBack.setOnClickListener(v -> showView(viewMenu));

        // -- U (Up / White) --
        populateMoveCard(R.id.card_move_u,
            "U", "Up · Clockwise",
            "Rotate the top (white) layer to the right when viewed from above.",
            R.drawable.ic_move_cw, COLOR_WHITE);
        populateMoveCard(R.id.card_move_u_prime,
            "U\u2032", "Up · Counter-Clockwise",
            "Rotate the top (white) layer to the left when viewed from above.",
            R.drawable.ic_move_ccw, COLOR_WHITE);
        populateMoveCard(R.id.card_move_u2,
            "U2", "Up · 180\u00b0 Half Turn",
            "Rotate the top (white) layer by half a turn (direction is your choice).",
            R.drawable.ic_move_180, COLOR_WHITE);

        // -- D (Down / Yellow) --
        populateMoveCard(R.id.card_move_d,
            "D", "Down · Clockwise",
            "Rotate the bottom (yellow) layer to the right when viewed from below.",
            R.drawable.ic_move_cw, COLOR_YELLOW);
        populateMoveCard(R.id.card_move_d_prime,
            "D\u2032", "Down · Counter-Clockwise",
            "Rotate the bottom (yellow) layer to the left when viewed from below.",
            R.drawable.ic_move_ccw, COLOR_YELLOW);
        populateMoveCard(R.id.card_move_d2,
            "D2", "Down · 180\u00b0 Half Turn",
            "Rotate the bottom (yellow) layer by half a turn.",
            R.drawable.ic_move_180, COLOR_YELLOW);

        // -- F (Front / Green) --
        populateMoveCard(R.id.card_move_f,
            "F", "Front · Clockwise",
            "Rotate the front (green) face clockwise when looking directly at it.",
            R.drawable.ic_move_cw, COLOR_GREEN);
        populateMoveCard(R.id.card_move_f_prime,
            "F\u2032", "Front · Counter-Clockwise",
            "Rotate the front (green) face counter-clockwise when looking directly at it.",
            R.drawable.ic_move_ccw, COLOR_GREEN);
        populateMoveCard(R.id.card_move_f2,
            "F2", "Front · 180\u00b0 Half Turn",
            "Rotate the front (green) face by half a turn.",
            R.drawable.ic_move_180, COLOR_GREEN);

        // -- B (Back / Blue) --
        populateMoveCard(R.id.card_move_b,
            "B", "Back · Clockwise",
            "Rotate the back (blue) face clockwise when looking at it from behind.",
            R.drawable.ic_move_cw, COLOR_BLUE);
        populateMoveCard(R.id.card_move_b_prime,
            "B\u2032", "Back · Counter-Clockwise",
            "Rotate the back (blue) face counter-clockwise when looking at it from behind.",
            R.drawable.ic_move_ccw, COLOR_BLUE);
        populateMoveCard(R.id.card_move_b2,
            "B2", "Back · 180\u00b0 Half Turn",
            "Rotate the back (blue) face by half a turn.",
            R.drawable.ic_move_180, COLOR_BLUE);

        // -- L (Left / Orange) --
        populateMoveCard(R.id.card_move_l,
            "L", "Left · Clockwise",
            "Rotate the left (orange) face clockwise when looking directly at it (from the left).",
            R.drawable.ic_move_cw, COLOR_ORANGE);
        populateMoveCard(R.id.card_move_l_prime,
            "L\u2032", "Left · Counter-Clockwise",
            "Rotate the left (orange) face counter-clockwise when looking at it from the left.",
            R.drawable.ic_move_ccw, COLOR_ORANGE);
        populateMoveCard(R.id.card_move_l2,
            "L2", "Left · 180\u00b0 Half Turn",
            "Rotate the left (orange) face by half a turn.",
            R.drawable.ic_move_180, COLOR_ORANGE);

        // -- R (Right / Red) --
        populateMoveCard(R.id.card_move_r,
            "R", "Right · Clockwise",
            "Rotate the right (red) face clockwise when looking directly at it (from the right).",
            R.drawable.ic_move_cw, COLOR_RED);
        populateMoveCard(R.id.card_move_r_prime,
            "R\u2032", "Right · Counter-Clockwise",
            "Rotate the right (red) face counter-clockwise when looking at it from the right.",
            R.drawable.ic_move_ccw, COLOR_RED);
        populateMoveCard(R.id.card_move_r2,
            "R2", "Right · 180\u00b0 Half Turn",
            "Rotate the right (red) face by half a turn.",
            R.drawable.ic_move_180, COLOR_RED);
    }

    /**
     * Populates a move card (included via {@code <include>} in layout_move_notation_guide.xml)
     * with the given notation, label, description, and a tinted diagram drawable.
     *
     * @param cardViewId  Resource ID of the included card (e.g. R.id.card_move_r)
     * @param notation    Move notation string (e.g. "R", "R\u2032", "R2")
     * @param label       Short label (e.g. "Right \u00b7 Clockwise")
     * @param description Full description sentence
     * @param diagramRes  Drawable resource for the face-grid+arrow diagram
     * @param tintColor   ARGB color used to tint the diagram image
     */
    private void populateMoveCard(int cardViewId, String notation, String label,
                                  String description, int diagramRes, int tintColor) {
        View card = viewMoveNotationGuide.findViewById(cardViewId);
        if (card == null) return;

        ((TextView) card.findViewById(R.id.tv_move_notation)).setText(notation);
        ((TextView) card.findViewById(R.id.tv_move_label)).setText(label);
        ((TextView) card.findViewById(R.id.tv_move_description)).setText(description);

        ImageView diagram = card.findViewById(R.id.iv_move_diagram);
        diagram.setImageResource(diagramRes);
        diagram.setImageTintList(android.content.res.ColorStateList.valueOf(tintColor));
    }

    // --- COLOR INPUT SCREEN SETUP ---
    private void setupColorInputScreen() {
        tvCurrentFaceName = findViewById(R.id.tv_current_face_name);
        btnSolveRun = findViewById(R.id.btn_solve_run);

        // Orientation compass views
        orientTopColor    = findViewById(R.id.orient_top_color);
        orientBottomColor = findViewById(R.id.orient_bottom_color);
        orientLeftColor   = findViewById(R.id.orient_left_color);
        orientRightColor  = findViewById(R.id.orient_right_color);
        orientTopName    = findViewById(R.id.orient_top_name);
        orientBottomName = findViewById(R.id.orient_bottom_name);
        orientLeftName   = findViewById(R.id.orient_left_name);
        orientRightName  = findViewById(R.id.orient_right_name);

        // Face selector circles (layout order: U, L, F, R, B, D)
        faceCircles[0] = findViewById(R.id.face_circle_u);
        faceCircles[1] = findViewById(R.id.face_circle_l);
        faceCircles[2] = findViewById(R.id.face_circle_f);
        faceCircles[3] = findViewById(R.id.face_circle_r);
        faceCircles[4] = findViewById(R.id.face_circle_b);
        faceCircles[5] = findViewById(R.id.face_circle_d);

        // Attach click listeners — each circle knows which face it represents
        // via BUTTON_TO_FACE, avoiding the old index mismatch.
        for (int i = 0; i < 6; i++) {
            final int faceIndex = BUTTON_TO_FACE[i];
            faceCircles[i].setOnClickListener(v -> {
                currentFaceIndex = faceIndex;
                updateFaceSelectorUI();
                updateGridUI();
            });
        }

        // Grid Cells Binding
        cellViews[0] = findViewById(R.id.cell_0);
        cellViews[1] = findViewById(R.id.cell_1);
        cellViews[2] = findViewById(R.id.cell_2);
        cellViews[3] = findViewById(R.id.cell_3);
        cellViews[4] = findViewById(R.id.cell_4);
        cellViews[5] = findViewById(R.id.cell_5);
        cellViews[6] = findViewById(R.id.cell_6);
        cellViews[7] = findViewById(R.id.cell_7);
        cellViews[8] = findViewById(R.id.cell_8);

        for (int i = 0; i < 9; i++) {
            final int index = i;
            if (index == 4) continue; // Skip center click
            cellViews[index].setOnClickListener(v -> {
                cubeColors[currentFaceIndex][index] = selectedPaintColor;
                setCellVisualColor(cellViews[index], selectedPaintColor);
                checkAllFacesCompleted();
            });
        }

        // Palette Buttons Binding
        paletteButtons[0] = findViewById(R.id.palette_white);
        paletteButtons[1] = findViewById(R.id.palette_orange);
        paletteButtons[2] = findViewById(R.id.palette_green);
        paletteButtons[3] = findViewById(R.id.palette_red);
        paletteButtons[4] = findViewById(R.id.palette_blue);
        paletteButtons[5] = findViewById(R.id.palette_yellow);

        for (int i = 0; i < 6; i++) {
            final int index = i;
            paletteButtons[index].setOnClickListener(v -> {
                selectedPaintColor = PALETTE_COLORS[index];
                updatePaletteUI();
            });
        }

        // Prev, Next, Clear Face
        MaterialButton btnPrevFace = findViewById(R.id.btn_prev_face);
        MaterialButton btnNextFace = findViewById(R.id.btn_next_face);
        MaterialButton btnResetFace = findViewById(R.id.btn_reset_face);

        btnPrevFace.setOnClickListener(v -> navigateFace(-1));
        btnNextFace.setOnClickListener(v -> navigateFace(1));
        
        btnResetFace.setOnClickListener(v -> {
            for (int c = 0; c < 9; c++) {
                if (c != 4) {
                    cubeColors[currentFaceIndex][c] = COLOR_UNASSIGNED;
                }
            }
            updateGridUI();
            checkAllFacesCompleted();
        });

        // Run Solver
        btnSolveRun.setOnClickListener(v -> runSolver());
        
        // Initial setup for the palette selection indicator
        updatePaletteUI();
    }

    private void navigateFace(int direction) {
        currentFaceIndex = (currentFaceIndex + direction + 6) % 6;
        updateFaceSelectorUI();
        updateGridUI();
    }

    private void updateFaceSelectorUI() {
        // i = button position in the circle row (0-5 maps to U/L/F/R/B/D layout order).
        // BUTTON_TO_FACE[i] gives the face constant so we compare correctly against
        // currentFaceIndex instead of the old (broken) direct i == currentFaceIndex check.
        for (int i = 0; i < 6; i++) {
            int faceIdx = BUTTON_TO_FACE[i];
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(CENTER_COLORS[faceIdx]);

            if (faceIdx == currentFaceIndex) {
                // Selected: prominent white ring (grey for white face so it's visible)
                int strokeColor = (CENTER_COLORS[faceIdx] == COLOR_WHITE) ? Color.DKGRAY : Color.WHITE;
                shape.setStroke(dpToPx(4), strokeColor);
            } else {
                // Unselected: subtle translucent ring
                shape.setStroke(dpToPx(1), 0x55FFFFFF);
            }
            faceCircles[i].setBackground(shape);
        }
        tvCurrentFaceName.setText(FACE_NAMES[currentFaceIndex]);
        updateOrientationUI();
    }

    /** Updates the compass indicators (Top/Bottom/Left/Right) for the current face. */
    private void updateOrientationUI() {
        int[] adj = FACE_ADJACENCY[currentFaceIndex];
        // adj = {topFace, bottomFace, leftFace, rightFace}
        setOrientIndicator(orientTopColor,    orientTopName,    adj[0]);
        setOrientIndicator(orientBottomColor, orientBottomName, adj[1]);
        setOrientIndicator(orientLeftColor,   orientLeftName,   adj[2]);
        setOrientIndicator(orientRightColor,  orientRightName,  adj[3]);
    }

    private void setOrientIndicator(View colorView, TextView nameView, int faceIndex) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dpToPx(6));
        int color = CENTER_COLORS[faceIndex];
        shape.setColor(color);
        if (color == COLOR_WHITE) {
            shape.setStroke(dpToPx(2), 0xFFCCCCCC);
        } else {
            shape.setStroke(dpToPx(1), 0x44000000);
        }
        colorView.setBackground(shape);
        nameView.setText(COLOR_NAMES[faceIndex]);
    }

    private void updateGridUI() {
        for (int i = 0; i < 9; i++) {
            setCellVisualColor(cellViews[i], cubeColors[currentFaceIndex][i]);
        }
    }

    private void setCellVisualColor(View view, int color) {
        // Draw round corner button backgrounds using GradientDrawable
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dpToPx(10));
        shape.setColor(color);
        // Optional dark border for visibility of White
        if (color == COLOR_WHITE) {
            shape.setStroke(dpToPx(2), 0xFFCCCCCC);
        } else {
            shape.setStroke(dpToPx(1), 0x33000000);
        }
        view.setBackground(shape);
    }

    private void updatePaletteUI() {
        for (int i = 0; i < 6; i++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(PALETTE_COLORS[i]);
            
            // Draw a thick white stroke around selected palette color
            if (PALETTE_COLORS[i] == selectedPaintColor) {
                int strokeColor = (selectedPaintColor == COLOR_WHITE) ? Color.DKGRAY : Color.WHITE;
                shape.setStroke(dpToPx(4), strokeColor);
            } else {
                shape.setStroke(dpToPx(1), 0x44FFFFFF);
            }
            paletteButtons[i].setImageDrawable(shape);
        }
    }

    private void checkAllFacesCompleted() {
        boolean allFinished = true;
        for (int f = 0; f < 6; f++) {
            for (int c = 0; c < 9; c++) {
                if (cubeColors[f][c] == COLOR_UNASSIGNED) {
                    allFinished = false;
                    break;
                }
            }
        }
        // Enable or visually highlight the solve run button if completed
        btnSolveRun.setAlpha(allFinished ? 1.0f : 0.5f);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // --- SOLVER EXECUTION ---
    private void runSolver() {
        // 1. Check if all cells are assigned
        for (int f = 0; f < 6; f++) {
            for (int c = 0; c < 9; c++) {
                if (cubeColors[f][c] == COLOR_UNASSIGNED) {
                    showValidationDialog("Incomplete Cube", "Please color all faces of the cube before solving.");
                    return;
                }
            }
        }

        // 2. Validate color counts (Exactly 9 of each color)
        int[] colorCounts = new int[6];
        for (int f = 0; f < 6; f++) {
            for (int c = 0; c < 9; c++) {
                int color = cubeColors[f][c];
                int colorIdx = getColorIndex(color);
                if (colorIdx != -1) {
                    colorCounts[colorIdx]++;
                }
            }
        }

        StringBuilder countErrors = new StringBuilder();
        String[] colorNames = {"White", "Orange", "Green", "Red", "Blue", "Yellow"};
        boolean countInvalid = false;
        for (int i = 0; i < 6; i++) {
            if (colorCounts[i] != 9) {
                countInvalid = true;
                countErrors.append("- ").append(colorNames[i]).append(": ").append(colorCounts[i]).append(" (needs 9)\n");
            }
        }

        if (countInvalid) {
            showValidationDialog("Invalid Color Counts", 
                "Each of the 6 colors must appear exactly 9 times on the cube. Current counts:\n\n" + countErrors.toString() + 
                "\nPlease review your color inputs.");
            return;
        }

        // 3. Build Kociemba 54-char string representation
        // Order expected: U, R, F, D, L, B
        // Index mapping: U=0, R=1, F=2, D=3, L=4, B=5
        // Each character represents the face name corresponding to that facelet's color.
        char[] faceNamesShort = {'U', 'R', 'F', 'D', 'L', 'B'};
        StringBuilder sb = new StringBuilder();

        for (int f = 0; f < 6; f++) {
            for (int c = 0; c < 9; c++) {
                int color = cubeColors[f][c];
                // Map color to center face
                int targetFaceIdx = getCenterFaceFromColor(color);
                if (targetFaceIdx != -1) {
                    sb.append(faceNamesShort[targetFaceIdx]);
                } else {
                    sb.append('U'); // Fallback
                }
            }
        }

        String representation = sb.toString();

        // Show a progress loading dialog
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Solving Cube")
                .setMessage("Finding the shortest path to solve...")
                .setView(new LinearProgressIndicator(this))
                .setCancelable(false)
                .create();
        progressDialog.show();

        // Run Kociemba Solver in background
        executorService.execute(() -> {
            if (solver == null) {
                solver = new Search();
            }

            // Kociemba parameters: String facelets, int maxDepth, long probeMax, long probeMin, int verbose
            // 21 maxDepth, 100000000 probeMax, 0 probeMin, 0 verbose
            final String result = solver.solution(representation, 22, 100000000, 0, 0);

            new Handler(Looper.getMainLooper()).post(() -> {
                progressDialog.dismiss();
                handleSolverResult(result);
            });
        });
    }

    private int getColorIndex(int color) {
        for (int i = 0; i < 6; i++) {
            if (PALETTE_COLORS[i] == color) return i;
        }
        return -1;
    }

    private int getCenterFaceFromColor(int color) {
        for (int i = 0; i < 6; i++) {
            if (CENTER_COLORS[i] == color) return i;
        }
        return -1;
    }

    private void handleSolverResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            showValidationDialog("Solver Error", "The solver returned an empty result.");
            return;
        }

        result = result.trim();

        // Check for error codes
        if (result.startsWith("Error")) {
            String friendlyError;
            if (result.contains("1") || result.contains("-1")) {
                friendlyError = "Incorrect count of colors. Please check that you entered exactly 9 of each color.";
            } else if (result.contains("2") || result.contains("-2")) {
                friendlyError = "Unsolvable configuration: Some edges are invalid or missing.";
            } else if (result.contains("3") || result.contains("-3")) {
                friendlyError = "Unsolvable configuration: One of the edges is flipped/inverted.";
            } else if (result.contains("4") || result.contains("-4")) {
                friendlyError = "Unsolvable configuration: Some corners are invalid or missing.";
            } else if (result.contains("5") || result.contains("-5")) {
                friendlyError = "Unsolvable configuration: One corner piece is twisted.";
            } else if (result.contains("6") || result.contains("-6")) {
                friendlyError = "Unsolvable configuration: Two edges or corners are swapped (parity error). Check mirror reflections.";
            } else if (result.contains("7") || result.contains("-7")) {
                friendlyError = "No solution found. Check that the colors entered match a real Rubik's cube.";
            } else {
                friendlyError = "Invalid cube configuration. Please double check the colors on all faces.";
            }
            showValidationDialog("Invalid Cube State", friendlyError);
            return;
        }

        // Check if already solved
        if (result.equals("0") || result.isEmpty()) {
            showValidationDialog("Already Solved!", "The cube you entered is already in a fully solved state!");
            return;
        }

        // Parse moves
        String[] parsedMoves = result.split("\\s+");
        moves = Arrays.asList(parsedMoves);
        currentStep = 0;

        // Open solve guide screen
        showView(viewSolveGuide);
        updateSolveGuideUI();
    }

    private void showValidationDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    // --- SOLVE GUIDE SCREEN SETUP ---
    private void setupSolveGuideScreen() {
        tvGuideStep = findViewById(R.id.tv_guide_step);
        progressSolveSteps = findViewById(R.id.progress_solve_steps);
        tvGuideMove = findViewById(R.id.tv_guide_move);
        tvGuideDescription = findViewById(R.id.tv_guide_description);
        tvVisualTitle = findViewById(R.id.tv_visual_title);
        imgTurnArrow = findViewById(R.id.img_turn_arrow);
        llFullSequenceContainer = findViewById(R.id.ll_full_sequence_container);
        btnGuidePrev = findViewById(R.id.btn_guide_prev);
        btnGuideNext = findViewById(R.id.btn_guide_next);

        btnGuidePrev.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                updateSolveGuideUI();
            } else {
                // Return to color entry
                showView(viewColorInput);
            }
        });

        btnGuideNext.setOnClickListener(v -> {
            if (currentStep < moves.size() - 1) {
                currentStep++;
                updateSolveGuideUI();
            } else {
                // Completed! Show success dialog and return home
                showSuccessDialog();
            }
        });
    }

    private void updateSolveGuideUI() {
        if (moves.isEmpty()) return;

        int totalSteps = moves.size();
        tvGuideStep.setText("Step " + (currentStep + 1) + " of " + totalSteps);
        
        // Update progress bar
        int progress = (int) (((float)(currentStep + 1) / totalSteps) * 100);
        progressSolveSteps.setProgress(progress);

        String currentMove = moves.get(currentStep);
        tvGuideMove.setText(currentMove);

        // Parse move components
        char face = currentMove.charAt(0);
        char modifier = currentMove.length() > 1 ? currentMove.charAt(1) : ' ';

        String faceName = getFaceFullName(face);
        String direction = "clockwise";
        if (modifier == '\'') {
            direction = "counter-clockwise";
        } else if (modifier == '2') {
            direction = "180 degrees (double turn)";
        }

        tvGuideDescription.setText("Rotate the " + faceName + " face " + direction + " 90 degrees.");
        if (modifier == '2') {
            tvGuideDescription.setText("Rotate the " + faceName + " face " + direction + ".");
        }

        tvVisualTitle.setText("Turn Face: " + faceName.toUpperCase());

        // Update direction arrow graphic
        if (modifier == '\'') {
            imgTurnArrow.setImageResource(R.drawable.ic_arrow_ccw);
        } else {
            imgTurnArrow.setImageResource(R.drawable.ic_arrow_cw); // CW is fallback for single or double turns
        }

        // Update horizontal scroll list of moves
        llFullSequenceContainer.removeAllViews();
        for (int i = 0; i < totalSteps; i++) {
            TextView tv = new TextView(this);
            tv.setText(moves.get(i));
            tv.setTextSize(16);
            tv.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            tv.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            tv.setLayoutParams(lp);

            if (i == currentStep) {
                // Highlight current move
                tv.setTextColor(Color.WHITE);
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.RECTANGLE);
                background.setCornerRadius(dpToPx(8));
                background.setColor(getResources().getColor(R.color.accent_primary));
                tv.setBackground(background);
                tv.setTextSize(18);
            } else {
                tv.setTextColor(getResources().getColor(R.color.text_secondary));
            }
            llFullSequenceContainer.addView(tv);
        }

        // Make button say "Done" on last step
        if (currentStep == totalSteps - 1) {
            btnGuideNext.setText("Done");
        } else {
            btnGuideNext.setText("Next");
        }
    }

    private String getFaceFullName(char face) {
        switch (face) {
            case 'U': return "Up (White)";
            case 'D': return "Down (Yellow)";
            case 'F': return "Front (Green)";
            case 'B': return "Back (Blue)";
            case 'L': return "Left (Orange)";
            case 'R': return "Right (Red)";
            default: return "Unknown";
        }
    }

    private void showSuccessDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Solved!")
                .setMessage("Congratulations! Your Rubik's cube should now be solved!")
                .setPositiveButton("Back to Home", (dialog, which) -> showView(viewHome))
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
