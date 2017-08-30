/**
 * @Probject Name: netty-wfj-base-dev
 * @Path: com.wfj.netty.monitor.infcHeart.java
 * @Create By Jack
 * @Create In 2015年8月25日 上午11:48:34
 * TODO
 */
package com.wfj.netty.servlet.infc;

import org.apache.zookeeper.ZooKeeper;

/**
 * @Class Name Heart
 * @Author Jack
 * @Create In 2015年8月25日
 */
public interface Health {

	/**
	 * 重新连接监控服务器
	 * @Methods Name restartHealth
	 * @Create In 2015年8月25日 By Jack 
	 * @param status 系统状态
	 */
	public void restartHealth(String status);
	
	/**
	 * 启动服务监控，后续更新请使用用updateHealth
	 * @Methods Name startHealth
	 * @Create In 2015年8月25日 By Jack
	 * @param status 被监控的系统信息，例如："Active"
	 */
	public void startHealth(String status);

	/**
	 * 更新系统状态
	 * @Methods Name updateHealth
	 * @Create In 2015年8月25日 By Jack
	 * @param status 系统状态
	 */
	public void updateHealth(String status);
	
	/**
	 * 更新系统状态
	 * @Methods Name shutdownHealth
	 * @Create In 2015年8月25日 By Jack
	 * @param status 系统状态
	 */
	public void shutdownHealth(String status);
	
	/**
	 *  设置Tomcat的应用端口号
	 * @Methods Name setListensePort
	 * @Create In 2016年3月24日 By Jack
	 * @param port void
	 */
	public void setListensePort(Integer port);
	
	public ZooKeeper getZk();
	public void setZk(ZooKeeper zk);
	public Thread getM();
	public void setIsMonitorStopBoolean(Boolean isMonitorStopBoolean) ;
	public Long getSessionId();
	public void setSessionId(Long sessionId) ;
	public byte[] getSessionPassword();
	public void setSessionPassword(byte[] sessionPassword) ;
		
}
