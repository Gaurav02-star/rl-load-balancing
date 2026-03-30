package org;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MetricsCalculator.java
 *
 * Stateless utility that derives all evaluation metrics from a completed
 * {@link SimulationResult}. No CloudSim knowledge is required here — it
 * works purely with the numeric data carried by the finished cloudlets.
 *
 * All formulas assume submission time = 0 for every cloudlet.
 *
 * Public API: a single static method  {@link #compute(SimulationResult)}.
 */
public class MetricsCalculator {

    // Private constructor — this is a pure-static utility class.
    private MetricsCalculator() {}

    /**
     * Compute all metrics for one simulation run.
     *
     * @param result completed {@link SimulationResult} from {@link SimulationRunner}
     * @return fully populated {@link Metrics} object
     */
    public static Metrics compute(SimulationResult result) {
        List<Cloudlet> cloudlets = result.completedCloudlets;
        List<Vm>       vms       = result.vms;
        int n  = cloudlets.size();
        int nv = vms.size();

        // ── Build VM index map for O(1) lookup ────────────────────────────────
        // Maps CloudSim VM ID → position in vms list.
        Map<Integer, Integer> vmIdToIdx = new HashMap<>();
        for (int i = 0; i < nv; i++) {
            vmIdToIdx.put(vms.get(i).getId(), i);
        }

        // ── Accumulate per-VM actual execution time ───────────────────────────
        // CloudSim fills getActualCPUTime() after the run; we use that directly.
        double[] vmExecTime = new double[nv];
        for (Cloudlet cl : cloudlets) {
            int idx = vmIdToIdx.getOrDefault(cl.getVmId(), -1);
            if (idx >= 0) {
                vmExecTime[idx] += cl.getActualCPUTime();
            }
        }

        // ── A. Makespan ───────────────────────────────────────────────────────
        double makespan = 0;
        for (Cloudlet cl : cloudlets) {
            makespan = Math.max(makespan, cl.getFinishTime());
        }

        // ── B. Average Response Time  (submit=0 → response = startTime) ──────
        double sumStart = 0;
        for (Cloudlet cl : cloudlets) sumStart += cl.getExecStartTime();
        double avgResponseTime = sumStart / n;

        // ── C. Average Turnaround Time  (submit=0 → TAT = finishTime) ────────
        double sumFinish = 0;
        for (Cloudlet cl : cloudlets) sumFinish += cl.getFinishTime();
        double avgTurnaround = sumFinish / n;

        // ── D. Throughput ─────────────────────────────────────────────────────
        double throughput = (makespan > 0) ? (double) n / makespan : 0;

        // ── E. VM Utilisation ─────────────────────────────────────────────────
        // utilisation[i] = time VM i was actively executing / makespan
        double[] vmUtil = new double[nv];
        for (int i = 0; i < nv; i++) {
            vmUtil[i] = (makespan > 0) ? vmExecTime[i] / makespan : 0;
        }
        double avgUtil = mean(vmUtil);

        // ── F. Load Imbalance  (std-dev of per-VM execution times) ───────────
        double loadImbalanceSD = stdDev(vmExecTime);

        // ── G. Degree of Imbalance  = (Tmax − Tmin) / Tavg ──────────────────
        double tMax = max(vmExecTime);
        double tMin = min(vmExecTime);
        double tAvg = mean(vmExecTime);
        double di   = (tAvg > 0) ? (tMax - tMin) / tAvg : 0;

        // ── H. Energy Consumption ─────────────────────────────────────────────
        // For each VM: Energy (J) = [ util × MaxPower + (1−util) × IdlePower ] × makespan
        double totalEnergy = 0;
        for (int i = 0; i < nv; i++) {
            double u = vmUtil[i];
            totalEnergy += (u * SimulationConfig.MAX_POWER
                           + (1 - u) * SimulationConfig.IDLE_POWER)
                           * makespan;
        }

        return new Metrics(
            result.strategyName,
            makespan,
            avgResponseTime,
            avgTurnaround,
            throughput,
            vmUtil,
            avgUtil,
            loadImbalanceSD,
            di,
            totalEnergy
        );
    }

    // =========================================================================
    //  Private math helpers
    // =========================================================================

    private static double mean(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s / arr.length;
    }

    private static double stdDev(double[] arr) {
        double mu = mean(arr);
        double variance = 0;
        for (double v : arr) variance += (v - mu) * (v - mu);
        return Math.sqrt(variance / arr.length);
    }

    private static double max(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v > m) m = v;
        return m;
    }

    private static double min(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v < m) m = v;
        return m;
    }
}
