package org;

import org.cloudbus.cloudsim.Cloudlet;

import java.text.DecimalFormat;
import java.util.List;

/**
 * ResultPrinter.java
 *
 * Handles ALL console output for the comparison run.
 * Keeping formatting here means the simulation, metrics, and strategy
 * classes never contain print statements — they stay purely computational.
 *
 * Public methods (called from Main):
 *   printCloudletTable(SimulationResult)   – per-cloudlet execution table
 *   printMetrics(Metrics)                  – all 8 metrics for one run
 *   printComparisonTable(List<Metrics>)    – summary table across all runs
 */
public class ResultPrinter {

    private static final DecimalFormat DF4 = new DecimalFormat("0.0000");
    private static final DecimalFormat DF2 = new DecimalFormat("0.00");

    // Private constructor — pure static utility.
    private ResultPrinter() {}

    // =========================================================================
    //  Section header
    // =========================================================================

    public static void printSectionHeader(String strategyName) {
        System.out.println("\n");
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.printf ("  Strategy: %s%n", strategyName);
        System.out.println("══════════════════════════════════════════════════════════");
    }

    // =========================================================================
    //  Cloudlet execution table
    // =========================================================================

    /**
     * Prints the per-cloudlet execution table for one simulation run.
     * Mirrors the original printCloudletList() output but with cleaner
     * column alignment.
     */
    public static void printCloudletTable(SimulationResult result) {
        List<Cloudlet> list = result.completedCloudlets;

        String row = "+-------------+-------+------------+-------------+-----------+";
        System.out.println("\n  Cloudlet Execution Table");
        System.out.println(row);
        System.out.printf("| %-11s | %-5s | %-10s | %-11s | %-9s |%n",
                "Cloudlet ID", "VM ID", "Start Time", "Finish Time", "Exec Time");
        System.out.println(row);

        for (Cloudlet cl : list) {
            if (cl.getCloudletStatus() == Cloudlet.SUCCESS) {
                System.out.printf("| %-11d | %-5d | %-10s | %-11s | %-9s |%n",
                        cl.getCloudletId(),
                        cl.getVmId(),
                        DF2.format(cl.getExecStartTime()),
                        DF2.format(cl.getFinishTime()),
                        DF2.format(cl.getActualCPUTime()));
            }
        }
        System.out.println(row);
    }

    // =========================================================================
    //  Per-run metrics block
    // =========================================================================

    /** Prints all 8 metrics for one completed simulation run. */
    public static void printMetrics(Metrics m) {
        System.out.println("\n  Metrics");
        System.out.println("  ───────────────────────────────────────────");
        System.out.printf("  Makespan                 : %s s%n",     DF4.format(m.makespan));
        System.out.printf("  Avg Response Time        : %s s%n",     DF4.format(m.avgResponseTime));
        System.out.printf("  Avg Turnaround Time      : %s s%n",     DF4.format(m.avgTurnaround));
        System.out.printf("  Throughput               : %s tasks/s%n",DF4.format(m.throughput));

        System.out.println("  VM Utilisation");
        for (int i = 0; i < m.vmUtilisation.length; i++) {
            System.out.printf("      VM %-3d : %s%n", i, DF4.format(m.vmUtilisation[i]));
        }
        System.out.printf("    Average Utilisation    : %s%n",       DF4.format(m.avgUtilisation));
        System.out.printf("  Load Imbalance (Std Dev) : %s s%n",     DF4.format(m.loadImbalanceSD));
        System.out.printf("  Degree of Imbalance (DI) : %s%n",       DF4.format(m.degreeOfImbalance));
        System.out.printf("  Total Energy             : %s J%n",     DF4.format(m.totalEnergy));
        System.out.println("  ───────────────────────────────────────────");
    }

    // =========================================================================
    //  Comparison summary table (bonus)
    // =========================================================================

    /**
     * Prints a single-glance comparison table across all strategies.
     * Column widths are fixed at 12 characters to keep the table readable
     * with up to 6 strategies side-by-side.
     */
    public static void printComparisonTable(List<Metrics> allMetrics) {
        int colW = 14; // column width for numeric cells
        int nameW = 16; // width of the strategy-name column

        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                       COMPARISON SUMMARY                                                                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");

        // Header row
        System.out.printf("║ %-" + nameW + "s", "Strategy");
        String[] cols = {
            "Makespan(s)", "AvgRT(s)", "AvgTAT(s)",
            "Throughput", "AvgUtil", "ImbalSD(s)", "DI", "Energy(J)"
        };
        for (String col : cols) {
            System.out.printf(" %-" + colW + "s", col);
        }
        System.out.println(" ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");

        // Data rows
        for (Metrics m : allMetrics) {
            System.out.printf("║ %-" + nameW + "s", m.strategyName);
            System.out.printf(" %-" + colW + "s", DF4.format(m.makespan));
            System.out.printf(" %-" + colW + "s", DF4.format(m.avgResponseTime));
            System.out.printf(" %-" + colW + "s", DF4.format(m.avgTurnaround));
            System.out.printf(" %-" + colW + "s", DF4.format(m.throughput));
            System.out.printf(" %-" + colW + "s", DF4.format(m.avgUtilisation));
            System.out.printf(" %-" + colW + "s", DF4.format(m.loadImbalanceSD));
            System.out.printf(" %-" + colW + "s", DF4.format(m.degreeOfImbalance));
            System.out.printf(" %-" + colW + "s", DF4.format(m.totalEnergy));
            System.out.println(" ║");
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝");

        // Legend
        System.out.println();
        System.out.println("  Legend");
        System.out.println("  AvgRT    = Avg Response Time   (avg start time,  submission = 0)");
        System.out.println("  AvgTAT   = Avg Turnaround Time (avg finish time, submission = 0)");
        System.out.println("  Throughput = completed cloudlets / makespan");
        System.out.println("  AvgUtil  = average VM utilisation across all VMs");
        System.out.println("  ImbalSD  = std-dev of per-VM total execution times");
        System.out.println("  DI       = Degree of Imbalance = (Tmax - Tmin) / Tavg");
        System.out.println("  Energy   = Σ [ (util×200W + (1-util)×100W) × makespan ] per VM");
    }
}
