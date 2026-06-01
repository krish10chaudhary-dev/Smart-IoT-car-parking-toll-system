package com.smartparking;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   Smart Parking System - Backend");
        System.out.println("========================================");

        // Start MQTT listener
        ParkingMqttClient mqttClient = new ParkingMqttClient();
        mqttClient.connect();

        // Start HTTP server for dashboard API on port 8080
        ParkingHttpServer httpServer = new ParkingHttpServer(8080, mqttClient.getParkingManager());
        httpServer.start();

        System.out.println("HTTP API running at: http://localhost:8080");
        System.out.println("Open dashboard/index.html in your browser!");
        System.out.println("========================================");

        // Keep running
        Thread.currentThread().join();
    }
}
