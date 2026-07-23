package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.Collections;
import java.util.List;

public class DynamicScheduler {

    private final AssignmentStrategy strategy;
    private final CloudSimGateway gateway;

    public DynamicScheduler(AssignmentStrategy strategy, CloudSimGateway gateway) {
        this.strategy = strategy;
        this.gateway = gateway;
    }

    public void scheduleArrival(Cloudlet cloudlet) {
        List<Vm> activeVms = gateway.getActiveVms();
        if (activeVms == null || activeVms.isEmpty()) {
            throw new IllegalStateException("No active VMs available in CloudSimGateway for scheduling.");
        }

        if (strategy instanceof RLStrategy) {
            ((RLStrategy) strategy).assignIncremental(cloudlet, activeVms);
        } else {
            strategy.assign(Collections.singletonList(cloudlet), activeVms);
        }

        gateway.submitCloudlet(cloudlet);
    }
}