package com.ecar.cmpp.socket;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ecar.cmpp.modle.CMPPActive;
import com.ecar.cmpp.modle.CMPPActiveResp;
import com.ecar.cmpp.modle.CMPPRequestPacket;
import com.ecar.cmpp.util.CommandIdUtil;


/**
	 * <p>Title:CMPPService</p>
	 * <p>Description: cmpp协议api,通过调用此api实现cmpp协议的各个消息 <br>
     * 在调用此类之前,必须先调用CMPPSocket类的initialSock方法,实现和网关socket连接的初始化</p>
	 * <p>Company: </p> 
	 * @author ecar 
	 * @date 2017-11-1 下午05:08:49
 */
public class CMPPService {
	/**
	 * Logger for this class
	 */
	private static final Log log = LogFactory.getLog(CMPPService.class);

	/**
	 * cmpp的socket连接
	 */
	private CMPPSocket socket;

	/**
	 * cmpp请求包体
	 */

	private CMPPRequestPacket packet;

	/**
	 * response消息的最大返回时间，单位为秒
	 */
	private int delayTime;

	/**
	 * 构造函数
	 * 
	 * @param socket
	 *            发送和接收cmpp消息的时候，使用的socket连接
	 * @param delayTime
	 *            接收response消息的最大返回时间
	 */
	public CMPPService(CMPPSocket socket, int delayTime) {
		this.socket = socket;
		this.packet = new CMPPRequestPacket();
		this.delayTime = delayTime;

	}

	/**
	 * 构造函数,默认延迟时间为10秒
	 * @param socket
	 */
	public CMPPService(CMPPSocket socket) {
		this.socket = socket;
		this.packet = new CMPPRequestPacket();
		this.delayTime = 10;// 默认时间为10秒
	}
	
	/**
	 * 发送链路检测包 <br>
	 * 本操作仅适用于通信双方采用长连接通信方式时用于保持连接。
	 * @return int 0：成功 <br>
	 *         -1：获得返回包延迟阻塞 <br>
	 * @throws IOException
	 */
	public int cmppActiveTest() throws IOException {
		packet.setCommandId(CommandIdUtil.CMPP_ACTIVE_TEST);
		packet.setSequenceId(getSequence());
		packet.setRequestBody(new CMPPActive());
		try {
			socket.write(packet);
			CMPPActiveResp resp = new CMPPActiveResp();
			long begin = System.currentTimeMillis();
			// 循环等待10秒,超过10秒,认为这条短信发送失败
			while (true) {
				long now = System.currentTimeMillis();
				if (socket.getInputStream().available() > 0) {
					//socket连接上读取输入流
					byte[] packetbytes = socket.read();
					if (packetbytes.length != resp.getTotalLength()) {
						begin = System.currentTimeMillis();// 时间重置
						continue;
					}
					//解析相应的消息体， 解析从输入流得到的包体字节流
					resp.parseResponseBody(packetbytes);
					log.info("读取输入流时成功，返回0");
					return 0;
				} else if (now - begin > delayTime * 1000) {
					log.info("读取输入流时阻塞，返回-1");
					return -1;
				}
			}
		} catch (IOException e) {
			log.error("active链路检测消息IO错误:" + e.getMessage(),e);
			throw e;
		}
	}
	
	private int sequence = 0;
	
	/**
	 * 取得每次操作的序列号,步长为1,重复使用
	 * 
	 * @return int
	 */
	private int getSequence() {
		sequence++;
		if (sequence > 0x7fffffff)
			sequence = 2;
		return sequence;
	}
	

}