package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FCFSStrategy.java  –  First Come First Serve
 *
 * Algorithm
 * ─────────
 * Assign cloudlets in their natural arrival order, cycling through VMs:
 *
 *   cloudlet[0] → VM[0]
 *   cloudlet[1] → VM[1]
 *   cloudlet[2] → VM[2]
 *   cloudlet[3] → VM[3]
 *   cloudlet[4] → VM[0]   ← wraps around
 *   ...
 *
 * No load information is consulted; the order of submission alone
 * determines placement. This serves as the baseline for comparison.
 *
 * Complexity: O(n)
 */
class FCFSStrategy implements AssignmentStrategy {

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        int numVms = vms.size();
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVmId(vms.get(i % numVms).getId());
        }
    }
}

/**
 * RoundRobinStrategy.java  –  Round Robin
 *
 * Algorithm
 * ─────────
 * Distribute cloudlets evenly across VMs in a circular, repeating pattern.
 * The difference from FCFS becomes meaningful when the VM list is dynamic
 * (e.g. VMs added/removed mid-run) or when the starting offset varies.
 * In a static setup with the same inputs it produces the same mapping as
 * FCFS, giving a clean apples-to-apples baseline for the load-aware algos.
 *
 *   cloudlet[0] → VM[ 0 % numVms ]
 *   cloudlet[1] → VM[ 1 % numVms ]
 *   ...
 *   cloudlet[k] → VM[ k % numVms ]
 *
 * Complexity: O(n)
 */
class RoundRobinStrategy implements AssignmentStrategy {

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        int numVms = vms.size();
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVmId(vms.get(i % numVms).getId());
        }
    }
}

/**
 * LeastLoadedStrategy.java  –  Greedy Least-Loaded (online)
 * <p>
 * Algorithm
 * ─────────
 * Process cloudlets in their arrival order (no sorting).
 * For each cloudlet, assign it to the VM that currently has the
 * smallest total estimated execution time:
 * <p>
 * 1. For each VM maintain:  vmLoad[i] = Σ (cloudlet.length / vm.mips)
 * 2. Pick  bestVm = argmin(vmLoad)
 * 3. Assign cloudlet → bestVm
 * 4. Update vmLoad[bestVm] += cloudlet.length / bestVm.mips
 * <p>
 * This is the online, order-sensitive counterpart to Min-Min.
 * It reacts to actual submission order rather than pre-sorting by length,
 * so it behaves differently on heterogeneous or bursty workloads.
 * <p>
 * Load unit: seconds of estimated execution time (MI / MIPS).
 * Using time (not raw MI) makes the model correct when VMs
 * have different MIPS ratings.
 * <p>
 * Complexity: O(n × m)  where n = cloudlets, m = VMs
 */

class LeastLoadedStrategy implements AssignmentStrategy {

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        // Estimated completion time accumulated per VM (seconds)
        double[] vmLoad = new double[vms.size()];

        for (Cloudlet cl : cloudlets) {
            int bestIdx = indexOfMin(vmLoad);
            cl.setVmId(vms.get(bestIdx).getId());

            // Update load: execution time = length (MI) / speed (MIPS)
            double execTime = (double) cl.getCloudletLength()
                    / vms.get(bestIdx).getMips();
            vmLoad[bestIdx] += execTime;
        }
    }

    /**
     * Returns the index of the smallest element in {@code arr}.
     */
    private int indexOfMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[idx]) idx = i;
        }
        return idx;
    }
}

/**
 * MinMinStrategy.java  –  Min-Min
 *
 * Algorithm
 * ─────────
 * 1. Sort ALL cloudlets ascending by length  (shortest first).
 * 2. For each cloudlet (in that sorted order):
 *      a. Find the VM with the minimum current accumulated load.
 *      b. Assign this cloudlet to that VM.
 *      c. Add the cloudlet's estimated execution time to that VM's load.
 *
 * Intuition
 * ─────────
 * Short tasks complete quickly and "fill gaps" between longer tasks,
 * keeping VMs busy. Min-Min generally achieves low makespan when the
 * workload has many short tasks mixed with a few long ones.
 *
 * The original cloudlet list order is NOT modified — a separate sorted
 * working copy is used so the broker receives cloudlets in submission order.
 *
 * Load unit: seconds of estimated execution time (MI / MIPS).
 *
 * Complexity: O(n log n + n × m)
 */
class MinMinStrategy implements AssignmentStrategy {

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] vmLoad = new double[vms.size()];

        // Sort a working copy shortest → longest; original list untouched.
        List<Cloudlet> sorted = new ArrayList<>(cloudlets);
        sorted.sort(Comparator.comparingLong(Cloudlet::getCloudletLength));

        for (Cloudlet cl : sorted) {
            int bestIdx = indexOfMin(vmLoad);
            cl.setVmId(vms.get(bestIdx).getId());

            double execTime = (double) cl.getCloudletLength()
                    / vms.get(bestIdx).getMips();
            vmLoad[bestIdx] += execTime;
        }
    }

    private int indexOfMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[idx]) idx = i;
        }
        return idx;
    }
}

/**
 * MaxMinStrategy.java  –  Max-Min
 *
 * Algorithm
 * ─────────
 * 1. Sort ALL cloudlets descending by length  (longest first).
 * 2. For each cloudlet (in that sorted order):
 *      a. Find the VM with the minimum current accumulated load.
 *      b. Assign this cloudlet to that VM.
 *      c. Add the cloudlet's estimated execution time to that VM's load.
 *
 * Intuition
 * ─────────
 * Placing large tasks first spreads the heavy load across VMs early.
 * Subsequent small tasks then fill remaining capacity precisely,
 * which tends to produce better load balance (lower std-dev) than
 * Min-Min — at the cost of potentially higher makespan when very large
 * tasks must be serialised on a single VM.
 *
 * Load unit: seconds of estimated execution time (MI / MIPS).
 *
 * Complexity: O(n log n + n × m)
 */
class MaxMinStrategy implements AssignmentStrategy {

    @Override
    public void assign(List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] vmLoad = new double[vms.size()];

        // Sort a working copy longest → shortest; original list untouched.
        List<Cloudlet> sorted = new ArrayList<>(cloudlets);
        sorted.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

        for (Cloudlet cl : sorted) {
            int bestIdx = indexOfMin(vmLoad);
            cl.setVmId(vms.get(bestIdx).getId());

            double execTime = (double) cl.getCloudletLength()
                    / vms.get(bestIdx).getMips();
            vmLoad[bestIdx] += execTime;
        }
    }

    private int indexOfMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[idx]) idx = i;
        }
        return idx;
    }
}





