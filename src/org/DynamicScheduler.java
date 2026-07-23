package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.Collections;
import java.util.List;

/**
 * DynamicScheduler.java
 * Thin adapter bridge between newly arrived dynamic cloudlets and AssignmentStrategy implementations.
 */
public class DynamicScheduler {

    private final AssignmentStrategy strategy;
    private final CloudSimGateway gateway;

    public DynamicScheduler(AssignmentStrategy strategy, CloudSimGateway gateway) {
        this.strategy = strategy;
        this.gateway = gateway;
    }

    /**
     * Schedules an arriving cloudlet by retrieving fresh active VMs from gateway,
     * delegating assignment, and submitting to execution.
     */
    public void scheduleArrival(Cloudlet cloudlet) {
        List<Vm> currentVms = gateway.getActiveVms();
        if (currentVms.isEmpty()) {
            throw new IllegalStateException("No active VMs available in CloudSimGateway for dynamic scheduling.");
        }

        List<Cloudlet> singletonList = Collections.singletonList(cloudlet);

        if (strategy instanceof RLStrategy) {
            ((RLStrategy) strategy).assignIncremental(cloudlet, currentVms);
        } else {
            strategy.assign(singletonList, currentVms);
        }

        gateway.submitCloudlet(cloudlet);
    }
}