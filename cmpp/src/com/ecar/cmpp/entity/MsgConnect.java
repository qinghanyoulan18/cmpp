package com.ecar.cmpp.entity;

public class MsgConnect extends MsgHead {
	//源地址，此处为SP_Id，即SP的企业代码
	private String  source_Addr;
    //用于鉴别源地址
	private byte[]  authenticatorSource;
	//双方协商的版本号
	private byte version;
	//时间戳的明文,由客户端产生,格式为MMDDHHMMSS，即月日时分秒，10位数字的整型，右对齐 。
	private int timestamp;
	
	
	public String getSource_Addr() {
		return source_Addr;
	}
	public void setSource_Addr(String source_Addr) {
		this.source_Addr = source_Addr;
	}
	public byte[] getAuthenticatorSource() {
		return authenticatorSource;
	}
	public void setAuthenticatorSource(byte[] authenticatorSource) {
		this.authenticatorSource = authenticatorSource;
	}
	public byte getVersion() {
		return version;
	}
	public void setVersion(byte version) {
		this.version = version;
	}
	public int getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}
	
	
	
}
