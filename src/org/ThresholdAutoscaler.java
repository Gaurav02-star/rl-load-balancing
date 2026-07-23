package org;

/**
 * ThresholdAutoscaler.java
 * Rule-based baseline autoscaler comparing CPU utilization & queue length against fixed thresholds.
 */
public class ThresholdAutoscaler implements Autoscaler {

    private static final double CPU_UPPER_THRESHOLD = 0.75;
    private static final double CPU_LOWER_THRESHOLD = 0.25;
    private static final double QUEUE_UPPER_THRESHOLD = 5.0;

    @Override
    public AutoscalerAction evaluateScaling(ClusterState state) {
        if (state == null) return AutoscalerAction.NO_OP;

        // Scale up if CPU is high OR queue is backing up
        if (state.getAvgCpuUtilisation() > CPU_UPPER_THRESHOLD || state.getAvgQueueLength() > QUEUE_UPPER_THRESHOLD) {
            if (state.getActiveVmCount() < SimulationConfig.MAX_VMS) {
                return AutoscalerAction.SCALE_UP;
            }
        }

        // Scale down if CPU is underutilized and queue is empty
        if (state.getAvgCpuUtilisation() < CPU_LOWER_THRESHOLD && state.getAvgQueueLength() < 1.0) {
            if (state.getActiveVmCount() > SimulationConfig.MIN_VMS) {
                return AutoscalerAction.SCALE_DOWN;
            }
        }

        return AutoscalerAction.NO_OP;
    }
}