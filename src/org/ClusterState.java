package org;

/**
 * ClusterState.java
 * Immutable snapshot of system dynamic metrics for observability and future RL autoscaling state.
 */
public class ClusterState {

    private final double simulationTime;
    private final double avgCpuUtilisation;
    private final double avgQueueLength;
    private final double avgResponseTime;
    private final double throughput;
    private final int activeVmCount;
    private final double arrivalRate;
    private final double slaViolationRate;
    private final long totalArrivals;
    private final long totalCompletions;
    private final int pendingCloudlets;

    public ClusterState(double simulationTime,
                        double avgCpuUtilisation,
                        double avgQueueLength,
                        double avgResponseTime,
                        double throughput,
                        int activeVmCount,
                        double arrivalRate,
                        double slaViolationRate,
                        long totalArrivals,
                        long totalCompletions,
                        int pendingCloudlets) {
        this.simulationTime = simulationTime;
        this.avgCpuUtilisation = avgCpuUtilisation;
        this.avgQueueLength = avgQueueLength;
        this.avgResponseTime = avgResponseTime;
        this.throughput = throughput;
        this.activeVmCount = activeVmCount;
        this.arrivalRate = arrivalRate;
        this.slaViolationRate = slaViolationRate;
        this.totalArrivals = totalArrivals;
        this.totalCompletions = totalCompletions;
        this.pendingCloudlets = pendingCloudlets;
    }

    public double getSimulationTime() { return simulationTime; }
    public double getAvgCpuUtilisation() { return avgCpuUtilisation; }
    public double getAvgQueueLength() { return avgQueueLength; }
    public double getAvgResponseTime() { return avgResponseTime; }
    public double getThroughput() { return throughput; }
    public int getActiveVmCount() { return activeVmCount; }
    public double getArrivalRate() { return arrivalRate; }
    public double getSlaViolationRate() { return slaViolationRate; }
    public long getTotalArrivals() { return totalArrivals; }
    public long getTotalCompletions() { return totalCompletions; }
    public int getPendingCloudlets() { return pendingCloudlets; }

    @Override
    public String toString() {
        return String.format("Time=%.2fs | ActiveVMs=%d | ArrivalRate=%.2f/s | Throughput=%.2f/s | AvgCPU=%.2f%% | Queue=%.2f | AvgRT=%.3fs | SLA_Viol=%.2f%% | Arrivals=%d | Completions=%d | Pending=%d",
                simulationTime, activeVmCount, arrivalRate, throughput, avgCpuUtilisation * 100.0,
                avgQueueLength, avgResponseTime, slaViolationRate * 100.0, totalArrivals, totalCompletions, pendingCloudlets);
    }
}