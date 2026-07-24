package org;

import org.cloudbus.cloudsim.Cloudlet;
import java.util.LinkedList;
import java.util.Queue;

/**
 * PendingTaskQueue.java
 * Holds cloudlets that have arrived but have not yet been bound to a VM.
 * Provides the real backpressure signal for autoscaling.
 */
public class PendingTaskQueue {

    private final Queue<Cloudlet> queue = new LinkedList<>();

    public synchronized void add(Cloudlet cloudlet) {
        if (cloudlet != null) {
            queue.add(cloudlet);
        }
    }

    public synchronized Cloudlet peek() {
        return queue.peek();
    }

    public synchronized Cloudlet poll() {
        return queue.poll();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized void clear() {
        queue.clear();
    }
}