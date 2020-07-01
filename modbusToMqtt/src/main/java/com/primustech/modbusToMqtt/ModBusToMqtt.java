package com.primustech.modbusToMqtt;

import java.io.File;
import java.io.FileInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import de.re.easymodbus.modbusclient.ModbusClient;
@Controller
@Configuration
@EnableScheduling
public class ModBusToMqtt {
	private ExecutorService executor = Executors.newCachedThreadPool();
	private RestTemplate restTemplate = new RestTemplate();
	
	@Value("http://localhost:${server.port}/publishGCP")
	private String uri;
	
	@Value("${modbus.ip}")
	private String modbusIpAddress;
	
	@Value("${modbus.port}")
	private Integer modbusPort;
	
	private ModbusClient modbusClient;
	
	private List<Point> pointList;
	
	@PostConstruct
	public void initModBusToMqtt() {
		try {
			this.modbusClient = new ModbusClient(modbusIpAddress, modbusPort);
			this.modbusClient.Connect();
			this.pointList = getPointList();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	@Scheduled(cron = "*/5 * * * * *")
	public void startPublish() {
		System.out.println("===Start Modbus Reading===");
		setPointListWithValue(modbusClient, pointList);
		System.out.println("===Start Publish GCP===");
		publishToGCP(pointList);
		//writeToASP(modbusClient);
		System.out.println("===Operation Completed===");
	}
	
	private void publishToGCP(List<Point> pointList) {
		Map<String, List<Point>> map = pointList.stream().collect(Collectors.groupingBy(Point :: getEquipment));
		map.entrySet().forEach(x->{
			String equipment = x.getKey();
			List<Point> pointListSub = x.getValue();
			JSONObject json = new JSONObject();
			JSONObject json2 = new JSONObject();
			json.put("version", 1);
			json.put("timestamp", getTimeStamp());
			json.put("pointset", json2);
			json.put("deviceId", equipment);
			
			pointListSub.forEach(y->{
				JSONObject json3 = new JSONObject();
				json2.put(y.getTag(), json3);
				json3.put("present_value", y.getPointValue());
			});        
			//System.out.println(json.toString());
			CompletableFuture.supplyAsync(() -> {
				restTemplate.postForEntity(uri, json.toString(), String.class);
				return "Done";
			}, executor);
		});
	}
	
	private static void setPointListWithValue(ModbusClient modbusClient, List<Point> pointList){
		try {
		Map<FunctionCodeEnum, List<Point>> pointMap = pointList.stream()
				  .collect(Collectors.groupingBy(Point::getFunctionCode));
		Iterator it = pointMap.entrySet().iterator();
		while(it.hasNext()) {
			  Map.Entry pair = (Map.Entry)it.next();
			  FunctionCodeEnum key = (FunctionCodeEnum) pair.getKey();
		      List<Point> subPointList = (List<Point>) pair.getValue();
		      int addr = Integer.parseInt(subPointList.get(0).getAddress())-1;
		      int qty = subPointList.size();
		      if(key.equals(FunctionCodeEnum.FC01) || key.equals(FunctionCodeEnum.FC02)) {
		    	  boolean[] readFC = null;
		    	  if(key.equals(FunctionCodeEnum.FC01)) {
		    		  readFC = modbusClient.ReadCoils(addr, qty);
		    	  }else if(key.equals(FunctionCodeEnum.FC02)) {
		    		  readFC = modbusClient.ReadDiscreteInputs(addr, qty);
		    	  }
		    	  for(int i=0;i<readFC.length;i++) {
		    		  subPointList.get(i).setPointValue(readFC[i]);
		    	  }
		      }else if(key.equals(FunctionCodeEnum.FC03) || key.equals(FunctionCodeEnum.FC04)) {
		    	  Map<PointTypeEnum, List<Point>> subPointMap = subPointList.stream().collect(Collectors.groupingBy(Point::getType));
				  Iterator subIt = subPointMap.entrySet().iterator();
				  while(subIt.hasNext()) {
					  Map.Entry pair2 = (Map.Entry)subIt.next();
					  PointTypeEnum key2 = PointTypeEnum.valueOf(String.valueOf(pair2.getKey())) ;
				      List<Point> subPointList2 = (List<Point>) pair2.getValue();
			    	  int[] readFC = null;
			    	  int j=0;
			    	  while(j<subPointList2.size()) {
			    		  Integer subQty = subPointList2.get(j).getQty();
					      int addr2 = Integer.parseInt(subPointList2.get(j).getAddress())-1;
					      if(key2==PointTypeEnum.INT) {
					    	  if(key.equals(FunctionCodeEnum.FC03)) {
					    		  readFC = modbusClient.ReadHoldingRegisters(addr2, subQty);
					    	  }else {
					    		  readFC = modbusClient.ReadInputRegisters(addr2, subQty);
					    	  }
					    	  for(int i=0;i<readFC.length;i++) {
					    		  subPointList2.get(j).setPointValue(readFC[i]);
					    		  j++;
					    	  }
					      }else if(key2==PointTypeEnum.Float) {
					    	  int[] subFC03 = null;
						      float pointValue = 0;
						      if(key.equals(FunctionCodeEnum.FC03)) {
						    	  readFC = modbusClient.ReadHoldingRegisters(addr2, subQty*2);
					    	  }else {
					    		  readFC = modbusClient.ReadInputRegisters(addr2, subQty*2);
					    	  }
						      for(int i=0;i<readFC.length;i=i+2) {
						    	  subFC03 = Arrays.copyOfRange(readFC,i,i+2);
						    	  pointValue = ModbusClient.ConvertRegistersToFloat(subFC03);
					    		  subPointList2.get(j).setPointValue(pointValue);
					    		  j++;
					    	  }
					      }
			    	  }
				  }
		      }
		}
	} catch (Exception e) {
		System.out.println(e.getMessage());
	}
	}
	
	private static List<Point> getPointList() {
		List<Point> pointList = new ArrayList<>();
		try {
			File file = ResourceUtils.getFile("classpath:modBus.xlsx");
			FileInputStream fis = new FileInputStream(file);
			XSSFWorkbook wb = new XSSFWorkbook(fis);
			XSSFSheet sh = wb.getSheet("modbus");
			DataFormatter formatter = new DataFormatter();
		    for(int i=1;i<sh.getLastRowNum()+1;i++) {
		    	Point point = new Point();
		    	point.setFunctionCode(FunctionCodeEnum.valueOf(formatter.formatCellValue(sh.getRow(i).getCell(0))));
		    	point.setEquipment(formatter.formatCellValue(sh.getRow(i).getCell(1)));
		    	point.setAddress(formatter.formatCellValue(sh.getRow(i).getCell(2)));
		    	point.setTag(formatter.formatCellValue(sh.getRow(i).getCell(3)));
		    	point.setType(PointTypeEnum.valueOf(formatter.formatCellValue(sh.getRow(i).getCell(4))));
		    	if(!ObjectUtils.nullSafeEquals(sh.getRow(i).getCell(5), null)) {
		    		point.setQty(Integer.valueOf(formatter.formatCellValue(sh.getRow(i).getCell(5))));
		    	}
		    	pointList.add(point);
		    }
		}catch(Exception e){
			System.out.println(e.getMessage());
		}
		return pointList;
	}
	
	
	private void writeToASP(ModbusClient modbusClient) {
		try {
			boolean[] boolArr = new boolean[8];
			boolArr[0] = false;
			boolArr[1] = false;
			boolArr[2] = true;
			/*boolArr[3] = true;
			boolArr[4] = false;
			boolArr[5] = true;
			boolArr[6] = true;
			boolArr[7] = false;*/
			//modbusClient.WriteMultipleCoils(100, boolArr);
			modbusClient.WriteSingleRegister(1, 10);
			modbusClient.WriteSingleRegister(2, 20);
			modbusClient.WriteSingleRegister(4, 30);
			modbusClient.WriteSingleRegister(7, 40);
			//modbusClient.WriteMultipleRegisters(100, ModbusClient.ConvertFloatToTwoRegisters((float) 14.56));
			modbusClient.WriteMultipleRegisters(14, ModbusClient.ConvertFloatToTwoRegisters((float) 11.86));
			modbusClient.WriteMultipleRegisters(10, ModbusClient.ConvertFloatToTwoRegisters((float) 10.16));
			modbusClient.WriteMultipleRegisters(12, ModbusClient.ConvertFloatToTwoRegisters((float) 12.26));
		
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	@Bean
	public Instant getTimeStamp() {
		 Clock offsetClock = Clock.offset(Clock.systemUTC(), Duration.ofHours(+8));
		 return Instant.now(offsetClock);
	}
}
