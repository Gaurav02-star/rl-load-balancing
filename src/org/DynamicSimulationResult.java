package org;

import org.cloudbus.cloudsim.Cloudlet;
import java.util.List;

public class DynamicSimulationResult {

    private final String strategyName;
    private final long totalArrivals;
    private final List<Cloudlet> completedCloudlets;
    private final int pendingCloudlets;
    private final double averageResponseTime;
    private final double averageTurnaroundTime;
    private final double overallThroughput;
    private final double peakArrivalRate;
    private final double peakQueueLength;
    private final double averageCpuUtilisation;
    private final double slaViolationRate;
    private final List<ClusterState> history;

    public DynamicSimulationResult(String strategyName,
                                   long totalArrivals,
                                   List<Cloudlet> completedCloudlets,
                                   int pendingCloudlets,
                                   double averageResponseTime,
                                   double averageTurnaroundTime,
                                   double overallThroughput,
                                   double peakArrivalRate,
                                   double peakQueueLength,
                                   double averageCpuUtilisation,
                                   double slaViolationRate,
                                   List<ClusterState> history) {
        this.strategyName = strategyName;
        this.totalArrivals = totalArrivals;
        this.completedCloudlets = completedCloudlets;
        this.pendingCloudlets = pendingCloudlets;
        this.averageResponseTime = averageResponseTime;
        this.averageTurnaroundTime = averageTurnaroundTime;
        this.overallThroughput = overallThroughput;
        this.peakArrivalRate = peakArrivalRate;
        this.peakQueueLength = peakQueueLength;
        this.averageCpuUtilisation = averageCpuUtilisation;
        this.slaViolationRate = slaViolationRate;
        this.history = history;
    }

    public String getStrategyName() { return strategyName; }
    public long getTotalArrivals() { return totalArrivals; }
    public List<Cloudlet> getCompletedCloudlets() { return completedCloudlets; }
    public int getPendingCloudlets() { return pendingCloudlets; }
    public double getAverageResponseTime() { return averageResponseTime; }
    public double getAverageTurnaroundTime() { return averageTurnaroundTime; }
    public double getOverallThroughput() { return overallThroughput; }
    public double getPeakArrivalRate() { return peakArrivalRate; }
    public double getPeakQueueLength() { return peakQueueLength; }
    public double getAverageCpuUtilisation() { return averageCpuUtilisation; }
    public double getSlaViolationRate() { return slaViolationRate; }
    public List<ClusterState> getHistory() { return history; }
}