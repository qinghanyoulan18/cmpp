package com.ecar.cmpp.spClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import com.ecar.cmpp.entity.MsgActiveTest;
import com.ecar.cmpp.entity.MsgActiveTestResp;
import com.ecar.cmpp.entity.MsgCommand;
import com.ecar.cmpp.entity.MsgConnect;
import com.ecar.cmpp.entity.MsgConnectResp;
import com.ecar.cmpp.entity.MsgDeliver;
import com.ecar.cmpp.entity.MsgDeliverResp;
import com.ecar.cmpp.entity.MsgHead;
import com.ecar.cmpp.entity.MsgSubmit;
import com.ecar.cmpp.entity.MsgSubmitResp;
import com.ecar.cmpp.util.MsgUtils;
import com.ecar.cmpp.util.Util;


public class SPConnection extends Thread{

	//生成一个连接线程
	private Socket socket;
	//输入输出流
	private DataInputStream din;
	private DataOutputStream dout;
	//全局的链路检测包和应答
	private MsgActiveTest activeTest;
	//全局deliver 
	private MsgActiveTestResp activeTest_resp;
	//连接是否打开
	private MsgDeliverResp deliver_resp;
	public static boolean isrunning = false;
	
	
	
	private int heart_s;
	private int heart_n;
	private int heart_c = 90;	
	private int heart_t = 60;	
	private int msg_time = 30000;  
	
	public static boolean isrecvstop = true;
	public static boolean isheartStartstop = true;
	public static boolean issendStartstop = true;
	public void startSP(){
		isrunning = true;
		initPara();
		this.recvStart();
		this.heartStart();
		this.sendStart();
	}
	
	
	public void closeSP(){
		isrunning = false;
		try {
			if(this.din != null){
				this.din.close();
				this.din = null;
			}
			if(this.dout != null){
				this.dout.close();
				this.dout = null;
			}
			if(this.socket != null){
				this.socket.close();
				this.socket = null;
			}
		} catch (IOException e) {
			Client.setText("SPConnection:关闭流异常\n");
		}
	}
	
	
	public void init(String ip,int port,String spid,String pwd,byte version) {
		try {
			this.socket = new Socket(ip,port);
			this.din = new DataInputStream(this.socket.getInputStream());
			this.dout = new DataOutputStream(this.socket.getOutputStream());
			this.login(spid,pwd,version);
		} catch (Exception ex) {
			
			Client.setText("SPConnection:初始化异常\n");
		}
	}
	
	
	public void initPara(){
		if(null != Util.getProperties("heart_c"))
			this.heart_c = Integer.parseInt(Util.getProperties("heart_c"));
		if(null != Util.getProperties("heart_t"))
			this.heart_t = Integer.parseInt(Util.getProperties("heart_t"));
		if(null != Util.getProperties("msg_time"))
			this.msg_time = Integer.parseInt(Util.getProperties("msg_time")) * 1000;
		this.activeTest = new MsgActiveTest();
		this.activeTest.setMsg_length(12);
		this.activeTest.setMsg_command(MsgCommand.CMPP_ACTIVE_TEST);
		this.activeTest_resp = new MsgActiveTestResp();
		this.activeTest_resp.setMsg_length(13);
		this.activeTest_resp.setMsg_command(MsgCommand.CMPP_ACTIVE_TEST_RESP);
		this.activeTest_resp.setReserved((byte)0);
		this.deliver_resp = new MsgDeliverResp();
		this.deliver_resp.setMsg_length(24);
		this.deliver_resp.setMsg_command(MsgCommand.CMPP_DELIVER_RESP);
		
	}
	
	/**
	 * sp  登陆
	 * @param ip
	 * @param port
	 * @return
	 */
	public void login(String spid,String pwd,byte version){
		try {
			MsgConnect msg = getCmpp_Connect(spid,pwd,version);
			byte[] connect_data = MsgUtils.packMsg(msg);
			Util.debugData("登陆数据：", connect_data);
			this.sendMsg(connect_data);
			byte[] connect_resp_data = this.recvMsg();
			MsgConnectResp connect_resp = (MsgConnectResp)MsgUtils.praseMsg(connect_resp_data);
			if(null != connect_resp){
				/**
				 * 对认证串的认证！！
				 */
				boolean b = Util.byteEquals(Util.getMd5AuthIsmg(connect_resp.getStatus(),msg.getAuthenticatorSource(),pwd),connect_resp.getAuthenticatorISMG());
				if(!b){
					Client.setText("SPConnection:认证失败！暂时未做处理\n");
				}
				if(0 == connect_resp.getStatus()){
					this.startSP();
				}else{
					Client.setText("SPConnection:登陆失败......IMSG返回的结果："+connect_resp.getStatus()+"\n");
				}
			}
		} catch (Exception e) {
			Client.setText("SPConnection:登陆时出现异常可能是ISMG未启动\n");
		}
	}
	
	
	/**
	 * 创建一个登陆消息
	 * @param id
	 * @param pwd
	 * @return
	 */
	public MsgConnect getCmpp_Connect(String spid,String pwd,byte version) {
		MsgConnect cmpp_connect = new MsgConnect();
		cmpp_connect.setMsg_length(4 + 4 + 4 + 6 + 16 + 1 + 4);
		cmpp_connect.setMsg_command(MsgCommand.CMPP_CONNECT);
		cmpp_connect.setMsg_squence(Util.getSequence());
		cmpp_connect.setSource_Addr(spid);
		String timeStamp = Util.getMMDDHHMMSS();
		byte[] b = Util.getLoginMD5(spid,pwd,timeStamp);
		cmpp_connect.setAuthenticatorSource(b);
		cmpp_connect.setVersion(version);
		cmpp_connect.setTimestamp(Integer.parseInt(timeStamp));
		return cmpp_connect;
	}
	
	/**
	 * 1将字节流发送出去
	 * 
	 * @param data
	 *            数据
	 * @param output
	 *            发送出去
	 */
	public void sendMsg(byte[] data){
		try {
			synchronized(this.dout){
				if(null != data){
					this.dout.write(data);
					this.dout.flush();
				}
			}
		} catch (IOException e) {
			Client.setText("SPConnection:发送数据异常\n");
			this.closeSP();
		}
	}
	
	/**
	 * 接收消息时,肯定是要先读消息头,先取四个字节
	 * 从输入流上接收消息
	 * @return
	 */
	public byte[] recvMsg(){
		try {
			synchronized(this.din){
				Client.setText("SPConnection:接收数据流等待...\n");
				int len = this.din.readInt();
				if(len == 0){
					return null;
				}else if(len > 0 && len < 500) {
					byte[] data = new byte[len - 4];
					this.din.readFully(data);   // 如果超时了呢?
					Util.debugData("数据", data);
					return data;
				}else{
					Client.setText("SPConnection:接收消息异常\n");
					this.closeSP();
				}
			}
		} catch (Exception ef){
			Client.setText("SPConnection:读数据发生异常\n");
			this.closeSP();
		}
		return null;
	}
	
	/**
	 *接收短信线程
	 */
	public void recvStart(){
		java.lang.Runnable runner = new java.lang.Runnable(){
			public void run(){
				isrecvstop = false;
				Client.setText("SPConnection:接收信息线程开启\n");
				while(isrunning){
					try{
						byte[] bb = recvMsg();
						//只要有消息接收 就代表连接是正常的   计时清0
						heart_s = 0;
						MsgHead msg = null;
						if(bb != null){
							msg = MsgUtils.praseMsg(bb);
						}
						if(null != msg){
							if(msg.getMsg_command() == MsgCommand.CMPP_ACTIVE_TEST_RESP){
								Client.setText("SPConnection:接收到心跳应答消息！\n");
								if(heart_n > 0){
									heart_n--;
								}
							}else if(msg.getMsg_command() == MsgCommand.CMPP_ACTIVE_TEST){
								Client.setText("SPConnection:接收到心跳消息！\n");
								activeTest_resp.setMsg_squence(msg.getMsg_squence());
								byte[] tb = MsgUtils.packMsg(activeTest_resp);
								Client.setText("SPConnection:发送心跳应答消息！\n");
								sendMsg(tb);
							}else{
								//deliver消息
								if(msg.getMsg_command() == MsgCommand.CMPP_DELIVER){
									//正常deliver消息
									MsgDeliver deliver = (MsgDeliver)msg;
									if(deliver.getRegistered_Delivery() == 0){
										StringBuffer sb = new StringBuffer();
										sb.append("上行消息:");
										sb.append(" 手机号码:"+deliver.getSrc_terminal_Id());
										sb.append(" 长号码:"+deliver.getDest_Id());
										sb.append(" 业务标识："+deliver.getService_Id());
										sb.append(" 信息内容:"+deliver.getMsg_Content());
										sb.append(" LinkID:"+deliver.getLinkID()+"\n");
										Client.setText(sb.toString());
										/**
										 * 通过deliver生成submit
										 */
										testDeliver = deliver;
										//消息报告	
									}else{
										//将报告处理结果存库
										Client.setText("report stats = "+deliver.getStat()+" "+"report msgid = "+deliver.getMsg_Id_report()+" "+"submit msgid = "+testSubmit.getMsg_Id()+" \n");
									}
									//回复应答
									deliver_resp.setMsg_squence(deliver.getMsg_squence());
									deliver_resp.setMsg_Id(deliver.getMsg_Id());
									deliver_resp.setResult(0);
									Client.setText("SPConnection:����deliverӦ����Ϣ\n");
									sendMsg(MsgUtils.packMsg(deliver_resp));
								}else if(msg.getMsg_command() == MsgCommand.CMPP_SUBMIT_RESP){
									//通知已发送成功
									MsgSubmitResp sub_resp = (MsgSubmitResp)msg;
									//MsgUtils.busyness_Submit_Resp(sub_resp);
									testSubmit.setMsg_Id(sub_resp.getMsg_Id());
									
								}else{
									Client.setText("SPConnection:其他类型消息接收,不做任何处理\n");
								}
							}
						}
						
					}catch(Exception e){
						Client.setText("SPConnection:接收信息发生异常\n"+e.toString());
						closeSP();
					}
				}
				isrecvstop = true;
				Client.setText("SPConnection:接收信息线程断开！\n");
			}
			
		};
		Thread t = new Thread(runner);
		t.start();
	}
	
	
	/**
	 * 心跳	定时发送心跳包
	 * 
	 */

	public void heartStart() {
		java.lang.Runnable runner = new java.lang.Runnable() {
			@SuppressWarnings({ "static-access" })
			public void run(){
				isheartStartstop = false;
				Client.setText("SPConnection:心跳线程开启\n");
				//发送心跳包
				
				try {
					activeTest.setMsg_squence(Util.getSequence());
					byte[] bs = MsgUtils.packMsg(activeTest);
					Client.setText("SPConnection:发送心跳消息！\n");
					sendMsg(bs);
				} catch (Exception e1) {
					Client.setText("SPConnection:发送心跳包异常\n");
					closeSP();
				}
				
				
				while(isrunning){
					try{
						Thread.currentThread().sleep(1000);
						//180秒  即3分钟
						heart_s++;
						
						if(heart_n >= 3){
							if(heart_s == heart_t){
								closeSP();
								break;
							}
						}
						if(heart_s == heart_t){
							if(heart_n > 0){
								//发送心跳包
								Client.setText("SPConnection:紧急发送心跳包\n ");
								activeTest.setMsg_squence(Util.getSequence());
								heart_s = 0;
								heart_n ++;
								byte[] b = MsgUtils.packMsg(activeTest);
								sendMsg(b);
								//时间置0
								continue;
							}
						}
						if(heart_s == heart_c){
							//发送心跳包
							activeTest.setMsg_squence(Util.getSequence());
							heart_s = 0;
							heart_n ++;
							byte[] b = MsgUtils.packMsg(activeTest);
							Client.setText("SPConnection:发送心跳消息！\n");
							sendMsg(b);
							//时间置0
						}
							
					}catch(Exception e){
						Client.setText("SPConnection:发送心跳包异常\n");
						closeSP();
					}
				}
				isheartStartstop = true;
				Client.setText("SPConnection:发送心跳线程 断开！\n");
			}
		};
		Thread t = new Thread(runner);
		t.start();
	}
	
	
	/**
	 * 启动一个线程，发送短信
	 */
	private MsgDeliver testDeliver;
	private MsgSubmit testSubmit;
	public void sendStart() {
		java.lang.Runnable runner = new java.lang.Runnable() {
			@SuppressWarnings({ "static-access" })
			public void run(){
				issendStartstop = false;
				Client.setText("SPConnection:发送信息线程开启\n");
				while(isrunning){
					try {
						Thread.currentThread().sleep(msg_time);
						if(null != testDeliver){
							testSubmit = intoSubmit();
							
							byte[] b = MsgUtils.packMsg(testSubmit);
							sendMsg(b);
							testDeliver = null;
						}
					}catch (Exception e) {
						Client.setText("SPConnection:发送submit信息出现异常\n");
						closeSP();
					}
				}
				issendStartstop = true;
				Client.setText("SPConnection:发送信息线程 断开！\n");
			}
		};
		Thread t = new Thread(runner);
		t.start();
	}
	
	public MsgSubmit intoSubmit(){
		MsgSubmit submit = new MsgSubmit();
		int SEQUENCE_ID = Util.getSequence();
		long MSG_ID = 0;
		byte PK_TOTAL = 1;
		byte PK_NUMBER = 1;
		byte REGISTERED_DELIVERY = 1;
		byte MSG_LEVEL = 1;
		String SERVICE_ID = "-DB";
		byte FEE_USERTYPE = 0;
		String FEE_TERMINAL_ID = "";
		byte FEE_TERMINAL_TYPE = 0;
		byte TP_PID = 0;
		byte TP_UDHI = 0;
		byte MSG_FMT = 0;
		String MSG_SRC = "901234";
		String FEETYPE = "01";
		String FEECODE = "00030";
		String VALID_TIME = "";
		String AT_TIME = "";
		String SRC_ID = "1064899110307";
		byte DESTUSR_TL = 1;
		String DEST_TERMINAL_ID = "1064782467052";
		byte DEST_TERMINAL_TYPE = 0;
		//信息内容
		String MSG_CONTENT = "";
		MSG_CONTENT = "222222。";
		int msgLen = MSG_CONTENT.getBytes().length;
		String LINKID = "1";
		int totalLen = 12 + 8 + 1 + 1 + 1 + 1 + 10 + 1 + 32 + 1 + 1 + 1 + 1 + 6
		+ 2 + 6 + 17 + 17 + 21 + 1 + 32 + 1 + 1 + msgLen + 20;
		
		submit.setMsg_length(totalLen);
		submit.setMsg_squence(SEQUENCE_ID);
		submit.setMsg_command(MsgCommand.CMPP_SUBMIT);
		submit.setMsg_Id(MSG_ID);
		submit.setPk_total(PK_TOTAL);
		submit.setPk_number(PK_NUMBER);
		submit.setRegistered_Delivery(REGISTERED_DELIVERY);
		submit.setMsg_level(MSG_LEVEL);
		submit.setService_Id(SERVICE_ID);
		submit.setFee_UserType(FEE_USERTYPE);
		submit.setFee_terminal_Id(FEE_TERMINAL_ID);
		submit.setFee_terminal_type(FEE_TERMINAL_TYPE);
		submit.setTP_pId(TP_PID);
		submit.setTP_udhi(TP_UDHI);
		submit.setMsg_Fmt(MSG_FMT);
		submit.setMsg_src(MSG_SRC);
		submit.setFeeType(FEETYPE);
		submit.setFeeCode(FEECODE);
		submit.setValId_Time(VALID_TIME);
		submit.setAt_Time(AT_TIME);
		submit.setSrc_Id(SRC_ID);
		submit.setDestUsr_tl(DESTUSR_TL);
		submit.setDest_terminal_Id(DEST_TERMINAL_ID);
		submit.setDest_terminal_type(DEST_TERMINAL_TYPE);
		submit.setMsg_Length((byte)msgLen);
		submit.setMsg_Content(MSG_CONTENT);
		submit.setLinkID(LINKID);
		
		StringBuffer sb = new StringBuffer();
		sb.append("下行消息:");
		sb.append(" 手机号码:"+submit.getDest_terminal_Id());
		sb.append(" 长号码:"+submit.getSrc_Id());
		sb.append(" 业务标识:"+submit.getService_Id());
		sb.append(" 信息内容:"+MSG_CONTENT);
		sb.append(" LinkID:"+submit.getLinkID()+"\n");
		Client.setText(sb.toString());
		return submit;
	}
	
}
