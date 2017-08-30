/**
 * @Probject Name: netty-wfj-base-dev
 * @Path: com.wfj.netty.monitorHeartLive.java
 * @Create By Jack
 * @Create In 2015年8月25日 上午9:49:49
 * TODO
 */
package com.wfj.netty.servlet;

import javax.servlet.ServletContext;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wfj.netty.servlet.conf.EnvPropertyConfig;
import com.wfj.netty.servlet.conf.SystemPropertyConfig;
import com.wfj.netty.servlet.handler.AppMonitor;
import com.wfj.netty.servlet.handler.factory.ZKConnectManager;
import com.wfj.netty.servlet.infc.Health;

/**
 * 服务器心跳组件
 * 
 * @Class Name HeartLive
 * @Author Jack
 * @Create In 2015年8月25日
 */
public class MonitorChecker implements Health {

	private Logger log = LoggerFactory.getLogger(this.getClass());

	private String appName;

	private ZooKeeper zk;

	private Long sessionId;

	private byte[] sessionPassword;

	private String appServerPath;

	private Thread m;

	private Boolean isMonitorStop = false;

	private Boolean isDebug;


	private static String ZK_MONITOR_HEARLTH_THREAD_NAME = "netty-wfj-server-monitor-hearth-thread";
	
	private AppMonitor appMonitor;
	
	
	
	/**
	 * @Return the Thread m
	 */
	public Thread getM() {
		return m;
	}

	/**
	 * @Return the AppMonitor appMonitor
	 */
	public AppMonitor getAppMonitor() {
		return appMonitor;
	}

	/**
	 * @Param AppMonitor appMonitor to set
	 */
	public void setAppMonitor(AppMonitor appMonitor) {
		this.appMonitor = appMonitor;
	}

	/**
	 * @Return the Long sessionId
	 */
	public Long getSessionId() {
		return sessionId;
	}

	/**
	 * @Param Long sessionId to set
	 */
	public void setSessionId(Long sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * @Return the byte[] sessionPassword
	 */
	public byte[] getSessionPassword() {
		return sessionPassword;
	}

	/**
	 * @Param byte[] sessionPassword to set
	 */
	public void setSessionPassword(byte[] sessionPassword) {
		this.sessionPassword = sessionPassword;
	}


	/**
	 * @Return the Boolean isMonitorStopBoolean
	 */
	public Boolean getIsMonitorStopBoolean() {
		return isMonitorStop;
	}

	/**
	 * @Param Boolean isMonitorStopBoolean to set
	 */
	public void setIsMonitorStopBoolean(Boolean isMonitorStopBoolean) {
		this.isMonitorStop = isMonitorStopBoolean;
	}

	/**
	 * @Return the String appServerPath
	 */
	public String getAppServerPath() {
		return appServerPath;
	}

	/**
	 * @Param String appServerPath to set
	 */
	public void setAppServerPath(String appServerPath) {
		this.appServerPath = appServerPath;
	}

	/**
	 * @Return the String appName
	 */
	public String getAppName() {
		return appName;
	}

	/**
	 * @Param String appName to set
	 */
	public void setAppName(String appName) {
		this.appName = appName;
	}

	/**
	 * @Return the ZooKeeper zk
	 */
	public ZooKeeper getZk() {
		return zk;
	}

	/**
	 * @Param ZooKeeper zk to set
	 */
	public void setZk(ZooKeeper zk) {
		this.zk = zk;
	}
	
	public MonitorChecker(Integer p, ServletContext sc) {
		init( p, sc );
	}
	
	/**
	 * 初始化系统信息
	 * 
	 * @Methods Name init
	 * @Create In 2015年8月26日 By Jack 
	 * @param p 控制端口
	 * @param sc Tomcat 的 ServletContext
	 */
	private void init(Integer p, ServletContext sc) {

		isDebug = Boolean.valueOf(SystemPropertyConfig.getContextProperty("system.seeting.monitor.isDebug", "true"));
		
		if (!isDebug) {
			this.appName = SystemPropertyConfig.getContextProperty("system.setting.context-name");
			appMonitor = AppMonitor.instance(p, sc);
			zk = ZKConnectManager.creteZooKeeper(this);
		}
	}


	/**
	 * 实例JVM及系统监控状况服务
	 * 
	 * @Methods Name hearthCheck
	 * @Create In 2015年8月26日 By Jack
	 * @param splitTime
	 *            毫秒millis
	 */
	private void hearthCheck(final Long splitTime) {
		isMonitorStop = new Boolean(false);

		this.m = new Thread(new Runnable() {
			public void run() {
				try {
					while (!isMonitorStop) {
						// 1.构建系统实时状态并存储
						appMonitor.buildAppInfo(zk, appServerPath);
						// 2.构造 SQL 及其他计数信息并发送
						appMonitor.buildSQLCountsInfo();
						appMonitor.buildRequestCountInfo(); 
						// 3.进入休眠，等待下一次执行，默认5分钟执行一次
						Thread.sleep(splitTime);
					}
					log.debug("Health Monitor Service is Stop!");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001012"));
					log.error("Details: " + e.getMessage());
					Thread.currentThread().interrupt();
				}
			}
		}, MonitorChecker.ZK_MONITOR_HEARLTH_THREAD_NAME);
		m.start();
	}

	

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.wfj.netty.monitor.infc.Heart#sendHeartLive(java.lang.String)
	 */
	public void startHealth(String status) {

		try {
			if (isDebug)
				return;
			if (!ZKConnectManager.getIsReconnection() && !ZKConnectManager.getIsWholeDateCheck()) {
				// 1.初始化跟踪节点
				this.appServerPath = appMonitor.buildMonitorRootInfo(zk, status);
				// 2 启动实例状态监控服务
				hearthCheck(new Long(EnvPropertyConfig.getContextProperty("env.setting.server.monitor.checker.sleeptime")));
			}
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001010"));
			log.error("Details: " + e.getMessage());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001010"));
			log.error("Details: " + e.getMessage());
		}

	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.wfj.netty.monitor.infc.Heart#updateHealth(java.lang.String)
	 */
	public void updateHealth(String status) {

		if (isDebug)
			return;

		Stat stat = new Stat();
		try {
			// 更新自身节点状态
			zk.getData(appServerPath, true, stat);
			Object[] tagArgs = { status };
			String rootDesc = SystemPropertyConfig.getContextProperty("system.setting.context-desc");
			rootDesc = SystemPropertyConfig.fromatter(rootDesc, tagArgs);
			zk.setData(appServerPath, rootDesc.getBytes(), stat.getVersion());

		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001011"));
			log.error("Details: " + e.getMessage());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001011"));
			log.error("Details: " + e.getMessage());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.wfj.netty.monitor.infc.Heart#shutdowdHeartLive(java.lang.String)
	 */
	public void shutdownHealth(String status) {

		if (isDebug)
			return;

		// 1.停止实例信息获取模块
		if (this.m != null) {
			this.isMonitorStop = true;
		}else{
			this.isMonitorStop = false;
		}
		// 2.停止zk 链接监控线程
		ZKConnectManager.shutdownZK(zk, status);

	}

	@Override
	public void restartHealth(String status) {
		// TODO Auto-generated method stub
		shutdownHealth(status);
		ZKConnectManager.reconnection(zk, this);
	
	}

	@Override
	public void setListensePort(Integer port) {
		// TODO Auto-generated method stub
		this.appMonitor.setPort(port);
	}

}
