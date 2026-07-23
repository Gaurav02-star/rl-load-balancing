package org;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * RLAutoscaler.java
 * Reinforcement Learning (Q-Learning) Autoscaler operating on macro ClusterState telemetry.
 */
public class RLAutoscaler implements Autoscaler {

    private final Map<String, double[]> qTable = new HashMap<>();
    private final Random random = new Random(42);

    private double alpha = 0.2;
    private double gamma = 0.9;
    private double epsilon = 0.2;

    private String lastState = null;
    private int lastActionIdx = -1;

    @Override
    public AutoscalerAction evaluateScaling(ClusterState state) {
        if (state == null) return AutoscalerAction.NO_OP;

        String stateKey = encodeState(state);
        int actionIdx;

        if (random.nextDouble() < epsilon) {
            actionIdx = random.nextInt(3);
        } else {
            actionIdx = getBestAction(stateKey);
        }

        if (lastState != null && lastActionIdx != -1) {
            double reward = calculateReward(state);
            qTable.putIfAbsent(lastState, new double[3]);
            qTable.putIfAbsent(stateKey, new double[3]);

            double qOld = qTable.get(lastState)[lastActionIdx];
            double maxQNext = getMaxQ(stateKey);

            double qNew = qOld + alpha * (reward + gamma * maxQNext - qOld);
            qTable.get(lastState)[lastActionIdx] = qNew;
        }

        lastState = stateKey;
        lastActionIdx = actionIdx;

        AutoscalerAction action = AutoscalerAction.values()[actionIdx];

        // Guard boundary limits strictly based on current active VM count
        if (action == AutoscalerAction.SCALE_UP && state.getActiveVmCount() >= SimulationConfig.MAX_VMS) {
            return AutoscalerAction.NO_OP;
        }
        if (action == AutoscalerAction.SCALE_DOWN && state.getActiveVmCount() <= SimulationConfig.MIN_VMS) {
            return AutoscalerAction.NO_OP;
        }

        return action;
    }

    private String encodeState(ClusterState s) {
        char cpuBucket = s.getAvgCpuUtilisation() < 0.3 ? 'L' : s.getAvgCpuUtilisation() > 0.7 ? 'H' : 'M';
        char queueBucket = s.getAvgQueueLength() < 2.0 ? 'S' : s.getAvgQueueLength() > 8.0 ? 'L' : 'M';
        char slaBucket = s.getSlaViolationRate() > 0.1 ? 'V' : 'O';
        return "" + cpuBucket + queueBucket + slaBucket + "V" + s.getActiveVmCount();
    }

    private double calculateReward(ClusterState s) {
        double reward = 0.0;
        reward -= s.getSlaViolationRate() * 50.0;
        reward -= s.getAvgQueueLength() * 2.0;

        if (s.getAvgCpuUtilisation() < 0.2) {
            reward -= 5.0;
        }

        if (s.getAvgCpuUtilisation() >= 0.4 && s.getAvgCpuUtilisation() <= 0.8) {
            reward += 10.0;
        }

        return reward;
    }

    private int getBestAction(String stateKey) {
        qTable.putIfAbsent(stateKey, new double[3]);
        double[] q = qTable.get(stateKey);
        int best = 0;
        for (int i = 1; i < q.length; i++) {
            if (q[i] > q[best]) best = i;
        }
        return best;
    }

    private double getMaxQ(String stateKey) {
        qTable.putIfAbsent(stateKey, new double[3]);
        double[] q = qTable.get(stateKey);
        double max = q[0];
        for (double v : q) if (v > max) max = v;
        return max;
    }

    public void setEpsilon(double epsilon) { this.epsilon = epsilon; }
}