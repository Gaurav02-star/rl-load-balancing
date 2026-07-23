package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * CloudSimGateway.java
 * Isolates direct communication with classic CloudSim runtime state during dynamic execution.
 */
public class CloudSimGateway {

    private final DynamicBroker broker;

    public CloudSimGateway(DynamicBroker broker) {
        this.broker = broker;
    }

    /**
     * Returns active created VMs if available; falls back to submitted VMs if at simulation start (t = 0.0).
     */
    public List<Vm> getActiveVms() {
        List<Vm> createdVms = broker.getActiveVms();
        if (!createdVms.isEmpty()) {
            return createdVms;
        }
        // Fallback for t = 0.0 before Datacenter VM creation event completes
        return broker.getSubmittedVms();
    }

    public void submitCloudlet(Cloudlet cloudlet) {
        broker.submitDynamicCloudlet(cloudlet);
    }

    public List<Cloudlet> getCompletedCloudlets() {
        return broker.getCompletedCloudletList();
    }

    public int getInFlightCloudletCount() {
        return broker.getInFlightCloudletCount();
    }

    public DynamicBroker getBroker() {
        return broker;
    }
}