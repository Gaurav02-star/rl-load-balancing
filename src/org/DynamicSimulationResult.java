package org;

import org.cloudbus.cloudsim.Cloudlet;
import java.util.List;

public class DynamicSimulationResult {

    private final String  strategyName;
    private final long    totalArrivals;
    private final List<Cloudlet> completedCloudlets;
    private final int     pendingCloudlets;
    private final double  avgResponseTime;
    private final double  avgTurnaroundTime;
    private final double  overallThroughput;
    private final double  peakArrival;
    private final double  peakQueue;
    private final double  avgCpuUtil;
    private final double  avgVmCount;
    private final double  totalVmSeconds;
    private final double  slaViolationRate;
    private final List<ClusterState> history;

    // ── New fields ────────────────────────────────────────────────────────────
    /**
     * Total energy consumed across all VMs for the entire simulation (Joules).
     * Computed as:
     *   Σ_ticks [ activeVmCount × (cpuUtil × MAX_POWER + (1-cpuUtil) × IDLE_POWER) × timeDelta ]
     */
    private final double totalEnergyJoules;

    /**
     * Total CO2 emissions for this simulation run (kg).
     * Computed as: (totalEnergyJoules / 3_600_000) × CARBON_INTENSITY_KG_PER_KWH
     * Converts Joules → kWh → kg CO2 using the configured grid carbon intensity.
     */
    private final double totalCO2Kg;

    /**
     * Total infrastructure cost in simulated currency units.
     * Computed as: totalVmSeconds × VM_COST_PER_SECOND
     */
    private final double totalCost;

    public DynamicSimulationResult(String strategyName,
                                   long totalArrivals,
                                   List<Cloudlet> completedCloudlets,
                                   int pendingCloudlets,
                                   double avgResponseTime,
                                   double avgTurnaroundTime,
                                   double overallThroughput,
                                   double peakArrival,
                                   double peakQueue,
                                   double avgCpuUtil,
                                   double avgVmCount,
                                   double totalVmSeconds,
                                   double slaViolationRate,
                                   List<ClusterState> history,
                                   double totalEnergyJoules,
                                   double totalCO2Kg,
                                   double totalCost) {
        this.strategyName       = strategyName;
        this.totalArrivals      = totalArrivals;
        this.completedCloudlets = completedCloudlets;
        this.pendingCloudlets   = pendingCloudlets;
        this.avgResponseTime    = avgResponseTime;
        this.avgTurnaroundTime  = avgTurnaroundTime;
        this.overallThroughput  = overallThroughput;
        this.peakArrival        = peakArrival;
        this.peakQueue          = peakQueue;
        this.avgCpuUtil         = avgCpuUtil;
        this.avgVmCount         = avgVmCount;
        this.totalVmSeconds     = totalVmSeconds;
        this.slaViolationRate   = slaViolationRate;
        this.history            = history;
        this.totalEnergyJoules  = totalEnergyJoules;
        this.totalCO2Kg         = totalCO2Kg;
        this.totalCost          = totalCost;
    }

    public String  getStrategyName()       { return strategyName; }
    public long    getTotalArrivals()      { return totalArrivals; }
    public List<Cloudlet> getCompletedCloudlets() { return completedCloudlets; }
    public int     getPendingCloudlets()   { return pendingCloudlets; }
    public double  getAvgResponseTime()    { return avgResponseTime; }
    public double  getAvgTurnaroundTime()  { return avgTurnaroundTime; }
    public double  getOverallThroughput()  { return overallThroughput; }
    public double  getPeakArrival()        { return peakArrival; }
    public double  getPeakQueue()          { return peakQueue; }
    public double  getAvgCpuUtil()         { return avgCpuUtil; }
    public double  getAvgVmCount()         { return avgVmCount; }
    public double  getTotalVmSeconds()     { return totalVmSeconds; }
    public double  getSlaViolationRate()   { return slaViolationRate; }
    public List<ClusterState> getHistory() { return history; }
    public double  getTotalEnergyJoules()  { return totalEnergyJoules; }
    public double  getTotalCO2Kg()         { return totalCO2Kg; }
    public double  getTotalCost()          { return totalCost; }
}