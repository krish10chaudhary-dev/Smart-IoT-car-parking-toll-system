package com.smartparking;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.google.gson.*;

public class ParkingMqttClient implements MqttCallback {

    private static final String BROKER     = "tcp://broker.hivemq.com:1883";
    private static final String CLIENT_ID  = "SmartParking_Java_" + System.currentTimeMillis();

    private static final String TOPIC_SLOTS   = "smartparking/slots";
    private static final String TOPIC_ENTRY   = "smartparking/entry";
    private static final String TOPIC_EXIT    = "smartparking/exit";
    private static final String TOPIC_VEHICLE = "smartparking/vehicle";

    private MqttClient mqttClient;
    private ParkingManager parkingManager;
    private Gson gson = new Gson();

    public ParkingMqttClient() {
        this.parkingManager = new ParkingManager();
    }

    public void connect() throws MqttException {
        mqttClient = new MqttClient(BROKER, CLIENT_ID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);

        mqttClient.setCallback(this);
        mqttClient.connect(options);

        // Subscribe to all topics
        mqttClient.subscribe(TOPIC_SLOTS,   0);
        mqttClient.subscribe(TOPIC_ENTRY,   0);
        mqttClient.subscribe(TOPIC_EXIT,    0);
        mqttClient.subscribe(TOPIC_VEHICLE, 0);

        System.out.println("Connected to MQTT broker: " + BROKER);
        System.out.println("Subscribed to all parking topics!");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        System.out.println("MQTT [" + topic + "]: " + payload);

        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();

            switch (topic) {
                case TOPIC_SLOTS:
                    handleSlotStatus(json);
                    break;
                case TOPIC_ENTRY:
                    System.out.println("Car entered the parking lot!");
                    parkingManager.setEntryGateOpen(true);
                    // Auto-close gate after 6 seconds (matches ESP32 timing)
                    new Thread(() -> {
                        try { Thread.sleep(6000); } catch (InterruptedException ignored) {}
                        parkingManager.setEntryGateOpen(false);
                    }).start();
                    break;
                case TOPIC_EXIT:
                    System.out.println("Car exited the parking lot!");
                    parkingManager.setExitGateOpen(true);
                    new Thread(() -> {
                        try { Thread.sleep(6000); } catch (InterruptedException ignored) {}
                        parkingManager.setExitGateOpen(false);
                    }).start();
                    break;
                case TOPIC_VEHICLE:
                    if (json.has("vehicleNumber")) {
                        parkingManager.setPendingVehicleNumber(
                            json.get("vehicleNumber").getAsString());
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error parsing MQTT message: " + e.getMessage());
        }
    }

    private void handleSlotStatus(JsonObject json) {
        String[] slotKeys = {"slot1", "slot2", "slot3"};
        for (String key : slotKeys) {
            if (json.has(key)) {
                String status = json.get(key).getAsString();
                parkingManager.updateSlotStatus(key, status, "");
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("MQTT connection lost: " + cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}

    public ParkingManager getParkingManager() { return parkingManager; }
}
