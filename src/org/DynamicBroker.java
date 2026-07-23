package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DynamicBroker.java
 *
 * Extension of classic CloudSim's DatacenterBroker allowing incremental, dynamic submission
 * of cloudlets and dynamic VM provisioning/destruction during runtime execution.
 */
public class DynamicBroker extends DatacenterBroker {

    private final List<Cloudlet> pendingCloudletQueue = new ArrayList<>();

    public DynamicBroker(String name) throws Exception {
        super(name);
    }

    /**
     * Dynamically registers and sends a newly scaled-up VM creation event to the Datacenter.
     * Uses VM_CREATE_ACK so Datacenter sends back an acknowledgment triggering processVmCreate().
     */
    public void registerAndCreateVm(Vm vm, int datacenterId) {
        getVmList().add(vm);
        getVmsToDatacentersMap().put(vm.getId(), datacenterId);

        // Send VM_CREATE_ACK so Datacenter sends an acknowledgment event back to the broker
        send(datacenterId, 0.0, CloudSimTags.VM_CREATE_ACK, vm);
    }

    /**
     * Dynamically sends a VM destruction event to the Datacenter.
     */
    public void destroyVm(Vm vm, int datacenterId) {
        getVmsCreatedList().remove(vm);
        send(datacenterId, 0.0, CloudSimTags.VM_DESTROY, vm);
    }

    /**
     * Dynamically submits a single cloudlet to a target VM during runtime.
     * Buffers cloudlets if VMs are not yet fully created by the Datacenter.
     */
    @SuppressWarnings("unchecked")
    public void submitDynamicCloudlet(Cloudlet cloudlet) {
        if (cloudlet == null) return;

        ((List<Cloudlet>) getCloudletList()).add(cloudlet);

        // If VMs are officially created in the Datacenter, submit immediately
        if (getVmsCreatedList().size() > 0) {
            int vmId = cloudlet.getVmId();
            Integer datacenterId = getVmsToDatacentersMap().get(vmId);
            if (datacenterId == null && !getDatacenterIdsList().isEmpty()) {
                datacenterId = getDatacenterIdsList().get(0);
            }

            if (datacenterId != null) {
                send(datacenterId, 0.0, CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
                ((List<Cloudlet>) getCloudletSubmittedList()).add(cloudlet);
            }
        } else {
            // Buffer cloudlet until VM creation handshake finishes
            pendingCloudletQueue.add(cloudlet);
        }
    }

    @Override
    protected void processVmCreate(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        int datacenterId = data[0];
        int vmId = data[1];
        int result = data[2];

        if (result == CloudSimTags.TRUE) {
            getVmsToDatacentersMap().put(vmId, datacenterId);

            Vm vm = null;
            for (Vm v : getVmList()) {
                if (v.getId() == vmId) {
                    vm = v;
                    break;
                }
            }

            if (vm != null && !getVmsCreatedList().contains(vm)) {
                getVmsCreatedList().add(vm);
                System.out.println("[DynamicBroker] Successfully created and registered active VM #" + vmId);
            }
        } else {
            System.err.println("[DynamicBroker] VM #" + vmId + " creation failed in Datacenter #" + datacenterId + " (out of host capacity)");
        }

        // Flush buffered cloudlets when active VMs are available
        if (getVmsCreatedList().size() > 0 && !pendingCloudletQueue.isEmpty()) {
            List<Cloudlet> toFlush = new ArrayList<>(pendingCloudletQueue);
            pendingCloudletQueue.clear();
            for (Cloudlet cl : toFlush) {
                submitDynamicCloudlet(cl);
            }
        }
    }

    /**
     * Safe unmodifiable view of active created VMs.
     */
    public List<Vm> getActiveVms() {
        return Collections.unmodifiableList(new ArrayList<>(getVmsCreatedList()));
    }

    /**
     * Safe unmodifiable view of initial submitted VMs.
     */
    @SuppressWarnings("unchecked")
    public List<Vm> getSubmittedVms() {
        return Collections.unmodifiableList(new ArrayList<>((List<Vm>) getVmList()));
    }

    /**
     * Safe unmodifiable view of completed cloudlets.
     */
    public List<Cloudlet> getCompletedCloudletList() {
        return Collections.unmodifiableList(new ArrayList<>(getCloudletReceivedList()));
    }

    /**
     * Estimates in-flight or waiting cloudlets registered at broker.
     */
    public int getInFlightCloudletCount() {
        int totalSubmitted = getCloudletSubmittedList().size() + pendingCloudletQueue.size();
        int totalReceived = getCloudletReceivedList().size();
        return Math.max(0, totalSubmitted - totalReceived);
    }
}