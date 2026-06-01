package com.smartparking;

import com.google.gson.Gson;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.Map;

public class ParkingHttpServer {

    private final int port;
    private final ParkingManager parkingManager;
    private final Gson gson = new Gson();
    private HttpServer server;

    public ParkingHttpServer(int port, ParkingManager parkingManager) {
        this.port = port;
        this.parkingManager = parkingManager;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // API endpoints
        server.createContext("/api/status",  this::handleStatus);
        server.createContext("/api/vehicle", this::handleVehicle);
        server.createContext("/api/toll",    this::handleToll);

        server.setExecutor(null);
        server.start();
        System.out.println("HTTP Server started on port " + port);
    }

    // GET /api/status - returns full parking status
    private void handleStatus(HttpExchange exchange) throws IOException {
        addCORSHeaders(exchange);

        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        Map<String, Object> status = parkingManager.getFullStatus();
        String response = gson.toJson(status);
        sendResponse(exchange, 200, response);
    }

    // POST /api/vehicle - set vehicle number before entry
    private void handleVehicle(HttpExchange exchange) throws IOException {
        addCORSHeaders(exchange);

        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equals("POST")) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<?, ?> data = gson.fromJson(body, Map.class);

        if (data != null && data.containsKey("vehicleNumber")) {
            String vehicleNumber = data.get("vehicleNumber").toString().toUpperCase();
            parkingManager.setPendingVehicleNumber(vehicleNumber);
            sendResponse(exchange, 200, "{\"success\":true,\"vehicleNumber\":\"" + vehicleNumber + "\"}");
        } else {
            sendResponse(exchange, 400, "{\"error\":\"vehicleNumber required\"}");
        }
    }

    // GET /api/toll - returns toll history
    private void handleToll(HttpExchange exchange) throws IOException {
        addCORSHeaders(exchange);

        if (exchange.getRequestMethod().equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String response = gson.toJson(parkingManager.getTollHistory());
        sendResponse(exchange, 200, response);
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void addCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
