<h1>RL Load Balancing</h1>

<img width="1440" height="1312" alt="image" src="https://github.com/user-attachments/assets/4dd3d3b7-d06e-40d0-a69d-0bddf2f57175" />

<p>Description of each file:</p>
<ul>
  <li>SimulationConfig.java: Every constant in one place — change VM count, cloudlet lengths, energy model, etc. here only</li>
  <li>AssignmentStrategy.java: The interface. The only contract all heuristics must satisfy</li>
  <li>SimulationResult.java: Plain data object — completed cloudlets + VM list + label</li>
  <li>SimulationRunner.java: The engine. Direct refactor of your original file. Calls strategy.assign() as the only variable line</li>
  <li>FCFSStrategy.java: Arrival-order cycling</li>
  <li>RoundRobinStrategy.java: Explicit cyclic (distinguishable from FCFS when VM list is dynamic)</li>
  <li>LeastLoadedStrategy.java: Online greedy — no sorting, reacts to arrival order</li>
  <li>MinMinStrategy.java: Offline — sort shortest first, assign to min-load VM</li>
  <li>MaxMinStrategy.java: Offline — sort longest first, assign to min-load VM</li>
  <li>RLStrategy.java: Stub with full extension guide — falls back to least-loaded until you wire the model</li>
  <li>Metrics.java: Immutable value object for all 8 metrics</li>
  <li>MetricsCalculator.java: Pure computation, no printing, no CloudSim dependencies</li>
  <li>ResultPrinter.java: All System.out calls live here and nowhere else</li>  
  <li>Main.java: One runOne() call per strategy — the only file you touch to add a new algorithm</li>
</ul>

# SARSA-Based Dynamic Autoscaling Enhancement

## Project Overview

This project enhances the existing CloudSim-based RL autoscaling framework by replacing the original Q-Learning/Watkins Q(λ) autoscaler with a Canonical SARSA(λ)-based autoscaler while preserving the existing dynamic load balancing architecture.

The objective is to make autoscaling decisions dynamically based on the current cluster state while maintaining SLA compliance and efficient VM utilization.

---

# Final Changes Implemented

## 1. RL Algorithm Upgrade

### Before
- Watkins Q(λ) / Q-Learning style update
- Used maximum Q-value of next state
- Cleared eligibility traces on exploratory actions

### After
- Canonical SARSA(λ)
- On-policy learning
- Uses Q(S', A') instead of maxQ(S')
- Eligibility traces are cleared only at terminal states

### Files Modified

- RLAutoscaler.java

---

## 2. SARSA Update Rule

### Old

TD Target

Q(s,a) ← r + γ max Q(s',a')

### New

TD Target

Q(s,a) ← r + γ Q(s',a')

This converts the autoscaler into a true on-policy SARSA(λ) learner.

---

## 3. Eligibility Trace Update

### Removed

Off-policy trace clearing

```java
lastWasOffPolicy
```

### New

Eligibility traces now decay naturally and are only cleared when an episode terminates.

---

## 4. Autoscaler State Representation

The autoscaler state now includes predicted workload information.

### State Components

- CPU Utilization
- Queue Length
- Queue Trend
- Predicted Arrival Tier
- Active VM Tier

Predicted Arrival Tier is divided into

- LOW
- MEDIUM
- HIGH

This enables proactive autoscaling decisions.

---

## 5. ClusterState Extension

ClusterState was extended to support prediction.

Added

```java
private final double predictedArrivalRate;
```

Getter

```java
getPredictedArrivalRate()
```

Convenience Method

```java
isLoadIncreasePredicted()
```

---

## 6. Monitoring Module Enhancement

MonitoringModule now

- Computes arrival rate
- Updates EMA predictor
- Stores predicted arrival rate
- Embeds prediction into ClusterState

Prediction is generated every monitoring interval.

---

## 7. Training Improvements

Training episodes increased.

### Before

```text
30 Episodes
```

### After

```text
200 Episodes
```

This allows SARSA to converge more reliably after increasing the state space.

---

## 8. Reward Function

Final reward uses

- Response Time Penalty
- Queue Growth Penalty
- Queue Length Penalty
- SLA Violation Penalty
- Idle Bonus

Final reward intentionally avoids

- CPU utilization reward
- VM cost reward
- Arrival pressure reward

These were experimentally evaluated but did not improve overall performance for the current workload.

---

## 9. VM Lifecycle

No functional changes were made to

- VmLifecycleManager.java

Scale Up

- Adds one VM

Scale Down

- Removes one VM

Future work may extend adaptive provisioning.

---

## Files Modified

### RLAutoscaler.java

- Converted Watkins Q(λ) → Canonical SARSA(λ)
- Updated TD target
- Removed off-policy logic
- Added prediction-aware state
- Updated reward function
- Training improvements

---

### MonitoringModule.java

- Integrated EMA predictor
- Added predicted arrival rate
- Builds prediction-aware ClusterState

---

### ClusterState.java

Added

- predictedArrivalRate
- getter
- prediction helper

---

### SimulationConfig.java

Training Episodes

```java
TRAINING_EPISODES = 200;
```

---

## Experimental Results

Final Dynamic Simulation

| Strategy | Avg Response | Avg CPU | Avg VMs |
|----------|-------------:|--------:|--------:|
| Rule-Based Autoscaler | 0.1591 s | 41.95 % | 6.41 |
| SARSA Autoscaler | 0.1667 s | 45.00 % | 6.18 |

Observations

- Comparable response time
- Similar SLA compliance
- Slightly fewer average VMs
- Fully dynamic SARSA-based autoscaling

---

# Future Enhancements

Potential improvements include

- Adaptive VM provisioning (+2 / -2 scaling)
- Deep SARSA (DQN-based)
- Continuous state representation
- Energy-aware reward function
- Carbon-aware scheduling
- Dynamic action space
- Adaptive ε-decay
- Multi-workload evaluation

---

# Technologies Used

- Java
- CloudSim
- SARSA(λ)
- EMA Prediction
- Dynamic VM Provisioning
- Reinforcement Learning

---

# Authors

Enhanced by replacing the existing RL autoscaler with a Canonical SARSA(λ)-based dynamic autoscaling framework while preserving compatibility with the existing CloudSim architecture.
