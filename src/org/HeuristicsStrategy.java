package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.*;

/**
 * HeuristicsStrategy.java
 * Persistent Minimum Completion Time (MCT) and Round-Robin over capacity-eligible VMs.
 */
public class HeuristicsStrategy {

    public static class MCTStrategy implements AssignmentStrategy {
        private final Map<Integer, Double> vmFinishTimes = new HashMap<>();

        @Override
        public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
            if (cloudlets == null || cloudlets.isEmpty() || vms == null || vms.isEmpty()) return;

            for (Cloudlet cl : cloudlets) {
                int bestVmIdx = 0;
                double minCompletionTime = Double.MAX_VALUE;

                for (int i = 0; i < vms.size(); i++) {
                    Vm vm = vms.get(i);
                    double currentReady = vmFinishTimes.getOrDefault(vm.getId(), 0.0);
                    double execTime = (double) cl.getCloudletLength() / vm.getMips();
                    double completionTime = currentReady + execTime;

                    if (completionTime < minCompletionTime) {
                        minCompletionTime = completionTime;
                        bestVmIdx = i;
                    }
                }

                Vm chosenVm = vms.get(bestVmIdx);
                cl.setVmId(chosenVm.getId());
                double execTime = (double) cl.getCloudletLength() / chosenVm.getMips();
                vmFinishTimes.put(chosenVm.getId(), vmFinishTimes.getOrDefault(chosenVm.getId(), 0.0) + execTime);
            }
        }
    }

    public static class RoundRobinStrategy implements AssignmentStrategy {
        private int counter = 0;

        @Override
        public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
            if (cloudlets == null || cloudlets.isEmpty() || vms == null || vms.isEmpty()) return;

            for (Cloudlet cl : cloudlets) {
                int vmIdx = Math.abs(counter++) % vms.size();
                cl.setVmId(vms.get(vmIdx).getId());
            }
        }
    }
}