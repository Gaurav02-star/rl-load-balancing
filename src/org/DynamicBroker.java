package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DynamicBroker extends DatacenterBroker {

    private final List<Cloudlet> pendingCloudletQueue = new ArrayList<>();
    private TaskDispatcher dispatcher;

    public DynamicBroker(String name) throws Exception {
        super(name);
    }

    public void setTaskDispatcher(TaskDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void registerAndCreateVm(Vm vm, int datacenterId) {
        getVmList().add(vm);
        getVmsToDatacentersMap().put(vm.getId(), datacenterId);
        send(datacenterId, 0.0, CloudSimTags.VM_CREATE_ACK, vm);
    }

    public void destroyVm(Vm vm, int datacenterId) {
        getVmsCreatedList().remove(vm);
        send(datacenterId, 0.0, CloudSimTags.VM_DESTROY, vm);
    }

    @SuppressWarnings("unchecked")
    public void submitDynamicCloudlet(Cloudlet cloudlet) {
        if (cloudlet == null) return;

        ((List<Cloudlet>) getCloudletList()).add(cloudlet);

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
            pendingCloudletQueue.add(cloudlet);
        }
    }

    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        super.processCloudletReturn(ev);

        if (dispatcher != null) {
            dispatcher.onCloudletCompleted(cloudlet);
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

            // GUARD: Check if VM is already registered before calling dispatcher
            if (vm != null && !getVmsCreatedList().contains(vm)) {
                getVmsCreatedList().add(vm);
                System.out.println("[DynamicBroker] Successfully registered active VM #" + vmId);

                if (dispatcher != null) {
                    dispatcher.onVmCreated(vm);
                }
            }
        } else {
            System.err.println("[DynamicBroker] VM #" + vmId + " creation failed in Datacenter #" + datacenterId);
        }

        if (getVmsCreatedList().size() > 0 && !pendingCloudletQueue.isEmpty()) {
            List<Cloudlet> toFlush = new ArrayList<>(pendingCloudletQueue);
            pendingCloudletQueue.clear();
            for (Cloudlet cl : toFlush) {
                submitDynamicCloudlet(cl);
            }
        }
    }

    public List<Vm> getActiveVms() {
        return Collections.unmodifiableList(new ArrayList<>(getVmsCreatedList()));
    }

    public List<Cloudlet> getCompletedCloudletList() {
        return Collections.unmodifiableList(new ArrayList<>(getCloudletReceivedList()));
    }
}