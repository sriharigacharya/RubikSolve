# 🧊 Rubik's Solver

A native Android app that **scans a scrambled Rubik's Cube with your phone camera** and instantly computes a step-by-step solution using Kociemba's Two-Phase Algorithm — no internet required.

---

## ✨ Features

- **Camera Scan** — Point your phone at each face of the cube; a live overlay guides you through all 6 faces.
- **Manual Color Input** — Alternatively, tap each of the 54 facelets to enter the cube state by hand.
- **Orientation Guide** — On-screen cues show exactly which color should face up/left/right before each scan, eliminating orientation mistakes.
- **Auto-Advance** — After each face is captured the app automatically moves to the next face, so the workflow is seamless.
- **Near-Optimal Solution** — Kociemba's algorithm guarantees a solution in **≤ 20 moves** and computes it in milliseconds on-device.
- **Step-by-Step Move Guide** — Each move is displayed as a card with a clear notation label and a direction icon (clockwise / counter-clockwise).
- **Move Notation Guide** — Built-in reference screen that explains standard cube notation (U, D, R, L, F, B and their primes/doubles) for beginners.
- **Fully Offline** — The solver runs entirely on-device; no network calls, no API keys, no data sent anywhere.
- **Lightweight** — Color detection uses pure Android `android.graphics` math; no OpenCV or other native bloat.

---

## 📱 Screenshots

| Home | Menu | Camera Scan |
|:---:|:---:|:---:|
| ![Home](Screenshots/WhatsApp%20Image%202026-09-30%20at%204.13.34%20PM.jpeg) | ![Menu](Screenshots/WhatsApp%20Image%202026-09-30%20at%204.13.34%20PM%20(1).jpeg) | ![Camera Scan](Screenshots/WhatsApp%20Image%202026-09-30%20at%204.13.33%20PM.jpeg) |

| Manual Input | Solution Step | Notation Guide |
|:---:|:---:|:---:|
| ![Manual Input](Screenshots/WhatsApp%20Image%202026-09-30%20at%204.13.33%20PM%20(1).jpeg) | ![Solution](Screenshots/WhatsApp%20Image%202026-09-30%20at%204.13.32%20PM.jpeg) | ![Notation Guide](Screenshots/WhatsApp%20Image%202026-09-30%20at%204.13.32%20PM%20(1).jpeg) |

---

## 🏗️ Architecture

The app is a single-`Activity` Android project (`MainActivity`) that inflates different layout panels as the user moves through the workflow.

```
com.example.rubikssolve/
├── MainActivity.java              # Root activity; orchestrates all panels & state
├── camera/
│   ├── CameraScanController.java  # CameraX lifecycle management & frame routing
│   ├── CubeColorClassifier.java   # HSV-based color detection
│   └── ScanOverlayView.java       # Live 3×3 grid overlay drawn on the preview
└── solver/
    ├── Search.java                # Kociemba two-phase search
    ├── CoordCube.java             # Coordinate-level cube representation
    ├── CubieCube.java             # Cubie-level cube representation & move tables
    ├── Tools.java                 # Face-string parsing & cube validation
    └── Util.java                  # Shared math & lookup-table utilities
```

### Three-Layer Pipeline

```
Camera Frame
     │
     ▼
┌─────────────────────────────┐
│  Camera / UI Layer          │  CameraX preview + ScanOverlayView
└───────────────┬─────────────┘
                │ Bitmap (3×3 grid crop)
                ▼
┌─────────────────────────────┐
│  Vision / Processing Layer  │  CubeColorClassifier — HSV weighted distance
└───────────────┬─────────────┘
                │ 54-character face string
                ▼
┌─────────────────────────────┐
│  Solver Layer               │  Kociemba Two-Phase Algorithm
└───────────────┬─────────────┘
                │ Move sequence  (e.g. "R U R' U' R' F R2 U'...")
                ▼
           Move-card list shown to user
```

---

## 🔬 How Color Detection Works

`CubeColorClassifier` avoids the pitfalls of raw RGB comparison:

1. **Inner 40% sampling** — Only the centre 30%–70% of each cell is sampled, skipping black sticker borders and edge glare.
2. **Pixel averaging** — Every other pixel (stride 2) in the sample region is averaged for a fast, noise-resistant colour estimate.
3. **RGB → HSV conversion** — Uses `android.graphics.Color.RGBToHSV` to separate colour (Hue) from lighting (Value).
4. **Weighted Euclidean distance** — Hue carries **2×** weight, Saturation **1×**, Value **0.5×**, so shadows and glare don't flip a Red sticker to Brown.
5. **White fast-path** — Pixels with saturation < 0.20 and value > 0.60 are immediately classified as White, preventing low-saturation colours from polluting the distance comparison.

---

## ⚙️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java |
| Min SDK | Android 5.0 (API 21) |
| Camera | AndroidX CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) |
| UI | ConstraintLayout · Material Components |
| Solver | Kociemba Two-Phase Algorithm (pure Java, ~1 MB lookup tables) |
| Build | Gradle (Kotlin DSL) |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio **Hedgehog** (2023.1.1) or newer
- Android SDK with API 21+
- A physical Android device **or** an emulator with camera support

### Build & Run

```bash
# Clone the repository
git clone https://github.com/<your-username>/RubiksSolve.git
cd RubiksSolve

# Open in Android Studio and click Run, or build from the command line:
./gradlew assembleDebug
```

Then install the APK on your device:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Permissions

The app requests **CAMERA** permission at runtime for the face-scan feature. It can be installed on camera-less devices; manual color input remains fully functional.

---

## 📖 How to Use

1. **Open the app** and tap **Solve Cube**.
2. Choose **Camera Scan** or **Manual Input**.

### Camera Scan

1. Follow the on-screen orientation guide (the required colours for top/left/right are shown as coloured squares).
2. Hold the cube steady inside the 3×3 grid overlay.
3. Tap the **Capture** button for each of the 6 faces. The app auto-advances to the next face after each capture.
4. Once all 6 faces are scanned, tap **Solve** to compute the solution.

### Manual Input

1. Tap each of the 54 facelets on the unfolded cube diagram and select its colour.
2. Tap **Solve** when all facelets are filled.

### Solution Screen

- Swipe through the move cards one by one.
- Each card shows the **move notation** (e.g. `R'`) and a direction icon.
- Tap the **ℹ Notation Guide** button at any time for a beginner-friendly explanation of each move.

---

## 🗺️ Roadmap

- [ ] Kotlin migration for UI & camera layers
- [ ] TensorFlow Lite colour segmentation for better accuracy under harsh lighting
- [ ] Animated 3-D cube visualisation for each solution step
- [ ] Support for 2×2 and 4×4 cubes

---

## 📄 License

This project is licensed under the **MIT License**. See [`LICENSE`](LICENSE) for details.

The Kociemba solver implementation is based on the open-source Java port by [Herbert Kociemba](http://kociemba.org/cube.htm).
