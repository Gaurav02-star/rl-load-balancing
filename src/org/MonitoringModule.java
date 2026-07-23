package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MonitoringModule.java
 * SimEntity sampling telemetry snapshots and orchestrating autoscaling ticks safely without memory leaks.
 */
public class MonitoringModule extends SimEntity {

    public static final int EVENT_MONITOR_TICK = 8001;

    private final CloudSimGateway gateway;
    private final WorkloadGenerator workloadGenerator;
    private final VmLifecycleManager lifecycleManager;
    private final Autoscaler autoscaler;
    private final double sampleInterval;
    private final double windowSize;
    private final double slaThreshold;
    private final List<ClusterState> history = new ArrayList<>();

    private double lastAutoscaleTime = 0.0;
    private ClusterState latestState = null;

    public MonitoringModule(String name,
                            CloudSimGateway gateway,
                            WorkloadGenerator workloadGenerator,
                            VmLifecycleManager lifecycleManager,
                            Autoscaler autoscaler,
                            double sampleInterval,
                            double windowSize,
                            double slaThreshold) {
        super(name);
        this.gateway = gateway;
        this.workloadGenerator = workloadGenerator;
        this.lifecycleManager = lifecycleManager;
        this.autoscaler = autoscaler;
        this.sampleInterval = sampleInterval;
        this.windowSize = windowSize;
        this.slaThreshold = slaThreshold;
    }

    @Override
    public void startEntity() {
        schedule(getId(), sampleInterval, EVENT_MONITOR_TICK);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == EVENT_MONITOR_TICK) {
            double currentTime = CloudSim.clock();
            sampleTelemetry(currentTime);

            // Trigger Autoscaling periodically
            if (autoscaler != null && lifecycleManager != null) {
                if (currentTime - lastAutoscaleTime >= SimulationConfig.AUTOSCALING_INTERVAL) {
                    AutoscalerAction action = autoscaler.evaluateScaling(latestState);
                    if (action == AutoscalerAction.SCALE_UP) {
                        lifecycleManager.scaleUp();
                    } else if (action == AutoscalerAction.SCALE_DOWN) {
                        lifecycleManager.scaleDown();
                    }
                    lastAutoscaleTime = currentTime;
                }
            }

            long totalArrivals = workloadGenerator.getTotalArrivals();
            List<Cloudlet> completed = gateway.getCompletedCloudlets();
            int pending = gateway.getInFlightCloudletCount();

            // Continue monitoring ticks as long as tasks are still arriving or processing in queues
            boolean keepRunning = (totalArrivals == 0) || (completed.size() < totalArrivals) || (pending > 0);

            if (keepRunning && currentTime < (SimulationConfig.WORKLOAD_DURATION + 500.0)) {
                schedule(getId(), sampleInterval, EVENT_MONITOR_TICK);
            }
        }
    }

    @Override
    public void shutdownEntity() {}

    private void sampleTelemetry(double currentTime) {
        List<Vm> activeVms = gateway.getActiveVms();
        int activeVmCount = Math.max(1, activeVms.size());

        Map<Integer, Double> arrivalTimes = workloadGenerator.getCloudletArrivalTimes();
        List<Cloudlet> completedList = gateway.getCompletedCloudlets();

        long totalArrivals = workloadGenerator.getTotalArrivals();
        long totalCompletions = completedList.size();

        int pendingCloudlets = (int) Math.max(0, totalArrivals - totalCompletions);

        double windowStart = Math.max(0.0, currentTime - windowSize);
        double effectiveWindow = Math.max(1.0e-3, currentTime - windowStart);

        long arrivalsInWindow = 0;
        for (Double arrTime : arrivalTimes.values()) {
            if (arrTime >= windowStart && arrTime <= currentTime) {
                arrivalsInWindow++;
            }
        }
        double arrivalRate = arrivalsInWindow / effectiveWindow;

        long completionsInWindow = 0;
        double sumResponseTime = 0.0;
        long slaViolationsInWindow = 0;

        for (Cloudlet c : completedList) {
            double finishTime = c.getFinishTime();
            if (finishTime >= windowStart && finishTime <= currentTime) {
                completionsInWindow++;
                Double arrTime = arrivalTimes.get(c.getCloudletId());
                double arrival = (arrTime != null) ? arrTime : c.getSubmissionTime();

                double responseTime = Math.max(0.0, c.getExecStartTime() - arrival);
                sumResponseTime += responseTime;

                if (responseTime > slaThreshold) {
                    slaViolationsInWindow++;
                }
            }
        }

        double throughput = completionsInWindow / effectiveWindow;
        double avgResponseTime = (completionsInWindow > 0) ? (sumResponseTime / completionsInWindow) : 0.0;
        double slaViolationRate = (completionsInWindow > 0) ? ((double) slaViolationsInWindow / completionsInWindow) : 0.0;

        int inFlightCount = gateway.getInFlightCloudletCount();
        double avgQueueLength = (double) inFlightCount / activeVmCount;

        // NOTE: This is a heuristic proxy for CPU load, not a real utilization reading from
        // CloudSim's Vm/Host MIPS accounting. It assumes a fixed "capacity" of 2 concurrent
        // cloudlets per active VM. Once activeVmCount correctly reflects scale-up/down (see
        // DynamicBroker fix), this ratio will move as intended -- but if you still see it
        // pinned at a suspiciously round number, check whether inFlightCount (sourced from
        // DynamicBroker.getInFlightCloudletCount()) is actually receiving all arriving
        // cloudlets, or whether your scheduler/workload generator is buffering cloudlets
        // internally before calling broker.submitDynamicCloudlet().
        double avgCpuUtilisation = Math.min(1.0, (double) inFlightCount / (activeVmCount * 2.0));

        ClusterState snapshot = new ClusterState(
                currentTime,
                avgCpuUtilisation,
                avgQueueLength,
                avgResponseTime,
                throughput,
                activeVmCount,
                arrivalRate,
                slaViolationRate,
                totalArrivals,
                totalCompletions,
                pendingCloudlets
        );

        latestState = snapshot;
        history.add(snapshot);
    }

    public ClusterState getCurrentState() {
        return latestState;
    }

    public List<ClusterState> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}