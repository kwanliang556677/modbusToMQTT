package com.primustech.modbusToMqtt;

public class Point {
	private FunctionCodeEnum functionCode;
	private String equipment;
	private String address;
	private String tag;
	private PointTypeEnum type;
	private Object pointValue;
	private Integer qty;

	public FunctionCodeEnum getFunctionCode() {
		return functionCode;
	}

	public void setFunctionCode(FunctionCodeEnum functionCode) {
		this.functionCode = functionCode;
	}

	public String getEquipment() {
		return equipment;
	}

	public void setEquipment(String equipment) {
		this.equipment = equipment;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public PointTypeEnum getType() {
		return type;
	}

	public void setType(PointTypeEnum type) {
		this.type = type;
	}

	public Object getPointValue() {
		return pointValue;
	}

	public void setPointValue(Object pointValue) {
		this.pointValue = pointValue;
	}

	public Integer getQty() {
		return qty;
	}

	public void setQty(Integer qty) {
		this.qty = qty;
	}
}
