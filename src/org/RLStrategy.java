package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;

/**
 * RLStrategy — SARSA(λ) load balancer.
 * Features VM ID-keyed state tracking, online SARSA updates during incremental arrival,
 * and VM-count-invariant state encoding for dynamic autoscaling safety.
 */
public class RLStrategy implements AssignmentStrategy {

    // ── Hyperparameters ───────────────────────────────────────────────────────
    private double alpha   = 0.2;
    private double gamma   = 0.90;
    private double lambda  = 0.85;
    private double epsilon = 0.7;

    private static final double EPSILON_MIN   = 0.01;
    private static final double EPSILON_DECAY = 0.9992;

    // ── Reward weights ────────────────────────────────────────────────────────
    private static final double W_MAKESPAN    = 8.0;
    private static final double W_IMBALANCE   = 4.0;
    private static final double W_EFT_BONUS   = 6.0;

    // ── State thresholds ──────────────────────────────────────────────────────
    private static final double SPEED_SLOW  = 0.80;
    private static final double SPEED_FAST  = 1.20;

    // ── Core data structures ──────────────────────────────────────────────────
    private final Map<String, double[]> qTable = new HashMap<>();
    private final Map<String, double[]> eTrace = new HashMap<>();
    private final Random random = new Random(42);

    // Dynamic state tracking keyed by VM ID (resilient to VM removal/reordering)
    private final Map<Integer, Double> vmFinishTimesMap = new HashMap<>();
    private final Map<Integer, Character> vmSpeedTierMap = new HashMap<>();

    // State memory for online incremental SARSA updates
    private String lastIncrementalState = null;
    private int lastIncrementalActionVmId = -1;
    private double lastMakespan = 0.0;

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (cloudlets == null || cloudlets.isEmpty() || vms == null || vms.isEmpty()) return;

        double[] vmFinishTimes = new double[vms.size()];
        char[] vmSpeedTier = computeSpeedTiers(vms);

        List<Cloudlet> sorted = new ArrayList<>(cloudlets);
        sorted.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

        if (epsilon == 0.0) {
            for (Cloudlet cl : sorted) {
                String state = buildBatchState(vmFinishTimes, cl, vms, vmSpeedTier);
                int bestActionIdx = getGreedyAction(state, vms.size());

                cl.setVmId(vms.get(bestActionIdx).getId());
                double execTime = (double) cl.getCloudletLength() / vms.get(bestActionIdx).getMips();
                vmFinishTimes[bestActionIdx] += execTime;
            }
            return;
        }

        // Training Mode (Batch SARSA λ)
        eTrace.clear();
        String currentState = buildBatchState(vmFinishTimes, sorted.get(0), vms, vmSpeedTier);
        int currentActionIdx = chooseAction(currentState, vms.size());

        for (int i = 0; i < sorted.size(); i++) {
            Cloudlet cl = sorted.get(i);

            int eftVmIdx = computeEftVmIdx(vmFinishTimes, cl, vms);
            double execTime = (double) cl.getCloudletLength() / vms.get(currentActionIdx).getMips();
            double oldMakespan = max(vmFinishTimes);

            cl.setVmId(vms.get(currentActionIdx).getId());
            vmFinishTimes[currentActionIdx] += execTime;

            double newMakespan = max(vmFinishTimes);
            double minFinish = min(vmFinishTimes);

            double reward = 0.0;
            reward -= (newMakespan - oldMakespan) * W_MAKESPAN;
            reward -= (newMakespan - minFinish) * W_IMBALANCE;
            if (currentActionIdx == eftVmIdx) reward += W_EFT_BONUS;

            boolean isTerminal = (i == sorted.size() - 1);
            String nextState = null;
            int nextActionIdx = 0;

            if (!isTerminal) {
                nextState = buildBatchState(vmFinishTimes, sorted.get(i + 1), vms, vmSpeedTier);
                nextActionIdx = chooseAction(nextState, vms.size());
            }

            qTable.putIfAbsent(currentState, new double[vms.size()]);
            double qCurrent = qTable.get(currentState)[currentActionIdx];
            double qNext = 0.0;
            if (!isTerminal) {
                qTable.putIfAbsent(nextState, new double[vms.size()]);
                qNext = qTable.get(nextState)[nextActionIdx];
            }
            double tdError = reward + gamma * qNext - qCurrent;

            eTrace.putIfAbsent(currentState, new double[vms.size()]);
            eTrace.get(currentState)[currentActionIdx] += 1.0;

            for (Map.Entry<String, double[]> entry : eTrace.entrySet()) {
                String s = entry.getKey();
                double[] traces = entry.getValue();
                qTable.putIfAbsent(s, new double[vms.size()]);
                double[] qVals = qTable.get(s);
                for (int a = 0; a < vms.size(); a++) {
                    if (traces[a] > 1e-6) {
                        qVals[a] += alpha * tdError * traces[a];
                        traces[a] *= gamma * lambda;
                    }
                }
            }

            currentState = nextState;
            currentActionIdx = nextActionIdx;
        }

        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
    }

    /**
     * Single-cloudlet incremental scheduling with online SARSA(λ) updates and VM-ID resilience.
     */
    public void assignIncremental(Cloudlet cloudlet, List<Vm> currentVms) {
        if (cloudlet == null || currentVms == null || currentVms.isEmpty()) return;

        updateVmMaps(currentVms);

        // Build VM-count-invariant state encoding
        String currentState = buildIncrementalState(cloudlet, currentVms);
        int chosenVmIdx = chooseAction(currentState, currentVms.size());
        Vm chosenVm = currentVms.get(chosenVmIdx);

        // Online SARSA(λ) step for previous arrival transition
        if (epsilon > 0.0 && lastIncrementalState != null && lastIncrementalActionVmId != -1) {
            double oldMakespan = lastMakespan;
            double currentMakespan = getMaxVmFinishTime(currentVms);
            double minFinish = getMinVmFinishTime(currentVms);

            double reward = 0.0;
            reward -= (currentMakespan - oldMakespan) * W_MAKESPAN;
            reward -= (currentMakespan - minFinish) * W_IMBALANCE;

            qTable.putIfAbsent(lastIncrementalState, new double[currentVms.size()]);
            qTable.putIfAbsent(currentState, new double[currentVms.size()]);

            double qOld = qTable.get(lastIncrementalState)[Math.min(lastIncrementalActionVmId, qTable.get(lastIncrementalState).length - 1)];
            double qNext = qTable.get(currentState)[chosenVmIdx];

            double tdError = reward + gamma * qNext - qOld;

            eTrace.putIfAbsent(lastIncrementalState, new double[currentVms.size()]);
            eTrace.get(lastIncrementalState)[Math.min(lastIncrementalActionVmId, eTrace.get(lastIncrementalState).length - 1)] += 1.0;

            for (Map.Entry<String, double[]> entry : eTrace.entrySet()) {
                String s = entry.getKey();
                double[] traces = entry.getValue();
                qTable.putIfAbsent(s, new double[currentVms.size()]);
                double[] qVals = qTable.get(s);
                for (int a = 0; a < currentVms.size(); a++) {
                    if (a < traces.length && traces[a] > 1e-6) {
                        qVals[a] += alpha * tdError * traces[a];
                        traces[a] *= gamma * lambda;
                    }
                }
            }
        }

        // Apply chosen action
        cloudlet.setVmId(chosenVm.getId());
        double execTime = (double) cloudlet.getCloudletLength() / chosenVm.getMips();
        double updatedFinish = vmFinishTimesMap.getOrDefault(chosenVm.getId(), 0.0) + execTime;
        vmFinishTimesMap.put(chosenVm.getId(), updatedFinish);

        lastIncrementalState = currentState;
        lastIncrementalActionVmId = chosenVmIdx;
        lastMakespan = getMaxVmFinishTime(currentVms);

        if (epsilon > 0.0) {
            epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
        }
    }

    private void updateVmMaps(List<Vm> currentVms) {
        double avgMips = 0.0;
        for (Vm v : currentVms) {
            avgMips += v.getMips();
            vmFinishTimesMap.putIfAbsent(v.getId(), 0.0);
        }
        avgMips /= currentVms.size();

        for (Vm v : currentVms) {
            double r = v.getMips() / avgMips;
            vmSpeedTierMap.put(v.getId(), r > SPEED_FAST ? 'F' : r < SPEED_SLOW ? 'S' : 'M');
        }
    }

    private String buildIncrementalState(Cloudlet cl, List<Vm> vms) {
        double avgTime = 0.0;
        for (Vm v : vms) {
            avgTime += vmFinishTimesMap.getOrDefault(v.getId(), 0.0);
        }
        avgTime /= vms.size();

        StringBuilder sb = new StringBuilder();
        sb.append("N").append(vms.size()); // Cluster size bucket

        for (Vm v : vms) {
            double diff = vmFinishTimesMap.getOrDefault(v.getId(), 0.0) - avgTime;
            sb.append(diff < -1.0 ? 'U' : diff > 1.0 ? 'O' : 'B');
            sb.append(vmSpeedTierMap.getOrDefault(v.getId(), 'M'));
        }

        long len = cl.getCloudletLength();
        sb.append('T').append(len < 1_450 ? 0 : len < 2_450 ? 1 : 2);
        return sb.toString();
    }

    private String buildBatchState(double[] vmTimes, Cloudlet cl, List<Vm> vms, char[] speedTiers) {
        double avgTime = mean(vmTimes);
        StringBuilder sb = new StringBuilder(vms.size() * 2 + 4);
        sb.append("N").append(vms.size());

        for (int i = 0; i < vms.size(); i++) {
            double diff = vmTimes[i] - avgTime;
            sb.append(diff < -1.0 ? 'U' : diff > 1.0 ? 'O' : 'B');
            sb.append(speedTiers[i]);
        }

        long len = cl.getCloudletLength();
        sb.append('T').append(len < 1_450 ? 0 : len < 2_450 ? 1 : 2);
        return sb.toString();
    }

    private static char[] computeSpeedTiers(List<Vm> vms) {
        double avg = 0.0;
        for (Vm v : vms) avg += v.getMips();
        avg /= vms.size();
        char[] t = new char[vms.size()];
        for (int i = 0; i < vms.size(); i++) {
            double r = vms.get(i).getMips() / avg;
            t[i] = r > SPEED_FAST ? 'F' : r < SPEED_SLOW ? 'S' : 'M';
        }
        return t;
    }

    private int computeEftVmIdx(double[] vmTimes, Cloudlet cl, List<Vm> vms) {
        int best = 0; double bestEft = Double.MAX_VALUE;
        for (int i = 0; i < vms.size(); i++) {
            double eft = vmTimes[i] + (double) cl.getCloudletLength() / vms.get(i).getMips();
            if (eft < bestEft) { bestEft = eft; best = i; }
        }
        return best;
    }

    private int chooseAction(String state, int numVMs) {
        qTable.putIfAbsent(state, new double[numVMs]);
        if (random.nextDouble() < epsilon) return random.nextInt(numVMs);
        return getGreedyAction(state, numVMs);
    }

    private int getGreedyAction(String state, int numVMs) {
        qTable.putIfAbsent(state, new double[numVMs]);
        double[] q = qTable.get(state);
        int best = 0;
        for (int i = 1; i < Math.min(numVMs, q.length); i++) {
            if (q[i] > q[best]) best = i;
        }
        return best;
    }

    private double getMaxVmFinishTime(List<Vm> vms) {
        double max = 0.0;
        for (Vm v : vms) {
            double t = vmFinishTimesMap.getOrDefault(v.getId(), 0.0);
            if (t > max) max = t;
        }
        return max;
    }

    private double getMinVmFinishTime(List<Vm> vms) {
        double min = Double.MAX_VALUE;
        for (Vm v : vms) {
            double t = vmFinishTimesMap.getOrDefault(v.getId(), 0.0);
            if (t < min) min = t;
        }
        return min == Double.MAX_VALUE ? 0.0 : min;
    }

    private static double max(double[] a) {
        double m = a[0]; for (double v : a) if (v > m) m = v; return m;
    }

    private static double min(double[] a) {
        double m = a[0]; for (double v : a) if (v < m) m = v; return m;
    }

    private static double mean(double[] a) {
        double s = 0.0; for (double v : a) s += v; return s / a.length;
    }

    public void setEpsilon(double e) { this.epsilon = e; }
    public double getEpsilon() { return epsilon; }
}