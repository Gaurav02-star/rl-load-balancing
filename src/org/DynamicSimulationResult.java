package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DynamicSimulationResult.java
 * Holds comprehensive results and telemetry history produced by dynamic event-driven runs.
 */
public class DynamicSimulationResult {

    public final String strategyName;
    public final List<Cloudlet> completedCloudlets;
    public final List<Vm> vms;
    public final List<ClusterState> telemetryHistory;
    public final Map<Integer, Double> cloudletArrivalTimes;
    public final long totalArrivals;

    public DynamicSimulationResult(String strategyName,
                                   List<Cloudlet> completedCloudlets,
                                   List<Vm> vms,
                                   List<ClusterState> telemetryHistory,
                                   Map<Integer, Double> cloudletArrivalTimes,
                                   long totalArrivals) {
        this.strategyName = strategyName;
        this.completedCloudlets = Collections.unmodifiableList(completedCloudlets);
        this.vms = Collections.unmodifiableList(vms);
        this.telemetryHistory = Collections.unmodifiableList(telemetryHistory);
        this.cloudletArrivalTimes = Collections.unmodifiableMap(cloudletArrivalTimes);
        this.totalArrivals = totalArrivals;
    }
}