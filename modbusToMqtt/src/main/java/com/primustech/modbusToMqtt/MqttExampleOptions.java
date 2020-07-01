/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.primustech.modbusToMqtt;

public class MqttExampleOptions {
	String projectId = "iotmqttproject";
	String registryId = "iotRegistry";
	String command = "mqtt-demo";
	String deviceId = "AHU-05";
	String gatewayId;
	String privateKeyFile = "rsa_private";
	String algorithm = "RS256";
	String cloudRegion = "us-central1";
	int numMessages = 100;
	int tokenExpMins = 20;
	String telemetryData = "Specify with -telemetry_data";

	String mqttBridgeHostname = "mqtt.googleapis.com";
	short mqttBridgePort = 8883;
	String messageType = "topic";
	int waitTime = 120;

	public MqttExampleOptions() {
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getPrivateKey() {
		return privateKeyFile;
	}

	public void setPrivateKey(String privateKeyFile) {
		this.privateKeyFile = privateKeyFile;
	}

}
