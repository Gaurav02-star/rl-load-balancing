package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * AssignmentStrategy.java
 *
 * The single plug-in point for every load-balancing algorithm.
 *
 * Contract
 * ────────
 * Implementations MUST call {@link Cloudlet#setVmId(int)} on every cloudlet
 * in the list before returning. The simulation runner submits the list to
 * the broker immediately after this call; any cloudlet without a VM ID will
 * be rejected by CloudSim.
 *
 * Adding a new algorithm
 * ──────────────────────
 * 1. Create a new file, e.g. RLStrategy.java
 * 2. Implement this interface
 * 3. Add one line in Main.java:  runner.run(new RLStrategy(), "RL")
 * The simulation core (SimulationRunner) never changes.
 *
 * Thread safety
 * ─────────────
 * CloudSim is single-threaded; implementations need not be thread-safe.
 */
public interface AssignmentStrategy {

    /**
     * Assign each cloudlet to a VM by calling cloudlet.setVmId(vm.getId()).
     *
     * @param cloudlets the full list of cloudlets for this simulation run
     * @param vms       the full list of VMs available in this run
     */
    void assign(List<Cloudlet> cloudlets, List<Vm> vms);
}
