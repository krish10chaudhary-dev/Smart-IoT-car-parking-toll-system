package com.smartparking;

import java.util.*;

public class ParkingManager {

    private Map<String, ParkingSlot> slots = new LinkedHashMap<>();
    private double totalEarnings = 0.0;
    private List<TollRecord> tollHistory = new ArrayList<>();

    // Pending vehicle number from dashboard before entry
    private String pendingVehicleNumber = "";

    // Gate status
    private boolean entryGateOpen = false;
    private boolean exitGateOpen  = false;

    public ParkingManager() {
        slots.put("slot1", new ParkingSlot("slot1"));
        slots.put("slot2", new ParkingSlot("slot2"));
        slots.put("slot3", new ParkingSlot("slot3"));
    }

    // Called when ESP32 publishes slot status
    public void updateSlotStatus(String slotId, String status, String vehicleNumber) {
        ParkingSlot slot = slots.get(slotId);
        if (slot == null) return;

        boolean nowOccupied = status.equals("occupied");

        if (nowOccupied && !slot.isOccupied()) {
            // Car just parked
            String vehicle = vehicleNumber != null && !vehicleNumber.isEmpty()
                    ? vehicleNumber : pendingVehicleNumber;
            if (vehicle.isEmpty()) vehicle = "UNKNOWN";
            slot.carParked(vehicle);
            pendingVehicleNumber = "";

        } else if (!nowOccupied && slot.isOccupied()) {
            // Save BEFORE carLeft() clears everything
            String savedVehicle  = slot.getVehicleNumber();
            long   savedDuration = slot.getParkedDurationSeconds();
            double toll          = slot.carLeft();
            totalEarnings += toll;
            tollHistory.add(new TollRecord(slotId, savedVehicle, toll, savedDuration));
        }
    }

    // Called when vehicle number is entered on dashboard
    public void setPendingVehicleNumber(String vehicleNumber) {
        this.pendingVehicleNumber = vehicleNumber;
        System.out.println("Pending vehicle number set: " + vehicleNumber);
    }

    // Build full status JSON for dashboard
    public Map<String, Object> getFullStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        List<Map<String, Object>> slotList = new ArrayList<>();
        for (ParkingSlot slot : slots.values()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("slotId",         slot.getSlotId());
            s.put("occupied",       slot.isOccupied());
            s.put("vehicleNumber",  slot.getVehicleNumber());
            s.put("durationSec",    slot.getParkedDurationSeconds());
            s.put("currentToll",    slot.calculateCurrentToll());
            slotList.add(s);
        }

        status.put("slots",         slotList);
        status.put("totalEarnings", totalEarnings);
        status.put("tollHistory",   tollHistory);
        status.put("freeSlots",     getFreeSlotCount());
        status.put("occupiedSlots", getOccupiedSlotCount());
        status.put("entryGateOpen", entryGateOpen);
        status.put("exitGateOpen",  exitGateOpen);
        return status;
    }

    public int getFreeSlotCount() {
        return (int) slots.values().stream().filter(s -> !s.isOccupied()).count();
    }

    public int getOccupiedSlotCount() {
        return (int) slots.values().stream().filter(ParkingSlot::isOccupied).count();
    }

    public Map<String, ParkingSlot> getSlots()  { return slots; }
    public double getTotalEarnings()             { return totalEarnings; }
    public List<TollRecord> getTollHistory()     { return tollHistory; }

    public void setEntryGateOpen(boolean open) { this.entryGateOpen = open; }
    public void setExitGateOpen(boolean open)  { this.exitGateOpen  = open; }

    // Inner class for toll history records
    public static class TollRecord {
        public String slotId;
        public String vehicleNumber;
        public double tollAmount;
        public long durationSeconds;
        public long timestamp;

        public TollRecord(String slotId, String vehicleNumber, double tollAmount, long durationSeconds) {
            this.slotId          = slotId;
            this.vehicleNumber   = vehicleNumber;
            this.tollAmount      = tollAmount;
            this.durationSeconds = durationSeconds;
            this.timestamp       = System.currentTimeMillis();
        }
    }
}
