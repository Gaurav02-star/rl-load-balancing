package org;

/**
 * Autoscaler.java
 * Interface for autoscaling algorithms.
 */
public interface Autoscaler {
    AutoscalerAction evaluateScaling(ClusterState currentState);
}