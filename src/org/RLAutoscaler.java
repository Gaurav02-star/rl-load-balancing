package org;

import java.util.*;

/**
 * RLAutoscaler.java
 *
 * Complete Q(λ) Autoscaler implementation featuring:
 *   - Action masking in chooseAction() to prevent selecting structurally infeasible actions
 *   - Policy-level credit assignment (recording desiredActionIdx for Q-table updates)
 *   - Full eligibility trace updates across all traced (state, action) pairs
 *   - Deterministic per-episode progress epsilon decay
 *   - Compacted 36-state encoding (CPU x Queue x Trend x VM)
 *   - Diagnostic instrumentation for tracking chosen vs. executed actions & state safety warnings
 */
public class RLAutoscaler implements Autoscaler {

    // ── Hyperparameters ───────────────────────────────────────────────────────

    private final double alpha  = 0.25;
    private final double gamma  = 0.90;
    private final double lambda = 0.70;

    private double epsilon = 1.0;

    // Action indices: 0 = SCALE_UP, 1 = NO_OP, 2 = SCALE_DOWN
    private static final int ACTION_SCALE_UP   = 0;
    private static final int ACTION_NO_OP      = 1;
    private static final int ACTION_SCALE_DOWN = 2;
    private static final int NUM_ACTIONS       = 3;

    // ── Core learning structures (LinkedHashMap for deterministic ordering) ───

    private final Map<String, double[]> qTable = new LinkedHashMap<>();
    private final Map<String, double[]> eTrace = new LinkedHashMap<>();
    private final Random random = new Random(42);

    // ── Credit-assignment bookkeeping ────────────────────────────────────────

    private String lastState        = null;
    private int    lastActionIdx    = ACTION_NO_OP;
    private double lastQueueLength   = 0.0;


    // ── Instrumentation & Diagnostics ────────────────────────────────────────

    private int scaleUpExecuted      = 0;
    private int scaleUpBlockedByGate = 0;
    private int scaleDownExecuted    = 0;
    private int noOpExecuted         = 0;

    // Detailed tracking: [SCALE_UP, NO_OP, SCALE_DOWN] -> {ChosenCount, ExecutedCount}
    private final Map<String, long[]> actionAttemptCounts  = new LinkedHashMap<>();
    private final Map<String, long[]> actionExecutedCounts = new LinkedHashMap<>();

    private boolean evalLoggingEnabled = false;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called at the start of each training episode.
     * Resets episode state boundaries, flushes traces, and updates epsilon.
     */
    public void startEpisode(int episode, int totalEpisodes) {
        // Explicit double cast prevents Java integer division truncation
        this.epsilon = Math.max(0.05, 1.0 - (double) (episode - 1) / totalEpisodes);
        this.lastState = null;
        this.lastActionIdx = ACTION_NO_OP;
        this.lastQueueLength = 0.0;
        this.eTrace.clear();
    }

    public void setEvalLogging(boolean enabled) {
        this.evalLoggingEnabled = enabled;
    }

    @Override
    public AutoscalerAction evaluateScaling(ClusterState state) {
        if (state == null) return AutoscalerAction.NO_OP;

        int    activeVms    = state.getActiveVmCount();
        double currentQueue = state.getAverageQueueLength();

        // ── Deterministic idle floor (hard safety rule, not learned) ────────
        if (currentQueue == 0.0 && state.getAverageCpuUtilisation() == 0.0) {
            if (epsilon > 0.0 && lastState != null) {
                double reward = calculateReward(state);
                settleUpdate(lastState, lastActionIdx, reward, null, ACTION_NO_OP);
            }
            lastState = null;
            lastQueueLength = currentQueue;

            if (evalLoggingEnabled) {
                System.out.printf("  [EVAL TICK @ t=%.1fs] State: [IDLE_FLOOR] -> Action: SCALE_DOWN (Hard Rule)%n", state.getTime());
            }

            if (activeVms > SimulationConfig.MIN_VMS) {
                scaleDownExecuted++;
                return AutoscalerAction.SCALE_DOWN;
            }
            noOpExecuted++;
            return AutoscalerAction.NO_OP;
        }

        // ── Normal (learned) decision path ──────────────────────────────────
        String currentState = encodeState(state);

        // FIX 1: Pass activeVms into chooseAction to apply strict action masking
        int desiredActionIdx = chooseAction(currentState, activeVms);

        // Record policy decision attempts for diagnostic instrumentation
        actionAttemptCounts.putIfAbsent(currentState, new long[NUM_ACTIONS]);
        actionAttemptCounts.get(currentState)[desiredActionIdx]++;

        if (epsilon > 0.0 && lastState != null) {
            double reward = calculateReward(state);
            settleUpdate(lastState, lastActionIdx, reward, currentState, desiredActionIdx);
        }

        int executedActionIdx;
        AutoscalerAction executedAction;

        if (desiredActionIdx == ACTION_SCALE_UP) {
            if (activeVms < SimulationConfig.MAX_VMS) {
                executedActionIdx = ACTION_SCALE_UP;
                executedAction    = AutoscalerAction.SCALE_UP;
                scaleUpExecuted++;
            } else {
                executedActionIdx = ACTION_NO_OP;
                executedAction    = AutoscalerAction.NO_OP;
                scaleUpBlockedByGate++;
                noOpExecuted++;
            }
        } else if (desiredActionIdx == ACTION_SCALE_DOWN) {
            if (activeVms > SimulationConfig.MIN_VMS && currentQueue == 0.0) {
                executedActionIdx = ACTION_SCALE_DOWN;
                executedAction    = AutoscalerAction.SCALE_DOWN;
                scaleDownExecuted++;
            } else {
                executedActionIdx = ACTION_NO_OP;
                executedAction    = AutoscalerAction.NO_OP;
                noOpExecuted++;
            }
        } else {
            executedActionIdx = ACTION_NO_OP;
            executedAction    = AutoscalerAction.NO_OP;
            noOpExecuted++;
        }

        // Record executed actions for diagnostic instrumentation
        actionExecutedCounts.putIfAbsent(currentState, new long[NUM_ACTIONS]);
        actionExecutedCounts.get(currentState)[executedActionIdx]++;

        if (evalLoggingEnabled) {
            double[] qVals = qTable.getOrDefault(currentState, new double[NUM_ACTIONS]);
            System.out.printf("  [EVAL TICK @ t=%.1fs] State: %s | Q[SU=%.2f, NOP=%.2f, SD=%.2f] -> Executed: %s%n",
                    state.getTime(), currentState, qVals[0], qVals[1], qVals[2], executedAction);
        }

        boolean gateOverrode = (executedActionIdx != desiredActionIdx);

        // FIX 2: Store desiredActionIdx as lastActionIdx so Q-updates reflect the policy's intent.
        // DESIGN DECISION: The reward calculation uses the real cluster state resulting from the executed step
        // without adding an arbitrary extra penalty score. This naturally depresses the Q-value of blocked actions
        // relative to valid alternatives without corrupting TD-error scale.
        lastState        = currentState;
        lastActionIdx     = desiredActionIdx;
        lastQueueLength   = currentQueue;

        return executedAction;
    }

    // =========================================================================
    //  Full Q(λ) Eligibility Trace Update Engine
    // =========================================================================

    private void settleUpdate(String state, int actionIdx, double reward,
                              String nextState, int nextActionIdx) {

        qTable.putIfAbsent(state, new double[NUM_ACTIONS]);
        double qOld = qTable.get(state)[actionIdx];

        double nextQ = 0.0;
        if (nextState != null) {
            qTable.putIfAbsent(nextState, new double[NUM_ACTIONS]);
            nextQ = qTable.get(nextState)[nextActionIdx];
        }

        double tdError = reward + gamma * nextQ -  qOld;

        eTrace.putIfAbsent(state, new double[NUM_ACTIONS]);
        eTrace.get(state)[actionIdx] += 1.0;

        for (Map.Entry<String, double[]> entry : eTrace.entrySet()) {
            double[] traces = entry.getValue();
            double[] qVals  = qTable.computeIfAbsent(entry.getKey(), k -> new double[NUM_ACTIONS]);
            for (int a = 0; a < NUM_ACTIONS; a++) {
                if (traces[a] > 1e-6) {
                    qVals[a]  += alpha * tdError * traces[a];
                    traces[a] *= gamma * lambda;
                }
            }
        }

        if (nextState == null) {
            eTrace.clear();
        }
    }

    // =========================================================================
    //  State Encoding
    // =========================================================================

    private String encodeState(ClusterState state) {
        double cpu   = state.getAverageCpuUtilisation();
        double queue = state.getAverageQueueLength();
        double predictedArrival = state.getPredictedArrivalRate();
        double deltaQueue = queue - lastQueueLength;
        char trendTier = deltaQueue > 0.05 ? 'R' : 'N';

        char cpuTier   = cpu > 0.50 ? 'H' : 'L';
        char queueTier = queue > 0.1 ? 'H' : (queue > 0.0 ? 'M' : 'Z');


        char predictionTier;

        if (predictedArrival < 2.0) {
            predictionTier = 'L';
        } else if (predictedArrival < 5.0) {
            predictionTier = 'M';
        } else {
            predictionTier = 'H';
        }
        char vmTier    = tierVmCount(state.getActiveVmCount());

        return "C" + cpuTier + "Q" + queueTier + "T" + trendTier + "P" + predictionTier + "V" + vmTier;
    }

    private char tierVmCount(int activeVms) {
        int min = SimulationConfig.MIN_VMS;
        int max = SimulationConfig.MAX_VMS;
        if (max <= min) return 'M';

        double position = (double) (activeVms - min) / (max - min);
        return position < 0.34 ? 'L' : (position > 0.66 ? 'H' : 'M');
    }

    // =========================================================================
    //  Reward Function
    // =========================================================================

    private double calculateReward(ClusterState state) {
        double reward = 0.0;
        double currentQueue = state.getAverageQueueLength();
        double deltaQueue = currentQueue - lastQueueLength;

        if (deltaQueue > 0.0) {
            reward -= deltaQueue * 150.0;
        }

        reward -= state.getAverageResponseTime() * 80.0;
        reward -= state.getSlaViolationRate() * 250.0;
        reward -= currentQueue * 40.0;

        if (currentQueue == 0.0 && state.getAverageCpuUtilisation() < 0.10) {
            reward += 10.0;
        }
        return reward;
    }

    // =========================================================================
    //  Action Selection with Action Masking (FIX 1)
    // =========================================================================

    private boolean lastChoiceWasExploratory = false;

    /**
     * Chooses an action applying strict action masking so infeasible actions
     * are never selected during exploration or greedy exploitation.
     */
    private int chooseAction(String state, int activeVms) {
        qTable.putIfAbsent(state, new double[NUM_ACTIONS]);

        // Build list of structurally feasible actions given active VM count
        List<Integer> feasibleActions = new ArrayList<>();

        if (activeVms < SimulationConfig.MAX_VMS) {
            feasibleActions.add(ACTION_SCALE_UP);
        }

        feasibleActions.add(ACTION_NO_OP); // NO_OP is always feasible

        if (activeVms > SimulationConfig.MIN_VMS) {
            feasibleActions.add(ACTION_SCALE_DOWN);
        }

        if (random.nextDouble() < epsilon) {
            lastChoiceWasExploratory = true;
            // Uniform selection across ONLY feasible actions
            int randomIndex = random.nextInt(feasibleActions.size());
            return feasibleActions.get(randomIndex);
        }

        lastChoiceWasExploratory = false;
        double[] q = qTable.get(state);

        // Argmax evaluated exclusively over feasible actions
        int bestAction = feasibleActions.get(0);
        double maxQ = q[bestAction];

        for (int i = 1; i < feasibleActions.size(); i++) {
            int actionCandidate = feasibleActions.get(i);
            if (q[actionCandidate] > maxQ) {
                maxQ = q[actionCandidate];
                bestAction = actionCandidate;
            }
        }

        return bestAction;
    }

    private double max(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v > m) m = v;
        return m;
    }

    public void printFinalQTable() {
        System.out.println("\n==========================================================================");
        System.out.println("   FINAL TRAINED Q-TABLE (State -> [SCALE_UP, NO_OP, SCALE_DOWN])");
        System.out.println("==========================================================================");
        if (qTable.isEmpty()) {
            System.out.println("  [EMPTY Q-TABLE]");
        } else {
            for (Map.Entry<String, double[]> entry : qTable.entrySet()) {
                String state = entry.getKey();
                double[] q = entry.getValue();

                long[] attempts = actionAttemptCounts.getOrDefault(state, new long[NUM_ACTIONS]);
                long[] executed = actionExecutedCounts.getOrDefault(state, new long[NUM_ACTIONS]);

                int bestUnmasked = (q[0] > q[1] && q[0] > q[2]) ? ACTION_SCALE_UP :
                        (q[1] >= q[0] && q[1] >= q[2]) ? ACTION_NO_OP : ACTION_SCALE_DOWN;

                String bestStr = (bestUnmasked == ACTION_SCALE_UP) ? "SCALE_UP" :
                        (bestUnmasked == ACTION_NO_OP) ? "NO_OP" : "SCALE_DOWN";

                // Flag if unmasked argmax action is structurally impossible given VM tier
                boolean isInfeasibleWarning = false;
                if (state.endsWith("VL") && bestUnmasked == ACTION_SCALE_DOWN) {
                    isInfeasibleWarning = true;
                } else if (state.endsWith("VH") && bestUnmasked == ACTION_SCALE_UP) {
                    isInfeasibleWarning = true;
                }

                String warningStr = isInfeasibleWarning ? "  <-- [WARNING: INFEASIBLE ARGMAX]" : "";

                System.out.printf("  State %-10s | Q: [SU: %8.2f, NOP: %8.2f, SD: %8.2f] -> Best: %-10s%s%n",
                        state, q[0], q[1], q[2], bestStr, warningStr);
                System.out.printf("             Chosen: [SU: %5d, NOP: %5d, SD: %5d] | Executed: [SU: %5d, NOP: %5d, SD: %5d]%n",
                        attempts[0], attempts[1], attempts[2], executed[0], executed[1], executed[2]);
            }
        }
        System.out.println("==========================================================================\n");
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public int getQTableSize() {
        return qTable.size();
    }

    public int getScaleUpExecuted() {
        return scaleUpExecuted;
    }

    public int getScaleUpBlockedByGate() {
        return scaleUpBlockedByGate;
    }

    public int getScaleDownExecuted() {
        return scaleDownExecuted;
    }

    public int getNoOpExecuted() {
        return noOpExecuted;
    }
}