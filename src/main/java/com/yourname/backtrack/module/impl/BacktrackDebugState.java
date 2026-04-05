package com.yourname.backtrack.module.impl;

public class BacktrackDebugState {

    // volatile: fields are written on the game thread but read on the render
    // thread by onRenderOverlay — volatile prevents the JIT from caching
    // stale values in a register across frames
    private volatile boolean active;
    private volatile int     targetEntityId = -1;
    private volatile String  targetName     = "NONE";
    private volatile double  distance;
    private volatile int     remainingDelay;
    private volatile String  status         = "IDLE";
    private volatile long    windowStartTime;

    public boolean isActive()        { return active; }
    public int    getTargetEntityId(){ return targetEntityId; }
    public String getTargetName()    { return targetName; }
    public double getDistance()      { return distance; }
    public int    getRemainingDelay(){ return remainingDelay; }
    public String getStatus()        { return status; }

    public void begin(int targetEntityId, String targetName, double distance, int delayMs) {
        this.active          = true;
        this.targetEntityId  = targetEntityId;
        this.targetName      = targetName;
        this.distance        = distance;
        this.remainingDelay  = delayMs;
        this.status          = delayMs > 0 ? "WARMUP" : "READY";
        this.windowStartTime = System.currentTimeMillis();
    }

    public void updateDistance(double distance) {
        this.distance = distance;
    }

    public void updateRemaining(long now, int delayMs) {
        int elapsed    = (int) (now - windowStartTime);
        remainingDelay = Math.max(0, delayMs - elapsed);
        status         = remainingDelay > 0 ? "WARMUP" : "READY";
    }

    public void reset(String status) {
        this.active          = false;
        this.targetEntityId  = -1;
        this.targetName      = "NONE";
        this.distance        = 0.0;
        this.remainingDelay  = 0;
        this.status          = status;
        this.windowStartTime = 0L;
    }
}

