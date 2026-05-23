package com.yourname.backtrack.client;

/**
 * Lightweight key-search: when client forward/strafe are unreliable
 * (modules overriding keys, lag), test alternatives and pick the
 * combination whose predicted motion minimises distance to actual motion.
 */
public final class InputKeySearcher {

    private static final int[][] KEY_COMBOS = {
            {1,0}, {0,0}, {1,-1}, {1,1}, {0,-1}, {0,1}, {-1,-1}, {-1,0}, {-1,1}
    };

    /** Maximum error to accept a key combination as "found". */
    private static final double MAX_ERROR = 0.008;

    /**
     * Searches forward/strafe combinations and returns the best match.
     * If the current input is good enough, returns null (no override needed).
     */
    public static int[] search(MovementSimState s, MovementPhysicsEngine physics,
                               double actualX, double actualZ) {
        double bestError = Double.MAX_VALUE;
        int bestFwd = s.forwardKey, bestStr = s.strafeKey;

        // Test current keys first
        double[] pred = physics.simulateOneTick(s, s.forwardKey, s.strafeKey);
        double err = error(pred[0], pred[2], actualX, actualZ);
        if (err < MAX_ERROR) return null; // current input is fine

        // Search all combos
        for (int[] combo : KEY_COMBOS) {
            double[] pred2 = physics.simulateOneTick(s, combo[0], combo[1]);
            double err2 = error(pred2[0], pred2[2], actualX, actualZ);
            if (err2 < bestError) {
                bestError = err2;
                bestFwd = combo[0];
                bestStr = combo[1];
            }
        }

        // Only override if significantly better
        if (bestError < err - 0.005) {
            return new int[]{bestFwd, bestStr};
        }
        return null;
    }

    private static double error(double px, double pz, double ax, double az) {
        double dx = px - ax, dz = pz - az;
        return Math.sqrt(dx*dx + dz*dz);
    }
}