/**
 * @Probject Name: netty-wfj-base-dev
 * @Path: com.wfj.netty.monitorStringCallBackMethod.java
 * @Create By Jack
 * @Create In 2015年8月25日 上午11:43:13
 * TODO
 */
package com.jack.netty.servlet.watcher;

import com.jack.netty.servlet.infc.Health;
import org.apache.zookeeper.AsyncCallback.StringCallback;

/**
 * @Class Name StringCallBackMethod
 * @Author Jack
 * @Create In 2015年8月25日
 */
public class StringCallBackMethod implements StringCallback {
	
	private Health hl;
	
	public StringCallBackMethod(Health target){
		hl = target;
	}

	/* (non-Javadoc)
	 * @see org.apache.zookeeper.AsyncCallback.StringCallback#processResult(int, java.lang.String, java.lang.Object, java.lang.String)
	 */
	public void processResult(int rc, String path, Object ctx, String name) {
		// TODO Auto-generated method stub

	}

}
