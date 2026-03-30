package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * SimulationResult.java
 *
 * Immutable data container produced by {@link SimulationRunner#run}.
 *
 * Carries everything that downstream modules (MetricsCalculator, reporters)
 * need — the completed cloudlet list, the VM list used, and the strategy
 * label for display purposes.
 *
 * Nothing in this class knows about CloudSim internals; it is pure data.
 */
public class SimulationResult {

    /** Human-readable name of the strategy that produced this result. */
    public final String strategyName;

    /**
     * Cloudlets as returned by {@code DatacenterBroker.getCloudletReceivedList()}.
     * Each cloudlet carries actual start time, finish time, CPU time, and VM ID
     * filled in by CloudSim after execution.
     */
    public final List<Cloudlet> completedCloudlets;

    /**
     * VMs submitted in this run.
     * Needed by MetricsCalculator to build the per-VM load map and to
     * look up MIPS ratings when estimating utilisation.
     */
    public final List<Vm> vms;

    /**
     * @param strategyName        display label (e.g. "Min-Min")
     * @param completedCloudlets  finished cloudlets from the broker
     * @param vms                 VMs used in this run
     */
    public SimulationResult(String strategyName,
                            List<Cloudlet> completedCloudlets,
                            List<Vm> vms) {
        this.strategyName        = strategyName;
        this.completedCloudlets  = completedCloudlets;
        this.vms                 = vms;
    }
}
