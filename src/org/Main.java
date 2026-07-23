package org;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int TRAINING_EPISODES = 500; // Fast training pass
    private static final int LOG_INTERVAL      = 100;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║    CloudSim Resource Management – Full System Run    ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        if (SimulationConfig.SIMULATION_MODE == SimulationMode.DYNAMIC) {
            runDynamicPipeline();
        } else {
            runStaticPipeline();
        }
    }

    private static void runStaticPipeline() {
        System.out.printf("  Mode: STATIC | VMs: %d | Cloudlets: %d%n%n",
                SimulationConfig.NUM_VMS, SimulationConfig.NUM_CLOUDLETS);

        List<Metrics> allResults = new ArrayList<>();

        System.out.println("[ Heuristics ]");
        runOneStatic(new FCFSStrategy(),        "FCFS",         allResults);
        runOneStatic(new RoundRobinStrategy(),  "Round Robin",  allResults);
        runOneStatic(new LeastLoadedStrategy(), "Least Loaded", allResults);
        runOneStatic(new MinMinStrategy(),      "Min-Min",      allResults);
        runOneStatic(new MaxMinStrategy(),      "Max-Min",      allResults);

        RLStrategy rl = new RLStrategy();

        System.out.printf("%n[ SARSA(λ) Training — %d episodes ]%n", TRAINING_EPISODES);
        System.out.printf("  %-8s  %-8s  %-10s  %-10s%n",
                "Episode", "Epsilon", "Q-States", "Traces");
        System.out.println("  " + "─".repeat(42));

        for (int ep = 1; ep <= TRAINING_EPISODES; ep++) {
            SimulationRunner.run(rl, "Training");

            if (ep == 1 || ep % LOG_INTERVAL == 0) {
                System.out.printf("  %-8d  %-8.4f  %-10d  %-10d%n",
                        ep,
                        rl.getEpsilon(),
                        rl.getQTableSize(),
                        rl.getTraceSize());
            }
        }

        System.out.println("  " + "─".repeat(42));
        System.out.printf("  Training complete.  ε=%.4f  Q-states=%d%n%n",
                rl.getEpsilon(), rl.getQTableSize());

        rl.setEpsilon(0.0);
        System.out.println("[ SARSA(λ) Final Evaluation ]");
        runOneStatic(rl, "RL SARSA(λ)", allResults);

        ResultPrinter.printComparisonTable(allResults);
    }

    private static void runDynamicPipeline() {
        System.out.printf("  Mode: DYNAMIC | Duration: %.0fs | Arrival Rate: %.1f tasks/s%n%n",
                SimulationConfig.WORKLOAD_DURATION, SimulationConfig.MEAN_ARRIVAL_RATE);

        // 1. Train Load Balancer
        System.out.println("[ 1/2 Training RL Load Balancer... ]");
        RLStrategy rlLoadBalancer = new RLStrategy();
        for (int ep = 1; ep <= TRAINING_EPISODES; ep++) {
            SimulationRunner.run(rlLoadBalancer, "Training");
        }
        rlLoadBalancer.setEpsilon(0.0);
        System.out.println("  ✔ RL Load Balancer trained successfully.");

        // 2. Instantiate RL Autoscaler
        RLAutoscaler rlAutoscaler = new RLAutoscaler();

        System.out.println("\n[ 2/2 Phase 3 Dynamic System Comparison ]");

        // Run Baseline 1: Static Infrastructure (No Autoscaling)
        runOneDynamic(rlLoadBalancer, "No Autoscaling (Static 4 VMs)", null);

        // Run Baseline 2: Rule-Based Threshold Autoscaler
        runOneDynamic(rlLoadBalancer, "Rule-Based Threshold Autoscaler", new ThresholdAutoscaler());

        // Run Experiment: Dual-RL (RL Load Balancer + RL Autoscaler)
        runOneDynamic(rlLoadBalancer, "Dual-RL (Load Balancer + RL Autoscaler)", rlAutoscaler);
    }

    private static void runOneDynamic(AssignmentStrategy strategy, String name, Autoscaler autoscaler) {
        DynamicSimulationResult res = SimulationRunner.runDynamic(strategy, name, autoscaler);
        ResultPrinter.printDynamic(res);
        System.gc(); // Force JVM cleanup after each run
    }

    private static void runOneStatic(AssignmentStrategy strategy,
                                     String name, List<Metrics> results) {
        ResultPrinter.printSectionHeader(name);
        try {
            SimulationResult result = SimulationRunner.run(strategy, name);
            Metrics metrics = MetricsCalculator.compute(result);
            ResultPrinter.printMetrics(metrics);
            results.add(metrics);
        } catch (Exception e) {
            System.err.println("  [ERROR] Strategy failed: " + name);
            e.printStackTrace();
        }
    }
}