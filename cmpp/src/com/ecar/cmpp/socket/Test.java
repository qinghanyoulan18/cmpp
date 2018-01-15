package com.ecar.cmpp.socket;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.ecar.cmpp.util.CharSetUtil;

public class Test {

	public static void main(String[] args) {
		//接收网关短信的手机号码集合
		String recP[]={"1064782467052"};
		//初始化短信发送
		CMPPClient cmppClient=new CMPPClient("183.230.96.94",17890, "110307", "110307","110307","ykclw");
		//发送短信
//		String msgContent = "测试";
//		msgContent = EncodeUCS2(msgContent).toLowerCase();
		cmppClient.sendNotifySms("1064899110307", "HHHHSSS", recP);   //1064899110307未短信接入码
	}
	
	/**
     * UCS2编码
     * 
     * @param src
     *            UTF-16BE编码的源串
     * @return 编码后的UCS2串
     */
    public static String EncodeUCS2(String src) {

        byte[] bytes = null;
        try {
            bytes = src.getBytes("UTF-16BE");
        } catch (UnsupportedEncodingException e) {
        	e.printStackTrace();
        }

        StringBuffer reValue = new StringBuffer();
        StringBuffer tem = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            tem.delete(0, tem.length());
            tem.append(Integer.toHexString(bytes[i] & 0xFF));
            if(tem.length()==1){
                tem.insert(0, '0');
            }
            reValue.append(tem);
        }
        return reValue.toString().toUpperCase();
    }

}
