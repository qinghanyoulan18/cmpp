package com.ecar.cmpp.except;

import java.io.IOException;

/**
	 * <p>Title:CMPPException</p>
	 * <p>Description: cmpp建立和关闭连接时候封装IOException</p>
	 * <p>Company: </p> 
	 * @author ecar 
	 * @date 2017-11-1 下午04:59:44
 */
public class CMPPException extends IOException {

	public CMPPException() {
        super();
    }

    public CMPPException(String msg) {
        super(msg);
    }
    
}
