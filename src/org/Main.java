package org;

import java.util.ArrayList;
import java.util.List;

/**
 * Main.java  –  Comparison Driver
 *
 * The ONLY file you touch when adding or removing strategies.
 *
 * Each call to runOne() is completely independent:
 *   fresh CloudSim init  →  fresh datacenter, VMs, cloudlets
 *   →  strategy applied  →  results collected  →  metrics printed
 *
 * Adding a new heuristic
 * ──────────────────────
 *   1. Create  YourStrategy.java  implementing AssignmentStrategy
 *   2. Add one line here:  runOne(new YourStrategy(), "Your Strategy", results)
 *   That's it. Nothing else changes.
 */
public class Main {

    public static void main(String[] args) {

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║    CloudSim Load Balancing – Strategy Comparison     ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("  VMs: %d   |   Cloudlets: %d%n",
                SimulationConfig.NUM_VMS, SimulationConfig.NUM_CLOUDLETS);

        // Accumulate Metrics objects for the final comparison table.
        List<Metrics> allResults = new ArrayList<>();

        // ── Run each strategy ────────────────────────────────────────────────
        // Order here is purely cosmetic; each run is isolated.

        runOne(new FCFSStrategy(),        "FCFS",          allResults);
        runOne(new RoundRobinStrategy(),  "Round Robin",   allResults);
        runOne(new LeastLoadedStrategy(), "Least Loaded",  allResults);
        runOne(new MinMinStrategy(),      "Min-Min",       allResults);
        runOne(new MaxMinStrategy(),      "Max-Min",       allResults);
        runOne(new RLStrategy(),          "RL (stub)",     allResults);

        // ── Final comparison table ────────────────────────────────────────────
        ResultPrinter.printComparisonTable(allResults);
    }

    // =========================================================================
    //  runOne — orchestrates a single strategy's full lifecycle
    // =========================================================================

    /**
     * Run one simulation, compute metrics, print results, accumulate summary.
     *
     * @param strategy   the load-balancing algorithm to evaluate
     * @param name       display label used in all output
     * @param allResults list to append this run's {@link Metrics} to
     */
    private static void runOne(AssignmentStrategy strategy,
                               String name,
                               List<Metrics> allResults) {
        ResultPrinter.printSectionHeader(name);

        try {
            // 1. Run a completely fresh simulation with this strategy
            SimulationResult result = SimulationRunner.run(strategy, name);

            // 2. Print the per-cloudlet execution table
            ResultPrinter.printCloudletTable(result);

            // 3. Compute all 8 metrics from the raw CloudSim output
            Metrics metrics = MetricsCalculator.compute(result);

            // 4. Print the metrics block for this strategy
            ResultPrinter.printMetrics(metrics);

            // 5. Save for comparison table
            allResults.add(metrics);

        } catch (RuntimeException e) {
            System.err.println("  [ERROR] Simulation failed for strategy: " + name);
            e.printStackTrace();
        }
    }
}
