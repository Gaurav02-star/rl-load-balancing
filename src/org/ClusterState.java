package org;

public class ClusterState {

    private final double time;
    private final double averageCpuUtilisation;
    private final double averageQueueLength;
    private final double averageResponseTime;
    private final double throughput;
    private final int    activeVmCount;
    private final double arrivalRate;
    private final double slaViolationRate;
    private final long   totalArrivals;
    private final long   totalCompletions;
    private final int    pendingCloudlets;

    /**
     * Predicted arrival rate at (time + PREDICTION_HORIZON) seconds,
     * computed by the exponential moving average predictor in MonitoringModule.
     * 0.0 when no prediction is available (e.g. first few ticks).
     */
    private final double predictedArrivalRate;

    // ── Constructor (backward-compatible: prediction defaults to 0.0) ─────────

    public ClusterState(double time,
                        double averageCpuUtilisation,
                        double averageQueueLength,
                        double averageResponseTime,
                        double throughput,
                        int    activeVmCount,
                        double arrivalRate,
                        double slaViolationRate,
                        long   totalArrivals,
                        long   totalCompletions,
                        int    pendingCloudlets) {
        this(time, averageCpuUtilisation, averageQueueLength, averageResponseTime,
                throughput, activeVmCount, arrivalRate, slaViolationRate,
                totalArrivals, totalCompletions, pendingCloudlets, 0.0);
    }

    public ClusterState(double time,
                        double averageCpuUtilisation,
                        double averageQueueLength,
                        double averageResponseTime,
                        double throughput,
                        int    activeVmCount,
                        double arrivalRate,
                        double slaViolationRate,
                        long   totalArrivals,
                        long   totalCompletions,
                        int    pendingCloudlets,
                        double predictedArrivalRate) {
        this.time                  = time;
        this.averageCpuUtilisation = averageCpuUtilisation;
        this.averageQueueLength    = averageQueueLength;
        this.averageResponseTime   = averageResponseTime;
        this.throughput            = throughput;
        this.activeVmCount         = activeVmCount;
        this.arrivalRate           = arrivalRate;
        this.slaViolationRate      = slaViolationRate;
        this.totalArrivals         = totalArrivals;
        this.totalCompletions      = totalCompletions;
        this.pendingCloudlets      = pendingCloudlets;
        this.predictedArrivalRate  = predictedArrivalRate;
    }

    public double getTime()                   { return time; }
    public double getAverageCpuUtilisation()  { return averageCpuUtilisation; }
    public double getAvgCpuUtilisation()      { return averageCpuUtilisation; }
    public double getAverageQueueLength()     { return averageQueueLength; }
    public double getAvgQueueLength()         { return averageQueueLength; }
    public double getAverageResponseTime()    { return averageResponseTime; }
    public double getThroughput()             { return throughput; }
    public int    getActiveVmCount()          { return activeVmCount; }
    public double getArrivalRate()            { return arrivalRate; }
    public double getSlaViolationRate()       { return slaViolationRate; }
    public long   getTotalArrivals()          { return totalArrivals; }
    public long   getTotalCompletions()       { return totalCompletions; }
    public int    getPendingCloudlets()       { return pendingCloudlets; }

    /**
     * Predicted arrival rate at (currentTime + PREDICTION_HORIZON).
     * Provided by the EMA predictor in MonitoringModule.
     * The RL autoscaler uses this to pre-provision VMs before a burst arrives.
     */
    public double getPredictedArrivalRate()   { return predictedArrivalRate; }

    /**
     * Convenience: is a load increase predicted?
     * True when predicted rate exceeds current rate by more than 10%.
     */
    public boolean isLoadIncreasePredicted() {
        return predictedArrivalRate > arrivalRate * 1.10;
    }
}