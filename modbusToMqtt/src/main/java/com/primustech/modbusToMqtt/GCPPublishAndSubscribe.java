package com.primustech.modbusToMqtt;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import Model.DeviceInfo;

@Controller
@Configuration
@EnableScheduling
public class GCPPublishAndSubscribe {
	private Map<String, MqttClient> map = new HashMap<>();
	private List<DeviceInfo> deviceInfoList = new ArrayList<>();

	public GCPPublishAndSubscribe() {
		try {
			File file = ResourceUtils.getFile("classpath:eqp.xlsx");
			FileInputStream fis = new FileInputStream(file);
			XSSFWorkbook wb = new XSSFWorkbook(fis);
			XSSFSheet sh = wb.getSheet("eqp");
			DataFormatter formatter = new DataFormatter();

			MqttExampleOptions options = new MqttExampleOptions();
			for (int i = 1; i < sh.getLastRowNum() + 1; i++) {
				String deviceId = formatter.formatCellValue(sh.getRow(i).getCell(0));
				String privateKeyFile = formatter.formatCellValue(sh.getRow(i).getCell(1));
				options.setDeviceId(deviceId);
				options.setPrivateKey(privateKeyFile);
				MqttClient mqttClient = MqttExample.getMqttClient(options);
				map.put(deviceId, mqttClient);// s

				DeviceInfo deviceInfo = new DeviceInfo();
				deviceInfo.setDeviceId(deviceId);
				deviceInfo.setPrivateKey(privateKeyFile);
				deviceInfoList.add(deviceInfo);
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	@ResponseBody
	@PostMapping(value = "/publishGCP")
	public void publishGCP(@RequestBody String udmiMessage) {
		try {
			System.out.println("Received Msg from MODBUS");
			System.out.println(udmiMessage);
			String jsonStr = udmiMessage;

			JSONObject jsonObj = new JSONObject(jsonStr);
			String deviceId = String.valueOf(jsonObj.get("deviceId"));
			jsonObj.remove("deviceId");

			MqttClient mqttClient = map.get(deviceId);
			MqttMessage mqttMessage = new MqttMessage();
			mqttMessage.setPayload(jsonObj.toString().getBytes());
			String mqttTopic = String.format("/devices/%s/events/%s", deviceId, "iot");
			mqttClient.publish(mqttTopic, mqttMessage);

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	@Scheduled(cron = "* * */12 * * *")
	public void doScheduledWork() {
		System.out.println("Start Renewing JWT Token");
		MqttExampleOptions options = new MqttExampleOptions();
		deviceInfoList.forEach(x -> {
			try {
				String deviceId = x.getDeviceId();
				String privateKeyFile = x.getPrivateKey();
				options.setDeviceId(deviceId);
				options.setPrivateKey(privateKeyFile);
				MqttClient mqttClient = MqttExample.getMqttClient(options);
				map.put(deviceId, mqttClient);
				System.out.println("JWT Token Renewed for : " + deviceId);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}

		});
	}
}
