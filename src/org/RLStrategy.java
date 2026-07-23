package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.*;

/**
 * RLStrategy — SARSA(λ) load balancer.
 * Fixed state representation, reward signals, evaluation modes, and array helpers.
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
    private char[] vmSpeedTier = null;

    // Incremental dynamic tracking state
    private double[] incrementalVmFinishTimes = null;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (cloudlets == null || cloudlets.isEmpty() || vms == null || vms.isEmpty()) return;

        double[] vmFinishTimes = new double[vms.size()];

        if (vmSpeedTier == null || vmSpeedTier.length != vms.size()) {
            vmSpeedTier = computeSpeedTiers(vms);
        }

        List<Cloudlet> sorted = new ArrayList<>(cloudlets);
        sorted.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

        // If epsilon == 0.0 (Evaluation Mode): Execute pure greedy policy without Q-table updates
        if (epsilon == 0.0) {
            for (Cloudlet cl : sorted) {
                String state = buildState(vmFinishTimes, cl, vms);
                int bestAction = getGreedyAction(state, vms.size());

                cl.setVmId(vms.get(bestAction).getId());
                double execTime = (double) cl.getCloudletLength() / vms.get(bestAction).getMips();
                vmFinishTimes[bestAction] += execTime;
            }
            return;
        }

        // ── Training Mode (SARSA λ) ──────────────────────────────────────────
        eTrace.clear();

        String currentState  = buildState(vmFinishTimes, sorted.get(0), vms);
        int    currentAction = chooseAction(currentState, vms.size());

        for (int i = 0; i < sorted.size(); i++) {
            Cloudlet cl = sorted.get(i);

            int    eftVm      = computeEftVm(vmFinishTimes, cl, vms);
            double execTime   = (double) cl.getCloudletLength() / vms.get(currentAction).getMips();
            double oldMakespan = max(vmFinishTimes);

            cl.setVmId(vms.get(currentAction).getId());
            vmFinishTimes[currentAction] += execTime;

            double newMakespan = max(vmFinishTimes);
            double minFinish   = min(vmFinishTimes);

            // Reward
            double reward = 0.0;
            reward -= (newMakespan - oldMakespan) * W_MAKESPAN;
            reward -= (newMakespan - minFinish)   * W_IMBALANCE;
            if (currentAction == eftVm) reward += W_EFT_BONUS;

            boolean isTerminal = (i == sorted.size() - 1);
            String  nextState  = null;
            int     nextAction = 0;

            if (!isTerminal) {
                nextState  = buildState(vmFinishTimes, sorted.get(i + 1), vms);
                nextAction = chooseAction(nextState, vms.size());
            }

            // TD error
            qTable.putIfAbsent(currentState, new double[vms.size()]);
            double qCurrent = qTable.get(currentState)[currentAction];
            double qNext    = 0.0;
            if (!isTerminal) {
                qTable.putIfAbsent(nextState, new double[vms.size()]);
                qNext = qTable.get(nextState)[nextAction];
            }
            double tdError = reward + gamma * qNext - qCurrent;

            // Eligibility trace update
            eTrace.putIfAbsent(currentState, new double[vms.size()]);
            eTrace.get(currentState)[currentAction] += 1.0;

            for (Map.Entry<String, double[]> entry : eTrace.entrySet()) {
                String   s      = entry.getKey();
                double[] traces = entry.getValue();
                qTable.putIfAbsent(s, new double[vms.size()]);
                double[] qVals  = qTable.get(s);
                for (int a = 0; a < vms.size(); a++) {
                    if (traces[a] > 1e-6) {
                        qVals[a]  += alpha * tdError * traces[a];
                        traces[a] *= gamma * lambda;
                    }
                }
            }

            currentState  = nextState;
            currentAction = nextAction;
        }

        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
    }

    /**
     * Single-cloudlet incremental scheduling operation for dynamic mode.
     */
    public void assignIncremental(Cloudlet cloudlet, List<Vm> currentVms) {
        if (incrementalVmFinishTimes == null || incrementalVmFinishTimes.length != currentVms.size()) {
            incrementalVmFinishTimes = new double[currentVms.size()];
            vmSpeedTier = computeSpeedTiers(currentVms);
        }

        String state = buildState(incrementalVmFinishTimes, cloudlet, currentVms);
        int chosenAction = getGreedyAction(state, currentVms.size());

        cloudlet.setVmId(currentVms.get(chosenAction).getId());

        double execTime = (double) cloudlet.getCloudletLength() / currentVms.get(chosenAction).getMips();
        incrementalVmFinishTimes[chosenAction] += execTime;
    }

    // ── Speed tiers ───────────────────────────────────────────────────────────

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

    // ── State construction ────────────────────────────────────────────────────

    private String buildState(double[] vmTimes, Cloudlet cl, List<Vm> vms) {
        double avgTime = mean(vmTimes);
        StringBuilder sb = new StringBuilder(vms.size() * 2 + 2);

        for (int i = 0; i < vms.size(); i++) {
            double diff = vmTimes[i] - avgTime;
            sb.append(diff < -1.0 ? 'U' : diff > 1.0 ? 'O' : 'B'); // Underloaded, Overloaded, Balanced
            sb.append(vmSpeedTier[i]);
        }

        long len = cl.getCloudletLength();
        sb.append('T').append(len < 1_450 ? 0 : len < 2_450 ? 1 : 2);
        return sb.toString();
    }

    // ── Oracle helpers ────────────────────────────────────────────────────────

    private int computeEftVm(double[] vmTimes, Cloudlet cl, List<Vm> vms) {
        int best = 0; double bestEft = Double.MAX_VALUE;
        for (int i = 0; i < vms.size(); i++) {
            double eft = vmTimes[i] + (double) cl.getCloudletLength() / vms.get(i).getMips();
            if (eft < bestEft) { bestEft = eft; best = i; }
        }
        return best;
    }

    // ── Q-Learning core ───────────────────────────────────────────────────────

    private int chooseAction(String state, int numVMs) {
        qTable.putIfAbsent(state, new double[numVMs]);
        if (random.nextDouble() < epsilon) return random.nextInt(numVMs);
        return getGreedyAction(state, numVMs);
    }

    private int getGreedyAction(String state, int numVMs) {
        qTable.putIfAbsent(state, new double[numVMs]);
        double[] q = qTable.get(state);
        int best = 0;
        for (int i = 1; i < numVMs; i++) {
            if (q[i] > q[best]) best = i;
        }
        return best;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double max(double[] a) {
        double m = a[0]; for (double v : a) if (v > m) m = v; return m;
    }

    private static double min(double[] a) {
        double m = a[0]; for (double v : a) if (v < m) m = v; return m;
    }

    private static double mean(double[] a) {
        double s = 0.0; for (double v : a) s += v; return s / a.length;
    }

    public void   setEpsilon(double e) { this.epsilon = e; }
    public double getEpsilon()         { return epsilon; }
    public int    getQTableSize()      { return qTable.size(); }
    public int    getTraceSize()       { return eTrace.size(); }
}