package org;

import org.cloudbus.cloudsim.Cloudlet;
import java.util.List;

public class DynamicSimulationResult {

    private final String strategyName;
    private final long totalArrivals;
    private final List<Cloudlet> completedCloudlets;
    private final int pendingCloudlets;
    private final double avgResponseTime;
    private final double avgTurnaroundTime;
    private final double overallThroughput;
    private final double peakArrival;
    private final double peakQueue;
    private final double avgCpuUtil;
    private final double avgVmCount;
    private final double totalVmSeconds; // <--- Added genuine time-integrated cost field
    private final double slaViolationRate;
    private final List<ClusterState> history;

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
                                   double totalVmSeconds, // <--- Constructor parameter
                                   double slaViolationRate,
                                   List<ClusterState> history) {
        this.strategyName = strategyName;
        this.totalArrivals = totalArrivals;
        this.completedCloudlets = completedCloudlets;
        this.pendingCloudlets = pendingCloudlets;
        this.avgResponseTime = avgResponseTime;
        this.avgTurnaroundTime = avgTurnaroundTime;
        this.overallThroughput = overallThroughput;
        this.peakArrival = peakArrival;
        this.peakQueue = peakQueue;
        this.avgCpuUtil = avgCpuUtil;
        this.avgVmCount = avgVmCount;
        this.totalVmSeconds = totalVmSeconds;
        this.slaViolationRate = slaViolationRate;
        this.history = history;
    }

    public String getStrategyName() { return strategyName; }
    public long getTotalArrivals() { return totalArrivals; }
    public List<Cloudlet> getCompletedCloudlets() { return completedCloudlets; }
    public int getPendingCloudlets() { return pendingCloudlets; }
    public double getAvgResponseTime() { return avgResponseTime; }
    public double getAvgTurnaroundTime() { return avgTurnaroundTime; }
    public double getOverallThroughput() { return overallThroughput; }
    public double getPeakArrival() { return peakArrival; }
    public double getPeakQueue() { return peakQueue; }
    public double getAvgCpuUtil() { return avgCpuUtil; }
    public double getAvgVmCount() { return avgVmCount; }
    public double getTotalVmSeconds() { return totalVmSeconds; } // <--- Exposed getter
    public double getSlaViolationRate() { return slaViolationRate; }
    public List<ClusterState> getHistory() { return history; }
}