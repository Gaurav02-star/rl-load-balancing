package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * RLStrategy.java  –  Reinforcement Learning (future extension)
 *
 * Plug this in exactly like any other strategy:
 *
 *   SimulationRunner.run(new RLStrategy(), "RL")
 *
 * No other file changes.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Implementation guide
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * State space (suggested)
 *   s = [ cloudlet.length / MAX_LENGTH,          // normalised task size
 *          vmLoad[i] / MAX_EXPECTED_LOAD,         // per-VM load fraction
 *          remainingCloudlets / TOTAL_CLOUDLETS ] // completion progress
 *
 * Action space
 *   a ∈ { 0, 1, …, numVms-1 }  →  index of the VM to assign this cloudlet
 *
 * Reward signal (suggested)
 *   r = - (finish_time - expected_finish_time)   // penalise slow assignments
 *   or
 *   r = - makespan                               // penalise at episode end
 *
 * Training loop
 *   Run SimulationRunner.run(new RLStrategy(), "RL") repeatedly.
 *   After each run, use SimulationResult + MetricsCalculator to get the
 *   reward signal and update the policy.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Current status: STUB — falls back to Least-Loaded until RL is wired up.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class RLStrategy implements AssignmentStrategy {

    // ── Policy weights / model reference ────────────────────────────────────
    // Replace with your trained model, e.g.:
    //   private final NeuralNetwork policy;
    //   public RLStrategy(NeuralNetwork policy) { this.policy = policy; }

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] vmLoad = new double[vms.size()];

        for (Cloudlet cl : cloudlets) {
            // ── Build state vector ───────────────────────────────────────────
            // double[] state = buildState(cl, vmLoad, cloudlets.size());

            // ── Query policy ────────────────────────────────────────────────
            // int action = policy.predict(state);   // returns VM index

            // ── Fallback: least-loaded (remove once RL is wired up) ─────────
            int action = indexOfMin(vmLoad);

            // ── Apply assignment ─────────────────────────────────────────────
            cl.setVmId(vms.get(action).getId());

            // ── Update load estimate (used both for fallback and as part of state) ──
            double execTime = (double) cl.getCloudletLength()
                              / vms.get(action).getMips();
            vmLoad[action] += execTime;

            // ── Store experience for training (offline / online) ─────────────
            // replayBuffer.add(state, action, reward=0);  // reward filled in later
        }
    }

    // ── Helper: index of minimum value in array ──────────────────────────────
    private int indexOfMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[idx]) idx = i;
        }
        return idx;
    }

    // ── Helper: build a normalised state vector ──────────────────────────────
    // Uncomment and adapt when wiring up the actual RL model.
    //
    // private double[] buildState(Cloudlet cl, double[] vmLoad, int totalCloudlets) {
    //     double maxLoad = Arrays.stream(vmLoad).max().orElse(1.0);
    //     double[] state = new double[vmLoad.length + 2];
    //     state[0] = cl.getCloudletLength() / SimulationConfig.CL_LENGTH_LONG;
    //     for (int i = 0; i < vmLoad.length; i++) {
    //         state[i + 1] = (maxLoad > 0) ? vmLoad[i] / maxLoad : 0.0;
    //     }
    //     state[vmLoad.length + 1] = 1.0 / totalCloudlets; // remaining progress
    //     return state;
    // }
}
