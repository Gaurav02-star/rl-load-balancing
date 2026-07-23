package org;

/**
 * ArrivalDistribution.java
 * Abstraction for dynamic task arrival timing and batching.
 */
public interface ArrivalDistribution {
    /**
     * @return Next inter-arrival duration in simulated seconds.
     */
    double nextInterArrivalTime();

    /**
     * @return Batch size of cloudlets arriving at this interval.
     */
    int nextBatchSize();
}