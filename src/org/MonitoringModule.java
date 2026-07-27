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
 *
 * Changes from original:
 *   1. Accepts an EMAPredictor parameter. Every tick, it feeds the current
 *      observed arrival rate into the predictor and embeds the resulting
 *      forecast into ClusterState.predictedArrivalRate.
 *   2. Uses the 12-argument ClusterState constructor (with predictedArrivalRate).
 *   3. All other logic is identical to the original.
 */
public class MonitoringModule extends SimEntity {

    public static final int EVENT_MONITOR_TICK = 8001;

    private final CloudSimGateway     gateway;
    private final PendingTaskQueue    pendingQueue;
    private final TaskDispatcher      dispatcher;
    private final WorkloadGenerator   workloadGenerator;
    private final VmLifecycleManager  lifecycleManager;
    private final Autoscaler          autoscaler;
    private final EMAPredictor        emaPredictor;       // NEW
    private final double              sampleInterval;
    private final double              windowSize;
    private final double              slaThreshold;
    private final List<ClusterState>  history = new ArrayList<>();

    private double       lastAutoscaleTime = 0.0;
    private double       allFinishedTime   = -1.0;
    private ClusterState latestState       = null;

    // ── Original constructor (no predictor) — kept for backward compatibility ─
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
        this(name, gateway, pendingQueue, dispatcher, workloadGenerator,
                lifecycleManager, autoscaler, sampleInterval, windowSize,
                slaThreshold, null);
    }

    // ── New constructor accepting EMAPredictor ────────────────────────────────
    public MonitoringModule(String name,
                            CloudSimGateway gateway,
                            PendingTaskQueue pendingQueue,
                            TaskDispatcher dispatcher,
                            WorkloadGenerator workloadGenerator,
                            VmLifecycleManager lifecycleManager,
                            Autoscaler autoscaler,
                            double sampleInterval,
                            double windowSize,
                            double slaThreshold,
                            EMAPredictor emaPredictor) {
        super(name);
        this.gateway           = gateway;
        this.pendingQueue      = pendingQueue;
        this.dispatcher        = dispatcher;
        this.workloadGenerator = workloadGenerator;
        this.lifecycleManager  = lifecycleManager;
        this.autoscaler        = autoscaler;
        this.emaPredictor      = emaPredictor;
        this.sampleInterval    = sampleInterval;
        this.windowSize        = windowSize;
        this.slaThreshold      = slaThreshold;
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

            if (autoscaler != null && lifecycleManager != null) {
                if (currentTime - lastAutoscaleTime >= SimulationConfig.AUTOSCALING_INTERVAL) {
                    AutoscalerAction action = autoscaler.evaluateScaling(latestState);
                    if      (action == AutoscalerAction.SCALE_UP)   lifecycleManager.scaleUp();
                    else if (action == AutoscalerAction.SCALE_DOWN) lifecycleManager.scaleDown();
                    lastAutoscaleTime = currentTime;
                }
            }

            long totalArrivals   = workloadGenerator.getTotalArrivals();
            List<Cloudlet> completed = gateway.getCompletedCloudlets();
            int  pendingInQueue  = pendingQueue.size();
            int  inFlight        = dispatcher.getTotalInFlight();

            boolean tasksFinished = (totalArrivals > 0)
                    && (completed.size() >= totalArrivals)
                    && (pendingInQueue == 0)
                    && (inFlight == 0);

            if (tasksFinished && allFinishedTime < 0) {
                allFinishedTime = currentTime;
            }

            boolean gracePeriodActive = (allFinishedTime > 0)
                    && ((currentTime - allFinishedTime) <= 15.0);
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
        List<Vm> activeVms    = gateway.getActiveVms();
        int      activeVmCount = Math.max(1, activeVms.size());

        Map<Integer, Double> arrivalTimes  = workloadGenerator.getCloudletArrivalTimes();
        List<Cloudlet> completedList       = gateway.getCompletedCloudlets();
        long  totalArrivals                = workloadGenerator.getTotalArrivals();
        int   pendingCloudlets             = pendingQueue.size();
        int   totalInFlight                = dispatcher.getTotalInFlight();

        double windowStart     = Math.max(0.0, currentTime - windowSize);
        double effectiveWindow = Math.max(1.0e-3, currentTime - windowStart);

        long arrivalsInWindow = 0;
        for (Double arrTime : arrivalTimes.values()) {
            if (arrTime >= windowStart && arrTime <= currentTime) arrivalsInWindow++;
        }
        double arrivalRate = arrivalsInWindow / effectiveWindow;

        long   completionsInWindow   = 0;
        double sumResponseTime       = 0.0;
        long   slaViolationsInWindow = 0;

        for (Cloudlet c : completedList) {
            double finishTime = c.getFinishTime();
            if (finishTime >= windowStart && finishTime <= currentTime) {
                completionsInWindow++;
                Double arrTime  = arrivalTimes.get(c.getCloudletId());
                double arrival  = (arrTime != null) ? arrTime : c.getSubmissionTime();
                double respTime = Math.max(0.0, c.getExecStartTime() - arrival);
                sumResponseTime += respTime;
                if (respTime > slaThreshold) slaViolationsInWindow++;
            }
        }

        double throughput      = completionsInWindow / effectiveWindow;
        double avgResponseTime = (completionsInWindow > 0) ? (sumResponseTime / completionsInWindow) : 0.0;
        double slaViolationRate = (completionsInWindow > 0) ? ((double) slaViolationsInWindow / completionsInWindow) : 0.0;
        double avgQueueLength  = (double) pendingCloudlets / activeVmCount;

        double avgCpuUtilisation = 0.0;
        if (pendingCloudlets > 0 || totalInFlight > 0) {
            double totalCpuRatio = 0.0;
            for (Vm vm : activeVms) {
                int    capacity = vm.getNumberOfPes() * SimulationConfig.CONCURRENCY_FACTOR;
                int    inFlight = dispatcher.getInFlightCount(vm.getId());
                double ratio    = Math.min(1.0, (double) inFlight / capacity);
                totalCpuRatio += ratio;
            }
            avgCpuUtilisation = totalCpuRatio / activeVmCount;
        }

        // ── Feed EMA predictor and get forecast ───────────────────────────────
        double predictedArrivalRate = 0.0;
        if (emaPredictor != null) {
            emaPredictor.update(arrivalRate);
            predictedArrivalRate = emaPredictor.predict();
        }

        // ── Build ClusterState with prediction embedded ───────────────────────
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
                completedList.size(),
                pendingCloudlets,
                predictedArrivalRate   // <-- new 12th argument
        );

        latestState = snapshot;
        history.add(snapshot);
    }

    public ClusterState      getCurrentState() { return latestState; }
    public List<ClusterState> getHistory()     { return Collections.unmodifiableList(new ArrayList<>(history)); }
}