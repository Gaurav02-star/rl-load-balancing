package org;

/**
 * ThresholdAutoscaler.java
 * Rule-based autoscaling logic reacting to queue lengths and CPU utilization.
 * Protected against mid-burst scale downs when queue backlog is present.
 */
public class ThresholdAutoscaler implements Autoscaler {

    private static final double SCALE_UP_QUEUE_THRESHOLD = 1.0;
    private static final double SCALE_UP_CPU_THRESHOLD   = 0.60;

    private static final double SCALE_DOWN_CPU_THRESHOLD = 0.15;

    @Override
    public AutoscalerAction evaluateScaling(ClusterState state) {
        if (state == null) return AutoscalerAction.NO_OP;

        double queue = state.getAverageQueueLength();
        double cpu = state.getAverageCpuUtilisation();
        int activeVms = state.getActiveVmCount();

        // SCALE UP Condition
        if ((queue > SCALE_UP_QUEUE_THRESHOLD || cpu > SCALE_UP_CPU_THRESHOLD)
                && activeVms < SimulationConfig.MAX_VMS) {
            return AutoscalerAction.SCALE_UP;
        }

        // SCALE DOWN Condition (Hard safety check: Queue must be completely zero)
        if (queue == 0.0 && cpu < SCALE_DOWN_CPU_THRESHOLD && activeVms > SimulationConfig.MIN_VMS) {
            return AutoscalerAction.SCALE_DOWN;
        }

        return AutoscalerAction.NO_OP;
    }
}