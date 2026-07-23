package org;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Vm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VmLifecycleManager.java
 * Handles dynamic VM creation and destruction inside classic CloudSim at runtime with recycled VM IDs.
 */
public class VmLifecycleManager {

    private final DynamicBroker broker;
    private final int datacenterId;

    public VmLifecycleManager(DynamicBroker broker, int datacenterId, int initialVmCount) {
        this.broker = broker;
        this.datacenterId = datacenterId;
    }

    /**
     * Dynamically provisions a new VM by recycling unused VM ID slots up to MAX_VMS.
     */
    public Vm scaleUp() {
        List<Vm> activeVms = broker.getActiveVms();
        if (activeVms.size() >= SimulationConfig.MAX_VMS) {
            return null; // Strict MAX_VMS cap
        }

        // Find smallest available VM ID slot between 0 and MAX_VMS - 1
        Set<Integer> activeIds = new HashSet<>();
        for (Vm v : activeVms) {
            activeIds.add(v.getId());
        }

        int targetVmId = -1;
        for (int id = 0; id < SimulationConfig.MAX_VMS; id++) {
            if (!activeIds.contains(id)) {
                targetVmId = id;
                break;
            }
        }

        if (targetVmId == -1) return null;

        int mips = SimulationConfig.VM_MIPS_VALUES[targetVmId % SimulationConfig.VM_MIPS_VALUES.length];

        Vm newVm = new Vm(
                targetVmId,
                broker.getId(),
                mips,
                SimulationConfig.VM_PES,
                SimulationConfig.VM_RAM,
                SimulationConfig.VM_BW,
                SimulationConfig.VM_SIZE,
                SimulationConfig.VM_VMM,
                new CloudletSchedulerTimeShared()
        );

        broker.registerAndCreateVm(newVm, datacenterId);
        return newVm;
    }

    /**
     * Safely de-provisions an active VM with the smallest load.
     */
    public Vm scaleDown() {
        List<Vm> activeVms = broker.getActiveVms();
        if (activeVms.size() <= SimulationConfig.MIN_VMS) {
            return null; // Strict MIN_VMS floor
        }

        Vm targetVm = activeVms.get(activeVms.size() - 1);
        int minTasks = Integer.MAX_VALUE;

        for (Vm vm : activeVms) {
            int taskCount = vm.getCloudletScheduler().runningCloudlets();
            if (taskCount < minTasks) {
                minTasks = taskCount;
                targetVm = vm;
            }
        }

        broker.destroyVm(targetVm, datacenterId);
        return targetVm;
    }
}