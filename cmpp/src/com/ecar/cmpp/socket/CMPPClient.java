package com.ecar.cmpp.socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
import com.google.gson.Gson;

public class CMPPClient {

	protected static final Log log = LogFactory.getLog(CMPPClient.class);
	
	private String ip; // 服务端ip
	private int port; // 服务端口
	private String spid;// 鉴权账号
	private String password;// 鉴权密码
	private String msgsrc;// 企业代码
	private String serviceId;// 服务Id
	
	//显示到接收手机端的主叫号码
	private String servicenumber;
	//发送的短信内容
	private String msgContent;
	//字节输入流
	private DataInputStream din;
	//字节输出流
	private DataOutputStream dout;
	//链路检查
	private MsgActiveTestResp activeTestResp;
	//短信下发相应
	private MsgDeliverResp deliverResp;
	//记录是否在和网关保持通讯
	public static boolean isrunning = false;
	
	/**
	 * 初始化短信发送器
	 * @param ip        服务端ip
	 * @param port      服务端口
	 * @param spid		鉴权账号
	 * @param password  鉴权密码
	 * @param msgsrc	企业代码
	 * @param serviceId	服务Id
	 */
	public CMPPClient(String ip, int port, String spid, String password,
			String msgsrc, String serviceId) {
		this.ip = ip;
		this.port = port;
		this.spid = spid;
		this.password = password;
		this.msgsrc = msgsrc;
		this.serviceId = serviceId;
	}
	
	/**
	 * 发送短信
	 * @param servicenumber  显示到接收手机端的主叫号码
	 * @param msgContent	   发送的短信内容
	 * @param receiveList    接收短信的号码集合
	 * @return
	 */
	public String sendNotifySms(String servicenumber, String msgContent,String[] receiveList) {
		try {
			// 新建CMPP封装的SOCKET
			CMPPSocket cmppSocket = new CMPPSocket(this.ip, this.port);
			// 初始化SOCKET
			cmppSocket.initialSock();
			CMPPService cmppService = new CMPPService(cmppSocket);
			this.din = new DataInputStream(cmppSocket.getInputStream());
			this.dout = new DataOutputStream(cmppSocket.getOs());
			//1、登陆连接到网关
			int connStatus = this.login(this.spid, this.password, (byte) 3);
			if (connStatus == 0) {
				log.info("登录成功!--------");
				//2、发送链路检测包
				int testStatus = cmppService.cmppActiveTest();
				// 链路测试成功
				if (testStatus == 0) {
					log.info("登录成功，链路检测成功!!!");
					isrunning = true;
					this.servicenumber = servicenumber;
					this.msgContent = msgContent;
					//3、开始发送短信
					this.sendStart();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			log.error("发送短信异常！"+e.getMessage(), e);
		}
		return msgContent;
	}

	/**
	 *  与网关建立联系
	 * @param spid 		登录的账号
	 * @param pwd	 	登录的密码
	 * @param version   版本
	 * @return
	 */
	public int login(String spid, String pwd, byte version) {
		log.info("登录的账号是:====>" + spid);
		log.info("登录的密码是:====>" + pwd);
		log.info("协议版本是:====>" + version);
		try {
			MsgConnect msg = getCmpp_Connect(spid, pwd, version);
			//获取封装的消息类字节数组
			byte[] connect_data = MsgUtils.packMsg(msg);
			//发送消息，将封装的消息类字节数组发送出去
			this.sendMsg(connect_data);
			//获取网关返回的消息
			byte[] connect_resp_data = this.recvMsg();
			//将返回的连接网关的消息转化为连接相应报文
			MsgConnectResp connect_resp = (MsgConnectResp) MsgUtils.praseMsg(connect_resp_data);
			if (null != connect_resp) {
				// 登陆成功 启动其他线程
				if (0 == connect_resp.getStatus()) {
					log.info("连接网关服务器成功！");
					return 0;
				} else {
					log.info("连接网关服务器失败！");
					return -1;
				}
			}
			// 做出其他处理
		} catch (Exception e) {
			log.error("SPConnection:登陆时出现异常可能是ISMG未启动！"+e.getMessage(), e);
		}
		return -1;
	}
	
	private MsgSubmit testSubmit;
	
	/**
	 * 开始发送短信内容给终端设备
	 */
	public void sendStart() {
		log.info("启动发短信线程-----");
		try {
			Gson gson = new Gson();
			//1、初始化短信提交消息
			testSubmit = intoSubmit();
			//打包短信内容到字节包
			byte[] b = MsgUtils.packMsg(testSubmit);
			//发送字节包到网关
			sendMsg(b);
			log.info("短信已经发送出去");
			isrunning = true;
			while (isrunning) {
				try {
					//获取从网关获取的响应
					byte[] bb = recvMsg();
					// 只要有消息接收 就代表连接是正常的 计时清0
					MsgHead msg = null;
					if (bb != null) {
						//将字节转化为协议内容
						msg = MsgUtils.praseMsg(bb);
					}
					if (null != msg) {
						if (msg.getMsg_command() == MsgCommand.CMPP_ACTIVE_TEST_RESP) {
							log.info("SPConnection:接收到心跳应答消息！\n");
						} else if (msg.getMsg_command() == MsgCommand.CMPP_ACTIVE_TEST) {
							log.info("SPConnection:接收到心跳消息！\n");
							activeTestResp.setMsg_squence(msg.getMsg_squence());
							byte[] tb = MsgUtils.packMsg(activeTestResp);
							log.info("SPConnection:发送心跳应答消息！\n");
							sendMsg(tb);
						} else {
							// deliver消息
							if (msg.getMsg_command() == MsgCommand.CMPP_DELIVER) {
								// 正常deliver消息
								MsgDeliver deliver = (MsgDeliver) msg;
								if (deliver.getRegistered_Delivery() == 0) {
									log.info("短信网关推送非状态报告------"+gson.toJson(deliver));
									// 消息报告
								} else {
									// 将报告处理结果存库
									log.info("短信网关推送状态报告成功------"+gson.toJson(deliver));
								}
								// 回复应答
								deliverResp = new MsgDeliverResp();
								deliverResp.setMsg_squence(deliver.getMsg_squence());
								deliverResp.setMsg_Id(deliver.getMsg_Id());
								deliverResp.setResult(0);
								sendMsg(MsgUtils.packMsg(deliverResp));
							} else if (msg.getMsg_command() == MsgCommand.CMPP_SUBMIT_RESP) {
								// 通知已发送成功
								MsgSubmitResp subResp = (MsgSubmitResp) msg;
								testSubmit.setMsg_Id(subResp.getMsg_Id());
								int result = subResp.getResult();
								log.info("发送短信返回结果的状态码为："+result);
								//如果短信结果为0，则表示发送成功
								if(result == 0){
									log.info("短信发送成功！");
								}else{
									log.info("短信发送失败！");
								}
								log.info("短信发送的对象为："+ gson.toJson(subResp));
//								isrunning = false;
							} else {
								log.info("SPConnection:其他类型消息接收,不做任何处理\n");
							}
						}
					}
				} catch (Exception e) {
					log.info("SPConnection:接收信息发生异常\n"+e.getMessage(),e);
				}
			}
		} catch (Exception e) {
			log.info("发送短信异常"+e.getMessage(),e);
		}
	}
	
	// 接收消息时,肯定是要先读消息头,先取四个字节
	// 从输入流上接收消息
	public byte[] recvMsg() {
		try {
			synchronized (this.din) {
				//读取数据
				int len = this.din.readInt();
				if (len == 0) {
					return null;
				} else if (len > 0 && len < 500) {
					byte[] data = new byte[len - 4];
					this.din.readFully(data); 
					return data;
				} else {
					log.info("SPConnection:不是正常的消息数据");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 发送字节消息到网关
	 * 
	 * @param data 发送的字节数据
	 */
	public void sendMsg(byte[] data) {
		try {
			synchronized (this.dout) {
				if (null != data) {
					this.dout.write(data);
					this.dout.flush();
				}
			}
		} catch (IOException e) {

		}
	}
	
	/**
	 * 初始化短信提交到网关信息
	 * @return
	 */
	public MsgSubmit intoSubmit() {
		MsgSubmit submit = new MsgSubmit();
		int SEQUENCE_ID = Util.getSequence();
		long MSG_ID = 0;
		byte PK_TOTAL = 1;
		byte PK_NUMBER = 1;
		byte REGISTERED_DELIVERY = 1;
		byte MSG_LEVEL = 1;
		String SERVICE_ID = serviceId;
		byte FEE_USERTYPE = 0x00;
		String FEE_TERMINAL_ID = "";
		byte FEE_TERMINAL_TYPE = 0;
		byte TP_PID = 0;
		byte TP_UDHI = 0;
		byte MSG_FMT = 0;
		String MSG_SRC = msgsrc;
		String FEETYPE = "01";
		String FEECODE = "00030";
		String VALID_TIME = "";
		String AT_TIME = "";
		String SRC_ID = servicenumber;
		byte DESTUSR_TL = 1;
		String DEST_TERMINAL_ID = "1064782467052";
		byte DEST_TERMINAL_TYPE = 0;
		// 信息内容
		String MSG_CONTENT = msgContent;
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
		submit.setMsg_Length((byte) msgLen);
		submit.setMsg_Content(MSG_CONTENT);
		submit.setLinkID(LINKID);

		return submit;
	}

	/**
	 * 构造CMPP_CONNECT 协议数据包
	 * 
	 * @param id
	 * @param pwd
	 * @return
	 */
	public MsgConnect getCmpp_Connect(String spid, String pwd, byte version) {
		MsgConnect cmpp_connect = new MsgConnect();
		cmpp_connect.setMsg_length(4 + 4 + 4 + 6 + 16 + 1 + 4);    //消息(包括消息头和消息体)的长度为39个字节
		cmpp_connect.setMsg_command(MsgCommand.CMPP_CONNECT);
		cmpp_connect.setMsg_squence(Util.getSequence());
		cmpp_connect.setSource_Addr(spid);
		String timeStamp = Util.getMMDDHHMMSS();
		byte[] b = Util.getLoginMD5(spid, pwd, timeStamp);
		cmpp_connect.setAuthenticatorSource(b);
		cmpp_connect.setVersion(version);
		cmpp_connect.setTimestamp(Integer.parseInt(timeStamp));
		return cmpp_connect;
	}
	
}
