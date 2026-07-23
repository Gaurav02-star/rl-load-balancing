package org;

import java.util.Random;

/**
 * PoissonArrival.java
 * Generates exponentially distributed inter-arrival times for Poisson processes.
 */
public class PoissonArrival implements ArrivalDistribution {

    private final double lambda;
    private final Random random;

    public PoissonArrival(double lambda, long seed) {
        this.lambda = lambda <= 0.0 ? 1.0 : lambda;
        this.random = new Random(seed);
    }

    @Override
    public double nextInterArrivalTime() {
        // Inter-arrival time = -ln(1 - U) / λ
        double u = random.nextDouble();
        // Prevent Math.log(0)
        while (u >= 1.0 || u <= 0.0) {
            u = random.nextDouble();
        }
        return -Math.log(1.0 - u) / lambda;
    }

    @Override
    public int nextBatchSize() {
        return 1;
    }
}