package com.example.rubikssolve.cube3d;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

public class CubeModelTest {

    private boolean isSolved(CubeModel m, int[][] initialFacelets) {
        int[][] current = m.toFacelets();
        for (int f = 0; f < 6; f++) {
            if (!Arrays.equals(initialFacelets[f], current[f])) {
                return false;
            }
        }
        return true;
    }

    private int[][] solvedTestFacelets() {
        int[][] f = new int[6][9];
        for (int i = 0; i < 6; i++) {
            Arrays.fill(f[i], i + 1); // 1, 2, 3, 4, 5, 6
        }
        return f;
    }

    @Test
    public void faceletsRoundTrip() {
        int[][] f = solvedTestFacelets();
        CubeModel model = new CubeModel(f);
        int[][] exported = model.toFacelets();
        for (int face = 0; face < 6; face++) {
            assertArrayEquals("Face " + face + " mismatch", f[face], exported[face]);
        }
    }

    @Test
    public void fourTimesIsIdentity() {
        // R R R R should return to identity
        String[] moves = {"R", "L", "U", "D", "F", "B"};
        for (String m : moves) {
            int[][] f = solvedTestFacelets();
            CubeModel model = new CubeModel(f);
            for (int i = 0; i < 4; i++) {
                model.applyMove(m);
            }
            assertTrue("4x " + m + " should be identity", isSolved(model, f));
        }
    }

    @Test
    public void moveThenInverseIsIdentity() {
        String[] moves = {"R", "L", "U", "D", "F", "B", "R'", "U2", "F2"};
        for (String m : moves) {
            int[][] f = solvedTestFacelets();
            CubeModel model = new CubeModel(f);
            model.applyMove(m);
            model.applyMove(Move.invert(m));
            assertTrue(m + " then " + Move.invert(m) + " should be identity", isSolved(model, f));
        }
    }

    @Test
    public void sexyMoveSixTimesIsIdentity() {
        // (R U R' U') x 6 is identity
        int[][] f = solvedTestFacelets();
        CubeModel model = new CubeModel(f);
        for (int i = 0; i < 6; i++) {
            model.applyMove("R");
            model.applyMove("U");
            model.applyMove("R'");
            model.applyMove("U'");
        }
        assertTrue("(R U R' U') x 6 should be identity", isSolved(model, f));
    }

    @Test
    public void doubleMoveEqualsTwoSingles() {
        String[] single = {"R", "L", "U", "D", "F", "B"};
        for (String m : single) {
            int[][] f1 = solvedTestFacelets();
            CubeModel m1 = new CubeModel(f1);
            m1.applyMove(m + "2");

            int[][] f2 = solvedTestFacelets();
            CubeModel m2 = new CubeModel(f2);
            m2.applyMove(m);
            m2.applyMove(m);

            assertArrayEquals(m1.toFacelets(), m2.toFacelets());
        }
    }
}
