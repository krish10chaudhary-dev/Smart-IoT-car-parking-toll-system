#include <WiFi.h>
#include <PubSubClient.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>

// ─── WiFi Config ───────────────────────────────────────────
const char* WIFI_SSID     = "Amrita_haridwar";
const char* WIFI_PASSWORD = "Password@123";

// ─── MQTT Config ───────────────────────────────────────────
const char* MQTT_BROKER = "broker.hivemq.com";
const int   MQTT_PORT   = 1883;
const char* MQTT_CLIENT = "SmartParking_ESP32";

// ─── MQTT Topics ───────────────────────────────────────────
const char* TOPIC_SLOT_STATUS    = "smartparking/slots";
const char* TOPIC_ENTRY_EVENT    = "smartparking/entry";
const char* TOPIC_EXIT_EVENT     = "smartparking/exit";
const char* TOPIC_GATE_CONTROL   = "smartparking/gate/control";
const char* TOPIC_VEHICLE_NUMBER = "smartparking/vehicle";

// ─── Pin Definitions ───────────────────────────────────────
#define ENTRY_SERVO_PIN     25
#define EXIT_SERVO_PIN      33
#define ENTRY_IR_PIN        27
#define EXIT_IR_PIN         26
#define SLOT1_IR_PIN        34
#define SLOT2_IR_PIN        35
#define SLOT3_IR_PIN        14
#define GREEN_LED_PIN       18
#define RED_LED_PIN         19
#define BUZZER_PIN          5

// ─── Gate Angles ───────────────────────────────────────────
#define GATE_OPEN_ANGLE     130
#define GATE_CLOSE_ANGLE    0

// ─── Objects ───────────────────────────────────────────────
Servo entryServo;
Servo exitServo;
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

// ─── State Variables ───────────────────────────────────────
bool entryGateOpen       = false;
bool exitGateOpen        = false;
bool lastEntryIR         = HIGH;
bool lastExitIR          = HIGH;
bool lastSlot1           = HIGH;
bool lastSlot2           = HIGH;
bool lastSlot3           = HIGH;

unsigned long lastPublishTime = 0;
const long PUBLISH_INTERVAL   = 2000; // publish slot status every 2 seconds

unsigned long entryGateOpenTime = 0;
const long GATE_AUTO_CLOSE_MS   = 5000; // auto close gate after 5 seconds

// ─── Setup ─────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);

  // Pin modes
  pinMode(ENTRY_IR_PIN, INPUT);
  pinMode(EXIT_IR_PIN,  INPUT);
  pinMode(SLOT1_IR_PIN, INPUT);
  pinMode(SLOT2_IR_PIN, INPUT);
  pinMode(SLOT3_IR_PIN, INPUT);
  pinMode(GREEN_LED_PIN, OUTPUT);
  pinMode(RED_LED_PIN,   OUTPUT);
  pinMode(BUZZER_PIN,    OUTPUT);

  // Initial LED/Buzzer state
  digitalWrite(GREEN_LED_PIN, LOW);
  digitalWrite(RED_LED_PIN,   HIGH); // Red ON = no entry allowed initially
  digitalWrite(BUZZER_PIN,    LOW);

  // Attach servos
  entryServo.attach(ENTRY_SERVO_PIN);
  exitServo.attach(EXIT_SERVO_PIN);
  closeEntryGate();
  closeExitGate();

  // Connect WiFi
  connectWiFi();

  // Setup MQTT
  mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);
  connectMQTT();

  Serial.println("Smart Parking System Ready!");
}

// ─── Loop ──────────────────────────────────────────────────
void loop() {
  if (!mqttClient.connected()) connectMQTT();
  mqttClient.loop();

  handleEntryGateIR();
  handleExitGateIR();
  autoCloseEntryGate();

  // Publish slot status periodically
  if (millis() - lastPublishTime > PUBLISH_INTERVAL) {
    publishSlotStatus();
    lastPublishTime = millis();
  }
}

// ─── IR Sensor Handlers ────────────────────────────────────
void handleEntryGateIR() {
  bool currentReading = digitalRead(ENTRY_IR_PIN);

  // Car detected at entry (IR goes LOW)
  if (currentReading == LOW && lastEntryIR == HIGH) {
    Serial.println("Car detected at ENTRY gate!");
    openEntryGate();
    greenLEDOn();
    buzzerBeep();
    publishEntryEvent();
    entryGateOpenTime = millis();
  }

  // Car passed entry (IR goes HIGH again)
  if (currentReading == HIGH && lastEntryIR == LOW) {
    Serial.println("Car passed ENTRY gate!");
    delay(1000); // small delay before closing
    closeEntryGate();
    greenLEDOff();
    redLEDOn();
  }

  lastEntryIR = currentReading;
}

void handleExitGateIR() {
  bool currentReading = digitalRead(EXIT_IR_PIN);

  // Car detected at exit (IR goes LOW)
  if (currentReading == LOW && lastExitIR == HIGH) {
    Serial.println("Car detected at EXIT gate!");
    openExitGate();
    publishExitEvent();
  }

  // Car passed exit (IR goes HIGH again)
  if (currentReading == HIGH && lastExitIR == LOW) {
    Serial.println("Car passed EXIT gate!");
    delay(1000);
    closeExitGate();
  }

  lastExitIR = currentReading;
}

void autoCloseEntryGate() {
  if (entryGateOpen && (millis() - entryGateOpenTime > GATE_AUTO_CLOSE_MS)) {
    closeEntryGate();
    greenLEDOff();
    redLEDOn();
  }
}

// ─── Gate Controls ─────────────────────────────────────────
void openEntryGate() {
  entryServo.write(GATE_OPEN_ANGLE);
  entryGateOpen = true;
  Serial.println("Entry gate OPENED");
}

void closeEntryGate() {
  entryServo.write(GATE_CLOSE_ANGLE);
  entryGateOpen = false;
  Serial.println("Entry gate CLOSED");
}

void openExitGate() {
  exitServo.write(GATE_OPEN_ANGLE);
  exitGateOpen = true;
  Serial.println("Exit gate OPENED");
}

void closeExitGate() {
  exitServo.write(GATE_CLOSE_ANGLE);
  exitGateOpen = false;
  Serial.println("Exit gate CLOSED");
}

// ─── LED & Buzzer ──────────────────────────────────────────
void greenLEDOn()  { digitalWrite(GREEN_LED_PIN, HIGH); digitalWrite(RED_LED_PIN, LOW); }
void greenLEDOff() { digitalWrite(GREEN_LED_PIN, LOW); }
void redLEDOn()    { digitalWrite(RED_LED_PIN, HIGH); }
void redLEDOff()   { digitalWrite(RED_LED_PIN, LOW); }

void buzzerBeep() {
  for(int i = 0; i < 150; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delayMicroseconds(500);
    digitalWrite(BUZZER_PIN, LOW);
    delayMicroseconds(500);
  }
  delay(100);
  for(int i = 0; i < 150; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delayMicroseconds(300);
    digitalWrite(BUZZER_PIN, LOW);
    delayMicroseconds(300);
  }
}

// ─── MQTT Publishers ───────────────────────────────────────
void publishSlotStatus() {
  bool slot1 = digitalRead(SLOT1_IR_PIN) == LOW; // LOW = occupied
  bool slot2 = digitalRead(SLOT2_IR_PIN) == LOW;
  bool slot3 = digitalRead(SLOT3_IR_PIN) == LOW;

  StaticJsonDocument<200> doc;
  doc["slot1"] = slot1 ? "occupied" : "free";
  doc["slot2"] = slot2 ? "occupied" : "free";
  doc["slot3"] = slot3 ? "occupied" : "free";
  doc["timestamp"] = millis();

  char buffer[200];
  serializeJson(doc, buffer);
  mqttClient.publish(TOPIC_SLOT_STATUS, buffer);
  Serial.println("Slot status published: " + String(buffer));
}

void publishEntryEvent() {
  StaticJsonDocument<100> doc;
  doc["event"]     = "entry";
  doc["timestamp"] = millis();

  char buffer[100];
  serializeJson(doc, buffer);
  mqttClient.publish(TOPIC_ENTRY_EVENT, buffer);
  Serial.println("Entry event published");
}

void publishExitEvent() {
  StaticJsonDocument<100> doc;
  doc["event"]     = "exit";
  doc["timestamp"] = millis();

  char buffer[100];
  serializeJson(doc, buffer);
  mqttClient.publish(TOPIC_EXIT_EVENT, buffer);
  Serial.println("Exit event published");
}

// ─── MQTT Callback ─────────────────────────────────────────
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message = "";
  for (int i = 0; i < length; i++) message += (char)payload[i];
  Serial.println("MQTT received [" + String(topic) + "]: " + message);
}

// ─── WiFi Connect ──────────────────────────────────────────
void connectWiFi() {
  Serial.print("Connecting to WiFi: ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected! IP: " + WiFi.localIP().toString());
}

// ─── MQTT Connect ──────────────────────────────────────────
void connectMQTT() {
  while (!mqttClient.connected()) {
    Serial.print("Connecting to MQTT broker...");
    if (mqttClient.connect(MQTT_CLIENT)) {
      Serial.println("Connected!");
      mqttClient.subscribe(TOPIC_GATE_CONTROL);
      mqttClient.subscribe(TOPIC_VEHICLE_NUMBER);
    } else {
      Serial.print("Failed, rc=");
      Serial.print(mqttClient.state());
      Serial.println(" retrying in 3s...");
      delay(3000);
    }
  }
}
