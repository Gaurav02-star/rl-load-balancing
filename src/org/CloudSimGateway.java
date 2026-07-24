package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * CloudSimGateway.java
 * Abstraction layer connecting the scheduler, broker, and task dispatcher.
 */
public class CloudSimGateway {

    private final DynamicBroker broker;
    private TaskDispatcher dispatcher;

    public CloudSimGateway(DynamicBroker broker) {
        this.broker = broker;
    }

    public void setTaskDispatcher(TaskDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public List<Vm> getActiveVms() {
        return broker.getActiveVms();
    }

    public void submitCloudletDirectly(Cloudlet cloudlet) {
        broker.submitDynamicCloudlet(cloudlet);
    }

    public List<Cloudlet> getCompletedCloudlets() {
        return broker.getCompletedCloudletList();
    }

    public int getInFlightCloudletCount() {
        if (dispatcher != null) {
            return dispatcher.getTotalInFlight();
        }
        return 0;
    }

    public DynamicBroker getBroker() {
        return broker;
    }
}