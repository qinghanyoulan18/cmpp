package com.ecar.cmpp.entity;

public class MsgHead {
	//消息总长度(含消息头及消息体)
	private int msg_length;
	//命令或响应类型
	private int msg_command;
	//消息流水号,顺序累加,步长为1,循环使用（一对请求和应答消息的流水号必须相同）
	private int msg_squence;
	
	public int getMsg_length() {
		return msg_length;
	}
	
	public void setMsg_length(int msg_length) {
		this.msg_length = msg_length;
	}
	
	public int getMsg_command() {
		return msg_command;
	}
	
	public void setMsg_command(int msg_command) {
		this.msg_command = msg_command;
	}
	
	public int getMsg_squence() {
		return msg_squence;
	}
	
	public void setMsg_squence(int msg_squence) {
		this.msg_squence = msg_squence;
	}
	
}
