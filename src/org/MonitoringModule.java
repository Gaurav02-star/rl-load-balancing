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
 * Samples telemetry using real queue depth from PendingTaskQueue and active VM capacity usage.
 * Includes a 15-second post-completion grace period to allow full scale-down convergence.
 */
public class MonitoringModule extends SimEntity {

    public static final int EVENT_MONITOR_TICK = 8001;

    private final CloudSimGateway gateway;
    private final PendingTaskQueue pendingQueue;
    private final TaskDispatcher dispatcher;
    private final WorkloadGenerator workloadGenerator;
    private final VmLifecycleManager lifecycleManager;
    private final Autoscaler autoscaler;
    private final double sampleInterval;
    private final double windowSize;
    private final double slaThreshold;
    private final List<ClusterState> history = new ArrayList<>();

    private double lastAutoscaleTime = 0.0;
    private double allFinishedTime = -1.0;
    private ClusterState latestState = null;

    public MonitoringModule(String name,
                            CloudSimGateway gateway,
                            PendingTaskQueue pendingQueue,
                            TaskDispatcher dispatcher,
                            WorkloadGenerator workloadGenerator,
                            VmLifecycleManager lifecycleManager,
                            Autoscaler autoscaler,
                            double sampleInterval,
                            double windowSize,
                            double slaThreshold) {
        super(name);
        this.gateway = gateway;
        this.pendingQueue = pendingQueue;
        this.dispatcher = dispatcher;
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

            // Periodically evaluate autoscaling actions
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
            int pendingInQueue = pendingQueue.size();
            int inFlightTasks = dispatcher.getTotalInFlight();

            boolean tasksFinished = (totalArrivals > 0)
                    && (completed.size() >= totalArrivals)
                    && (pendingInQueue == 0)
                    && (inFlightTasks == 0);

            if (tasksFinished && allFinishedTime < 0) {
                allFinishedTime = currentTime;
            }

            // Allow 15s post-completion runway so autoscaler can step down 7 -> 6 -> 5 -> 4 -> 3 -> 2
            boolean gracePeriodActive = (allFinishedTime > 0) && ((currentTime - allFinishedTime) <= 15.0);
            boolean shouldContinue = (!tasksFinished || gracePeriodActive)
                    && (currentTime < (SimulationConfig.WORKLOAD_DURATION + 80.0));

            if (shouldContinue) {
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

        int pendingCloudlets = pendingQueue.size();
        int totalInFlight = dispatcher.getTotalInFlight();

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

        double avgQueueLength = (double) pendingCloudlets / activeVmCount;

        // Reset CPU to 0.0 when system has no pending queue and zero in-flight tasks
        double avgCpuUtilisation = 0.0;
        if (pendingCloudlets > 0 || totalInFlight > 0) {
            double totalCpuRatio = 0.0;
            for (Vm vm : activeVms) {
                int capacity = vm.getNumberOfPes() * SimulationConfig.CONCURRENCY_FACTOR;
                int inFlight = dispatcher.getInFlightCount(vm.getId());
                double ratio = Math.min(1.0, (double) inFlight / capacity);
                totalCpuRatio += ratio;
            }
            avgCpuUtilisation = totalCpuRatio / activeVmCount;
        }

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