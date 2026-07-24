package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskDispatcher.java
 * Capacity-gated dispatch loop with re-entrancy protection against event recursion loops.
 */
public class TaskDispatcher {

    private final PendingTaskQueue pendingQueue;
    private final CloudSimGateway gateway;
    private final AssignmentStrategy strategy;

    private final Map<Integer, Integer> inFlightMap = new ConcurrentHashMap<>();
    private boolean isDraining = false; // Re-entrancy guard flag

    public TaskDispatcher(PendingTaskQueue pendingQueue, CloudSimGateway gateway, AssignmentStrategy strategy) {
        this.pendingQueue = pendingQueue;
        this.gateway = gateway;
        this.strategy = strategy;
    }

    public synchronized void drainQueue() {
        if (isDraining || pendingQueue.isEmpty()) return;

        isDraining = true;
        try {
            List<Vm> activeVms = gateway.getActiveVms();
            if (activeVms == null || activeVms.isEmpty()) return;

            List<Vm> eligibleVms = getEligibleVms(activeVms);

            while (!pendingQueue.isEmpty() && !eligibleVms.isEmpty()) {
                Cloudlet headTask = pendingQueue.peek();

                Vm selectedVm = selectVmForTask(headTask, eligibleVms);
                if (selectedVm == null) break;

                pendingQueue.poll();
                headTask.setVmId(selectedVm.getId());

                int vmId = selectedVm.getId();
                inFlightMap.put(vmId, inFlightMap.getOrDefault(vmId, 0) + 1);

                gateway.submitCloudletDirectly(headTask);

                eligibleVms = getEligibleVms(activeVms);
            }
        } finally {
            isDraining = false;
        }
    }

    public synchronized void onCloudletCompleted(Cloudlet cloudlet) {
        if (cloudlet == null) return;
        int vmId = cloudlet.getVmId();
        int count = inFlightMap.getOrDefault(vmId, 0);
        if (count > 0) {
            inFlightMap.put(vmId, count - 1);
        }
        drainQueue();
    }

    public synchronized void onVmCreated(Vm vm) {
        if (vm != null) {
            inFlightMap.putIfAbsent(vm.getId(), 0);
        }
        drainQueue();
    }

    private Vm selectVmForTask(Cloudlet cloudlet, List<Vm> eligibleVms) {
        if (eligibleVms.isEmpty()) return null;

        if (strategy instanceof RLStrategy) {
            ((RLStrategy) strategy).assignIncremental(cloudlet, eligibleVms);
            int targetId = cloudlet.getVmId();
            for (Vm v : eligibleVms) {
                if (v.getId() == targetId) return v;
            }
            return eligibleVms.get(0);
        } else {
            strategy.assign(Collections.singletonList(cloudlet), eligibleVms);
            int targetId = cloudlet.getVmId();
            for (Vm v : eligibleVms) {
                if (v.getId() == targetId) return v;
            }
            return eligibleVms.get(0);
        }
    }

    private List<Vm> getEligibleVms(List<Vm> activeVms) {
        List<Vm> eligible = new ArrayList<>();
        for (Vm vm : activeVms) {
            int capacity = vm.getNumberOfPes() * SimulationConfig.CONCURRENCY_FACTOR;
            int currentInFlight = inFlightMap.getOrDefault(vm.getId(), 0);
            if (currentInFlight < capacity) {
                eligible.add(vm);
            }
        }
        return eligible;
    }

    public int getInFlightCount(int vmId) {
        return inFlightMap.getOrDefault(vmId, 0);
    }

    public int getTotalInFlight() {
        int sum = 0;
        for (int count : inFlightMap.values()) sum += count;
        return sum;
    }

    public Map<Integer, Integer> getInFlightMap() {
        return Collections.unmodifiableMap(inFlightMap);
    }
}