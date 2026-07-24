package org;

import org.cloudbus.cloudsim.Cloudlet;

/**
 * DynamicScheduler.java
 * Receives arrivals from WorkloadGenerator, enqueues them into PendingTaskQueue,
 * and triggers a TaskDispatcher drain pass.
 */
public class DynamicScheduler {

    private final PendingTaskQueue pendingQueue;
    private final TaskDispatcher dispatcher;

    public DynamicScheduler(PendingTaskQueue pendingQueue, TaskDispatcher dispatcher) {
        this.pendingQueue = pendingQueue;
        this.dispatcher = dispatcher;
    }

    public void scheduleArrival(Cloudlet cloudlet) {
        if (cloudlet == null) return;

        // Push to pending queue
        pendingQueue.add(cloudlet);

        // Attempt dispatch drain pass
        dispatcher.drainQueue();
    }
}