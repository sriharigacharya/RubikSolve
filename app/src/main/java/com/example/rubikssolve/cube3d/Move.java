package com.example.rubikssolve.cube3d;

/**
 * Represents a single Rubik's cube move as axis + layer + signed quarter-turns.
 *
 * Coordinate system: x = right, y = up, z = toward front viewer.
 * Positive quarter-turn direction per axis is the right-hand rule.
 *
 * Direction table (verified against WCA standard notation):
 *   U  axis=1 layer=+1 dir=+1  → UFR→UBR (CW from above)
 *   D  axis=1 layer=-1 dir=+1  → DFR→DBR (CW from below)
 *   R  axis=0 layer=+1 dir=-1  → UFR→UBR (CW from right)
 *   L  axis=0 layer=-1 dir=+1  → UFL→UBL (CW from left)
 *   F  axis=2 layer=+1 dir=-1  → UFL→UFR (CW from front)
 *   B  axis=2 layer=-1 dir=+1  → UBR→UBL (CW from back)
 */
public final class Move {

    public final int axis;          // 0 = X, 1 = Y, 2 = Z
    public final int layer;         // +1 or -1
    public final int quarterTurns;  // signed: +1 = +90°, -1 = -90°, ±2 = ±180°

    private Move(int axis, int layer, int quarterTurns) {
        this.axis = axis;
        this.layer = layer;
        this.quarterTurns = quarterTurns;
    }

    /** Parse a standard Kociemba move string such as "R", "U'", "F2". */
    public static Move parse(String m) {
        if (m == null || m.isEmpty()) throw new IllegalArgumentException("Empty move");
        int axis, layer, dir;
        switch (m.charAt(0)) {
            case 'U': axis = 1; layer =  1; dir = -1; break;
            case 'D': axis = 1; layer = -1; dir =  1; break;
            case 'R': axis = 0; layer =  1; dir = -1; break;
            case 'L': axis = 0; layer = -1; dir =  1; break;
            case 'F': axis = 2; layer =  1; dir = -1; break;
            case 'B': axis = 2; layer = -1; dir =  1; break;
            default:  throw new IllegalArgumentException("Unknown face: " + m);
        }
        if (m.endsWith("'")) dir = -dir;
        int turns = m.endsWith("2") ? 2 : 1;
        return new Move(axis, layer, dir * turns);
    }

    /**
     * Returns the inverse move string.
     * R2 → R2, R → R', R' → R.
     */
    public static String invert(String m) {
        if (m == null || m.isEmpty()) throw new IllegalArgumentException("Empty move");
        if (m.endsWith("2")) return m;                   // 180° is self-inverse
        if (m.endsWith("'")) return m.substring(0, 1);  // R' → R
        return m + "'";                                  // R  → R'
    }
}
