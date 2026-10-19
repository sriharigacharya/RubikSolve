package com.example.rubikssolve.cube3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Java Rubik's cube model: 27 cubies, each with integer grid position
 * and a list of coloured stickers.  No Android imports.
 *
 * Facelet order follows Kociemba: U R F D L B (indices 0-5), 9 cells each,
 * row-major as seen looking directly at that face (matches cubeColors[][] in
 * MainActivity).
 *
 * Hand-check (R move, axis=0, layer=+1, dir=-1):
 *   UFR (1,1,1) → rot s=-1 → (1, 1,-1) = UBR  ✓
 *   U sticker normal (0,1,0) → (0,0,-1) = B direction ✓
 */
public class CubeModel {

    // Face indices in Kociemba order
    public static final int U = 0, R = 1, F = 2, D = 3, L = 4, B = 5;

    /**
     * Outward face normals in Kociemba order.
     * NORMALS[U] = (0,+1,0), NORMALS[R] = (+1,0,0), NORMALS[F] = (0,0,+1),
     * NORMALS[D] = (0,-1,0), NORMALS[L] = (-1,0,0), NORMALS[B] = (0,0,-1).
     */
    public static final int[][] NORMALS = {
        { 0,  1,  0},   // U
        { 1,  0,  0},   // R
        { 0,  0,  1},   // F
        { 0, -1,  0},   // D
        {-1,  0,  0},   // L
        { 0,  0, -1},   // B
    };

    /** A single coloured sticker: outward normal direction + ARGB colour. */
    public static class Sticker {
        public int[] n;       // outward normal, integer (0/±1 components)
        public int   color;   // ARGB int, e.g. 0xFFFFFFFF

        public Sticker(int[] n, int color) {
            this.n     = n.clone();
            this.color = color;
        }
    }

    /** One of the 27 small cubes. */
    public static class Cubie {
        public int[]        pos;      // grid position, each component in {-1,0,+1}
        public List<Sticker> stickers = new ArrayList<>(3);

        public Cubie(int[] pos) {
            this.pos = pos.clone();
        }
    }

    private final List<Cubie> cubies = new ArrayList<>(27);

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Build a CubeModel from a 6×9 facelet colour array (same layout as
     * MainActivity.cubeColors: U=0, R=1, F=2, D=3, L=4, B=5, row-major).
     */
    public CubeModel(int[][] facelets) {
        // Create 27 cubies at integer grid positions
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    cubies.add(new Cubie(new int[]{x, y, z}));

        // Assign stickers from facelets
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < 9; i++) {
                int row = i / 3, col = i % 3;
                int[] pos    = facePos(f, row, col);
                Cubie cubie  = cubieAt(pos);
                cubie.stickers.add(new Sticker(NORMALS[f], facelets[f][i]));
            }
        }
    }

    /**
     * Factory: create a solved cube where every cell on a face has the given
     * per-face colour (e.g. CENTER_COLORS from MainActivity).
     */
    public static CubeModel createSolved(int[] centerColors) {
        int[][] facelets = new int[6][9];
        for (int f = 0; f < 6; f++) Arrays.fill(facelets[f], centerColors[f]);
        return new CubeModel(facelets);
    }

    /**
     * Factory for unit tests: each face gets colour = face index (0-5).
     * Colours are distinct so identity tests are meaningful.
     */
    public static CubeModel createSolvedTest() {
        int[][] facelets = new int[6][9];
        for (int f = 0; f < 6; f++) Arrays.fill(facelets[f], f);
        return new CubeModel(facelets);
    }

    // -------------------------------------------------------------------------
    // Facelet position mapping (Kociemba layout)
    //
    // U face (y=+1): row 0 = back (z=-1), row 2 = front (z=+1),
    //                col 0 = left (x=-1), col 2 = right (x=+1)
    // R face (x=+1): row 0 = top  (y=+1), col 0 = front (z=+1)
    // F face (z=+1): row 0 = top  (y=+1), col 0 = left  (x=-1)
    // D face (y=-1): row 0 = front(z=+1), col 0 = left  (x=-1)
    // L face (x=-1): row 0 = top  (y=+1), col 0 = back  (z=-1)
    // B face (z=-1): row 0 = top  (y=+1), col 0 = right (x=+1)
    // -------------------------------------------------------------------------

    /** 3-D grid position of facelet (row, col) on face f. */
    static int[] facePos(int f, int row, int col) {
        switch (f) {
            case U: return new int[]{ col - 1,  1,       row - 1};
            case R: return new int[]{ 1,        1 - row, 1 - col};
            case F: return new int[]{ col - 1,  1 - row, 1      };
            case D: return new int[]{ col - 1, -1,       1 - row};
            case L: return new int[]{-1,        1 - row, col - 1};
            default: // B
                   return new int[]{ 1 - col,   1 - row, -1     };
        }
    }

    // -------------------------------------------------------------------------
    // Integer rotation (no floating-point, no drift)
    //
    // rot(v, axis, s) rotates integer vector v by s × 90° around axis.
    // s = +1 → +90° (right-hand rule); s = -1 → -90°.
    //
    // X (axis=0): y → -s*z, z →  s*y
    // Y (axis=1): x →  s*z, z → -s*x
    // Z (axis=2): x → -s*y, y →  s*x
    //
    // Verification (R move: axis=0, s=-1):
    //   UFR (1,1,1) → (1, z=1, -y=-1) ... wait:
    //   y_new = -s*z = -(-1)*1 = 1, z_new = s*y = -1*1 = -1 → (1,1,-1) = UBR ✓
    // -------------------------------------------------------------------------

    static int[] rot(int[] v, int axis, int s) {
        int x = v[0], y = v[1], z = v[2];
        switch (axis) {
            case 0: return new int[]{ x,       -s * z,  s * y};  // X
            case 1: return new int[]{ s * z,    y,      -s * x}; // Y
            default:return new int[]{-s * y,    s * x,   z    }; // Z
        }
    }

    // -------------------------------------------------------------------------
    // Move application
    // -------------------------------------------------------------------------

    /**
     * Apply a move string (e.g. "R", "U'", "F2") to the model.
     * Positions and sticker normals are updated with exact integer arithmetic.
     */
    public void applyMove(String moveStr) {
        Move mv   = Move.parse(moveStr);
        int  sign = Integer.signum(mv.quarterTurns);
        int  steps = Math.abs(mv.quarterTurns);

        for (Cubie c : cubies) {
            if (c.pos[mv.axis] != mv.layer) continue;
            for (int k = 0; k < steps; k++) {
                c.pos = rot(c.pos, mv.axis, sign);
                for (Sticker s : c.stickers) s.n = rot(s.n, mv.axis, sign);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Export / query
    // -------------------------------------------------------------------------

    /**
     * Export current state as a 6×9 facelet colour array (inverse of constructor).
     * Useful for round-trip tests and for the Kociemba solver interface.
     */
    public int[][] toFacelets() {
        int[][] out = new int[6][9];
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < 9; i++) {
                int row = i / 3, col = i % 3;
                int[] pos  = facePos(f, row, col);
                Cubie c    = cubieAt(pos);
                out[f][i]  = stickerColor(c, NORMALS[f]);
            }
        }
        return out;
    }

    /** True if this cubie is in the layer that move mv rotates. */
    public boolean affects(Move mv, Cubie c) {
        return c.pos[mv.axis] == mv.layer;
    }

    /** All 27 cubies (unmodifiable view not needed; renderer reads only). */
    public List<Cubie> getCubies() { return cubies; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Cubie cubieAt(int[] p) {
        for (Cubie c : cubies) {
            if (c.pos[0] == p[0] && c.pos[1] == p[1] && c.pos[2] == p[2]) return c;
        }
        throw new IllegalArgumentException(
                "No cubie at (" + p[0] + "," + p[1] + "," + p[2] + ")");
    }

    private int stickerColor(Cubie c, int[] normal) {
        for (Sticker s : c.stickers) {
            if (s.n[0] == normal[0] && s.n[1] == normal[1] && s.n[2] == normal[2])
                return s.color;
        }
        return 0; // interior face — no sticker
    }
}
