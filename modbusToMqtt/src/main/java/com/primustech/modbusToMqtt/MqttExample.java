package com.primustech.modbusToMqtt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Properties;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Controller
public class MqttExample {

	private static MqttClient mosclient;
	static MqttCallback mCallback;

	public MqttExample() throws MqttException {
		String host = String.format("tcp://%s:%d", "localhost", 1883);
		String username = "username";
		String password = "password";
		String clientId = "mosquittoToJace";

		MqttConnectOptions conOpt = new MqttConnectOptions();
		conOpt.setCleanSession(true);
		conOpt.setUserName(username);
		conOpt.setPassword(password.toCharArray());

		mosclient = new MqttClient(host, clientId);
		mosclient.connect(conOpt);
	}

	public static MqttClient getMqttClient(MqttExampleOptions options)
			throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, MqttException, InterruptedException {
		final String mqttServerAddress = String.format("ssl://%s:%s", options.mqttBridgeHostname,
				options.mqttBridgePort);
		final String mqttClientId = String.format("projects/%s/locations/%s/registries/%s/devices/%s",
				options.projectId, options.cloudRegion, options.registryId, options.deviceId);

		MqttConnectOptions connectOptions = new MqttConnectOptions();
		connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

		Properties sslProps = new Properties();
		sslProps.setProperty("com.ibm.ssl.protocol", "TLSv1.2");
		connectOptions.setSSLProperties(sslProps);
		connectOptions.setUserName("unused");

		DateTime iat = new DateTime();
		if (options.algorithm.equals("RS256")) {
			connectOptions.setPassword(createJwtRsa(options.projectId, options.privateKeyFile).toCharArray());
			System.out.println(options.getDeviceId() + '-' + String.valueOf(connectOptions.getPassword()));
		} else {
			throw new IllegalArgumentException(
					"Invalid algorithm " + options.algorithm + ". Should be one of 'RS256' or 'ES256'.");
		}
		
		MqttClient client = new MqttClient(mqttServerAddress, mqttClientId, new MemoryPersistence());

		long initialConnectIntervalMillis = 500L;
		long maxConnectIntervalMillis = 6000L;
		long maxConnectRetryTimeElapsedMillis = 900000L;
		float intervalMultiplier = 1.5f;

		long retryIntervalMs = initialConnectIntervalMillis;
		long totalRetryTimeMs = 0;

		while (!client.isConnected() && totalRetryTimeMs < maxConnectRetryTimeElapsedMillis) {
			try {
				client.connect(connectOptions);
			} catch (MqttException e) {
				int reason = e.getReasonCode();

				System.out.println("An error occurred: " + e.getMessage());
				if (reason == MqttException.REASON_CODE_CONNECTION_LOST
						|| reason == MqttException.REASON_CODE_SERVER_CONNECT_ERROR) {
					System.out.println("Retrying in " + retryIntervalMs / 1000.0 + " seconds.");
					Thread.sleep(retryIntervalMs);
					totalRetryTimeMs += retryIntervalMs;
					retryIntervalMs *= intervalMultiplier;
					if (retryIntervalMs > maxConnectIntervalMillis) {
						retryIntervalMs = maxConnectIntervalMillis;
					}
				} else {
					throw e;
				}
			}
		}

		attachCallback(client, options.deviceId);
		long secsSinceRefresh = ((new DateTime()).getMillis() - iat.getMillis()) / 1000;
		if (secsSinceRefresh > (options.tokenExpMins * 60)) {
			System.out.format("\tRefreshing token after: %d seconds\n", secsSinceRefresh);
			iat = new DateTime();
			if (options.algorithm.equals("RS256")) {
				connectOptions.setPassword(createJwtRsa(options.projectId, options.privateKeyFile).toCharArray());
			} else {
				throw new IllegalArgumentException(
						"Invalid algorithm " + options.algorithm + ". Should be one of 'RS256' or 'ES256'.");
			}
			client.disconnect();
			client.connect();
			attachCallback(client, options.deviceId);
		}
		return client;
	}

	public static void attachCallback(MqttClient client, String deviceId) throws MqttException {
		mCallback = new MqttCallback() {
			@Override
			public void connectionLost(Throwable cause) {
				// Do nothing...
			}

			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				System.out.println(deviceId);
				String payload = new String(message.getPayload());
				System.out.println("Payload : " + payload);
//				try {
//					JSONObject jsonObj = new JSONObject(payload);
//					JSONObject pointsetObj = new JSONObject(String.valueOf(jsonObj.get("pointset")));
//					JSONObject pointsObj = new JSONObject(String.valueOf(pointsetObj.get("points")));
//					pointsObj.keys().forEachRemaining(x -> {
//						JSONObject subPointObj = new JSONObject(String.valueOf(pointsObj.get(x)));
//						subPointObj.keys().forEachRemaining(y -> {
//							System.out.println(x);
//							System.out.println(subPointObj.get(y));
//							message.setPayload(String.valueOf(subPointObj.get(y)).getBytes());
//							try {
//								mosclient.publish(deviceId + '_' + x, message);
//							} catch (Exception e) {
//								e.printStackTrace();
//							}
//						});
//					});
//				} catch (Exception e) {
//					System.out.println(e.getMessage());
//				}
			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
				// Do nothing;
			}
		};

		String configTopic = String.format("/devices/%s/config", deviceId);

		client.subscribe(configTopic, 1);
		client.setCallback(mCallback);
	}

	private static String createJwtRsa(String projectId, String privateKeyFile)
			throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
		DateTime now = new DateTime();
		JwtBuilder jwtBuilder = Jwts.builder().setIssuedAt(now.toDate()).setExpiration(now.plusMinutes(1000).toDate())
				.setAudience(projectId);
		byte[] keyBytes = Files.readAllBytes(Paths.get(privateKeyFile));
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("RSA");

		return jwtBuilder.signWith(SignatureAlgorithm.RS256, kf.generatePrivate(spec)).compact();
	}

}
