package org;

public class ClusterState {

    private final double time;
    private final double averageCpuUtilisation;
    private final double averageQueueLength;
    private final double averageResponseTime;
    private final double throughput;
    private final int activeVmCount;
    private final double arrivalRate;
    private final double slaViolationRate;
    private final long totalArrivals;
    private final long totalCompletions;
    private final int pendingCloudlets;

    public ClusterState(double time,
                        double averageCpuUtilisation,
                        double averageQueueLength,
                        double averageResponseTime,
                        double throughput,
                        int activeVmCount,
                        double arrivalRate,
                        double slaViolationRate,
                        long totalArrivals,
                        long totalCompletions,
                        int pendingCloudlets) {
        this.time = time;
        this.averageCpuUtilisation = averageCpuUtilisation;
        this.averageQueueLength = averageQueueLength;
        this.averageResponseTime = averageResponseTime;
        this.throughput = throughput;
        this.activeVmCount = activeVmCount;
        this.arrivalRate = arrivalRate;
        this.slaViolationRate = slaViolationRate;
        this.totalArrivals = totalArrivals;
        this.totalCompletions = totalCompletions;
        this.pendingCloudlets = pendingCloudlets;
    }

    public double getTime() { return time; }
    public double getAverageCpuUtilisation() { return averageCpuUtilisation; }
    public double getAvgCpuUtilisation() { return averageCpuUtilisation; } // Alias for RLAutoscaler / ThresholdAutoscaler
    public double getAverageQueueLength() { return averageQueueLength; }
    public double getAvgQueueLength() { return averageQueueLength; } // Alias for RLAutoscaler / ThresholdAutoscaler
    public double getAverageResponseTime() { return averageResponseTime; }
    public double getThroughput() { return throughput; }
    public int getActiveVmCount() { return activeVmCount; }
    public double getArrivalRate() { return arrivalRate; }
    public double getSlaViolationRate() { return slaViolationRate; }
    public long getTotalArrivals() { return totalArrivals; }
    public long getTotalCompletions() { return totalCompletions; }
    public int getPendingCloudlets() { return pendingCloudlets; }
}