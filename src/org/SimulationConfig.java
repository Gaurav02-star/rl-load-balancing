package org;

/**
 * SimulationConfig.java
 *
 * Single source of truth for every tunable parameter in the simulation.
 * Change a value here and every module picks it up automatically —
 * nothing is hard-coded anywhere else.
 */
public class SimulationConfig {

    // ── CloudSim runtime ──────────────────────────────────────────────────────
    public static final int     NUM_USERS       = 1;
    public static final boolean TRACE_FLAG      = false;

    // ── Datacenter ────────────────────────────────────────────────────────────
    public static final String  DC_ARCH         = "x86";
    public static final String  DC_OS           = "Linux";
    public static final String  DC_VMM          = "Xen";
    public static final double  DC_TIME_ZONE    = 10.0;
    public static final double  DC_COST         = 3.0;
    public static final double  DC_COST_MEM     = 0.05;
    public static final double  DC_COST_STORAGE = 0.001;
    public static final double  DC_COST_BW      = 0.0;

    // ── Hosts (one entry per host; add more rows to scale out) ───────────────
    public static final int     NUM_HOSTS       = 2;
    public static final int     HOST_MIPS       = 1000;  // per PE
    public static final int     HOST_PES        = 2;     // cores per host
    public static final int     HOST_RAM        = 4096;  // MB
    public static final long    HOST_STORAGE    = 1_000_000; // MB
    public static final int     HOST_BW         = 10_000;

    // ── VMs ───────────────────────────────────────────────────────────────────
    public static final int     NUM_VMS         = 4;
    public static final int     VM_MIPS         = 1000;
    public static final int     VM_PES          = 1;
    public static final int     VM_RAM          = 1024;  // MB
    public static final long    VM_BW           = 1000;
    public static final long    VM_SIZE         = 10_000; // MB (image)
    public static final String  VM_VMM          = "Xen";

    // ── Cloudlets ─────────────────────────────────────────────────────────────
    public static final int     NUM_CLOUDLETS   = 20;
    public static final int     CL_PES          = 1;
    public static final long    CL_FILE_SIZE    = 300;   // MB
    public static final long    CL_OUTPUT_SIZE  = 300;   // MB
    // Length pattern: id%3==0 → SHORT, id%3==1 → MEDIUM, id%3==2 → LONG
    public static final long    CL_LENGTH_SHORT  = 1_000; // MI
    public static final long    CL_LENGTH_MEDIUM = 2_000;
    public static final long    CL_LENGTH_LONG   = 3_000;

    // ── Energy model (simple linear) ─────────────────────────────────────────
    public static final double  IDLE_POWER      = 100.0; // Watts
    public static final double  MAX_POWER       = 200.0; // Watts

    // Private constructor — this is a constants class, never instantiated.
    private SimulationConfig() {}
}
