package com.smartparking;

public class ParkingSlot {
    private String slotId;
    private boolean occupied;
    private long entryTimeMs;
    private String vehicleNumber;
    private double tollAccumulated;

    // Toll rate: Rs 5 per 10 seconds
    public static final double RATE_PER_10_SEC = 5.0;

    public ParkingSlot(String slotId) {
        this.slotId = slotId;
        this.occupied = false;
        this.entryTimeMs = 0;
        this.vehicleNumber = "";
        this.tollAccumulated = 0.0;
    }

    public void carParked(String vehicleNumber) {
        this.occupied = true;
        this.entryTimeMs = System.currentTimeMillis();
        this.vehicleNumber = vehicleNumber;
        this.tollAccumulated = 0.0;
        System.out.println("Slot " + slotId + " occupied by " + vehicleNumber);
    }

    public double carLeft() {
        double toll = calculateCurrentToll();
        System.out.println("Slot " + slotId + " freed. Total toll: Rs " + toll);
        this.occupied = false;
        this.entryTimeMs = 0;
        this.vehicleNumber = "";
        this.tollAccumulated = 0.0;
        return toll;
    }

    public double calculateCurrentToll() {
        if (!occupied || entryTimeMs == 0) return 0.0;
        long elapsedMs = System.currentTimeMillis() - entryTimeMs;
        long intervals = elapsedMs / 10000; // every 10 seconds
        return intervals * RATE_PER_10_SEC;
    }

    public long getParkedDurationSeconds() {
        if (!occupied || entryTimeMs == 0) return 0;
        return (System.currentTimeMillis() - entryTimeMs) / 1000;
    }

    // Getters
    public String getSlotId()        { return slotId; }
    public boolean isOccupied()      { return occupied; }
    public long getEntryTimeMs()     { return entryTimeMs; }
    public String getVehicleNumber() { return vehicleNumber; }

    public void setOccupied(boolean occupied) { this.occupied = occupied; }
    public void setVehicleNumber(String v)    { this.vehicleNumber = v; }
    public void setEntryTimeMs(long t)        { this.entryTimeMs = t; }
}
