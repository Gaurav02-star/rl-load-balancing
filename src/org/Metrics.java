package org;

/**
 * Metrics.java
 *
 * Immutable value object holding every evaluation metric for one
 * simulation run. Produced by {@link MetricsCalculator#compute}.
 *
 * Definitions (submission time = 0 for all cloudlets)
 * ────────────────────────────────────────────────────
 *  makespan          max(finishTime)                             seconds
 *  avgResponseTime   mean(startTime)    ← response = start − submit
 *  avgTurnaround     mean(finishTime)   ← TAT      = finish − submit
 *  throughput        completedCloudlets / makespan               tasks/s
 *  vmUtilisation[]   execTime[vm] / makespan  (one entry per VM)
 *  avgUtilisation    mean(vmUtilisation[])
 *  loadImbalanceSD   std-dev of per-VM total execution times     seconds
 *  degreeOfImbalance (Tmax − Tmin) / Tavg                       dimensionless
 *  totalEnergy       Σ_vm [(util×MaxPower + (1−util)×IdlePower) × makespan]  Joules
 */
public class Metrics {

    public final String strategyName;

    // ── Core timing ───────────────────────────────────────────────────────────
    public final double makespan;
    public final double avgResponseTime;
    public final double avgTurnaround;
    public final double throughput;

    // ── Utilisation ───────────────────────────────────────────────────────────
    public final double[] vmUtilisation;   // one entry per VM, same order as vmList
    public final double   avgUtilisation;

    // ── Balance ───────────────────────────────────────────────────────────────
    public final double loadImbalanceSD;   // std-dev of per-VM execution times
    public final double degreeOfImbalance; // (Tmax - Tmin) / Tavg

    // ── Energy ────────────────────────────────────────────────────────────────
    public final double totalEnergy;       // Joules

    /** All fields set by {@link MetricsCalculator}; not for external construction. */
    Metrics(String strategyName,
            double makespan,
            double avgResponseTime,
            double avgTurnaround,
            double throughput,
            double[] vmUtilisation,
            double avgUtilisation,
            double loadImbalanceSD,
            double degreeOfImbalance,
            double totalEnergy) {

        this.strategyName      = strategyName;
        this.makespan          = makespan;
        this.avgResponseTime   = avgResponseTime;
        this.avgTurnaround     = avgTurnaround;
        this.throughput        = throughput;
        this.vmUtilisation     = vmUtilisation;
        this.avgUtilisation    = avgUtilisation;
        this.loadImbalanceSD   = loadImbalanceSD;
        this.degreeOfImbalance = degreeOfImbalance;
        this.totalEnergy       = totalEnergy;
    }
}
