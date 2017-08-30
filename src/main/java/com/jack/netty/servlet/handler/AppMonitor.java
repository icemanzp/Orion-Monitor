/**
 * @Probject Name: netty-wfj-monitor
 * @Path: com.wfj.netty.monitor.handlerAppMonitor.java
 * @Create By Jack
 * @Create In 2015年11月10日 下午5:17:46
 * TODO
 */
package com.wfj.netty.servlet.handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletContext;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wfj.netty.servlet.conf.Constant;
import com.wfj.netty.servlet.conf.EnvPropertyConfig;
import com.wfj.netty.servlet.conf.SystemPropertyConfig;
import com.wfj.netty.servlet.dto.ApplicationInfo;
import com.wfj.netty.servlet.dto.CounterRequest;
import com.wfj.netty.servlet.dto.JavaInformations;
import com.wfj.netty.servlet.dto.RequestInfo;
import com.wfj.netty.servlet.dto.SQLInfo;
import com.wfj.netty.servlet.dto.SystemInfo;
import com.wfj.netty.servlet.handler.factory.KafkaConnectManager;
import com.wfj.netty.servlet.handler.factory.SLACountManager;
import com.wfj.netty.servlet.handler.wrapper.JdbcWrapper;
import com.wfj.netty.servlet.handler.wrapper.RequestWrapper;
import com.wfj.netty.servlet.util.JacksonMapperUtil;

/**
 * @Class Name AppMonitor
 * @Author Jack
 * @Create In 2015年11月10日
 */
public class AppMonitor {

	private Logger log = LoggerFactory.getLogger(this.getClass());

	private static AppMonitor appMonitor = null;

	private Integer port;
	
	private JavaInformations javaInfor;

	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * @Param Integer port to set
	 */
	public void setPort(Integer port) {
		this.port = port;
	}

	private final ServletContext sc;

	private AppMonitor(Integer p, ServletContext sc) {
		this.port = p;
		this.sc = sc;

		javaInfor = JavaInformations.instance(sc, true);
	}

	/**
	 * 获取实例
	 * 
	 * @Methods Name instance
	 * @Create In 2015年11月10日 By Jack
	 * @param p
	 *            控制端口
	 * @return AppMonitor
	 */
	public static AppMonitor instance(Integer p, ServletContext sc) {
		if (appMonitor != null) {
			return appMonitor;
		} else {
			appMonitor = new AppMonitor(p, sc);
			return appMonitor;
		}
	}

	/**
	 * 初始化 ZK监控根节点信息
	 * 
	 * @Methods Name buildMonitorRootInfo
	 * @Create In 2015年9月7日 By Jack
	 * @param zk
	 *            Zookeeper实例
	 * @param status
	 *            一般为 Active
	 * @throws KeeperException
	 * @throws InterruptedException
	 * @return String AppServerPath 监控系统根目录地址
	 */
	public String buildMonitorRootInfo(ZooKeeper zk, String status) throws KeeperException, InterruptedException {
		// 1.
		// 判断用于监控的父节点是否存在，如不存在则建立，每个集成Netty-WFJ-Base的服务均会检查此配置，争抢建立,利用Zookeeper的原生节点创建锁完成
		String rootPath = EnvPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_SERVER_MONITOR_ROOT_PATH);
		boolean isMonitorRootExist;
		isMonitorRootExist = zk.exists(rootPath, true) != null ? true : false;
		if (!isMonitorRootExist) {
			Object[] tagArgs = { "Active" };
			String rootDesc = EnvPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_SERVER_MONITOR_ROOT_DESC);
			rootDesc = EnvPropertyConfig.fromatter(rootDesc, tagArgs);
			zk.create(rootPath, rootDesc.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		}

		// 2.
		// 判断用于监控的系统本身的父节点是否存在，如不存在则建立，建立过程每个此系统的实例争抢创建，利用Zookeeper的原生节点创建锁完成
		String systemPath = rootPath + "/" + SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_NAME) + "-" +
									SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_CODE);

		String systemDesc = SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_NAME) + "-" + 
									SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_CODE);

		boolean isSystemRootExist = zk.exists(systemPath, true) != null ? true : false;
		if (!isSystemRootExist) {
			zk.create(systemPath, systemDesc.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		}
		//获取实例数量，并给出标号
		int instanceID = zk.getChildren(systemPath, true).size();
		SystemPropertyConfig.setContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_INSTANCE_KEY, 
			SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_NAME)
			+ "-i" + String.valueOf(++instanceID));

		// 3. 创建本次实例的临时节点，利用临时节点特性，完成系统监控
		Object[] tagArgs = { status };
		String rootDesc = SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_DESC);
		rootDesc = SystemPropertyConfig.fromatter(rootDesc, tagArgs);
		return zk.create(systemPath + "/" + systemDesc + "-", rootDesc.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
	}

	/**
	 * 获取并构造实例基础信息，并更监控服务器状态信息
	 * 
	 * @Methods Name buildAppInfo
	 * @Create In 2015年8月26日 By Jack
	 * @param zk
	 * @param appServerPath
	 * @return void
	 */
	public void buildAppInfo(ZooKeeper zk, String appServerPath) {
		ApplicationInfo ai;
		
		Stat stat = new Stat();
		SystemInfo si = new SystemInfo();
		String rootDesc;

		try {

			// 0.判断是否当天计数，如已不是当天，重置计数器，但每个实例目前存在一个5分钟的计数延迟待处理
			compareDate();
			javaInfor.rebuildJavaInfo(sc, true);
			
			// 1.获取目前节点基础信息
			rootDesc = new String(zk.getData(appServerPath, true, stat));
			Object[] tagArgs = { "Active" };
			rootDesc = SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_DESC);
			rootDesc = SystemPropertyConfig.fromatter(rootDesc, tagArgs);
			ai = (ApplicationInfo) JacksonMapperUtil.jsonToObject(rootDesc, ApplicationInfo.class);

			// 2.开始获取实例基本信息及机器基本信息
			// =======================通过java来获取相关系统状态============================
			si.setTotalMem(javaInfor.getMemoryInformations().getTotalMemory() / 1024 / 1024);
			si.setFreeMem(javaInfor.getMemoryInformations().getFreeMemory() / 1024 / 1024);
			si.setMaxMem(javaInfor.getMemoryInformations().getMaxMemory() / 1024 / 1024);
			
			// =======================OperatingSystemMXBean============================
			si.setOsName(javaInfor.getOS());
			si.setSysArch(javaInfor.getArc());
			si.setVersion(javaInfor.getSysVersion());
			si.setAvailableProcessors(javaInfor.getAvailableProcessors());
			
			si.setCommittedVirtualMemorySize(javaInfor.getMemoryInformations().getCommittedVirtualMemorySize());
			
			si.setProcessCpuTime(javaInfor.getProcessCpuTimeMillis());
			
			si.setFreeSwapSpaceSize(javaInfor.getMemoryInformations().getFreeSwapSpaceSize() / 1024 / 1024);
			si.setFreePhysicalMemorySize(javaInfor.getMemoryInformations().getFreePhysicalMemorySize() / 1024 / 1024);
			si.setTotalPhysicalMemorySize(javaInfor.getMemoryInformations().getTotalPhysicalMemorySize() / 1024 / 1024);
			
			si.setHostName(javaInfor.getHost());
			si.setIps(getAddress(this.port));
			
			si.setSystemCpuRatio(javaInfor.getSystemCpuLoad());
			si.setCpuRatio(javaInfor.getProcessCpuLoad());;

			// =======================MemoryMXBean============================
			si.setHeapMemoryUsage(javaInfor.getMemoryInformations().getHeapMemoryUsage());
			si.setNonHeapMemoryUsage(javaInfor.getMemoryInformations().getNonHeapMemoryUsage());
			
			// =======================ThreadMXBean============================
			si.setThreadCount(javaInfor.getThreadCount());
			si.setPeakThreadCount(javaInfor.getPeakThreadCount());
			
			si.setCurrentThreadCpuTime(javaInfor.getCurrentThreadCpuTime());
			si.setDaemonThreadCount(javaInfor.getDaemonThreadCount());
			si.setCurrentThreadUserTime(javaInfor.getCurrentThreadUserTime());

			// =======================CompilationMXBean============================
			// "
			si.setCompliationName(javaInfor.getCompliationName());
			si.setTotalCompliationTime(javaInfor.getTotalCompliationTime());
			si.setMemPoolInfos(javaInfor.getMemoryInformations().getMemPoolInfos());
			si.setGCInfos(javaInfor.getMemoryInformations().getGcInfos());

			// =======================RuntimeMXBean============================
			// "
			si.setClassPath(javaInfor.getClassPath());
			si.setLibraryPath(javaInfor.getLibraryPath());
			si.setVmName(javaInfor.getVmName());
			si.setVmVendor(javaInfor.getVmVendor());
			si.setVmVersion(javaInfor.getVmVersion());
			si.setVmArguments(javaInfor.getJvmArguments());
			
			// 3.构造实例信息
			ai.setSysInfo(si);
			ai.setUpdateTime(df.format(new Date()));
			
			ai.setPeerDealReqTime(SLACountManager.instance().getPeerDealRequestTime().longValue());
			ai.setSumInboundReqCounts(SLACountManager.instance().getSumInboundRequestCounts().longValue());
			ai.setSumOutboundReqCounts(SLACountManager.instance().getSumOutboundRequestCounts().longValue());
			ai.setSumDealReqCounts(SLACountManager.instance().getSumDealRequestCounts().longValue());
			ai.setSumDealReqTime(SLACountManager.instance().getSumDealRequestTime().longValue());
			ai.setSumErrDealReqCounts(SLACountManager.instance().getSumErrDealRequestCounts().longValue());
			ai.setSumErrDealReqTime(SLACountManager.instance().getSumErrDealRequestTime().longValue());

			// 4.更新服务器名称及版本
			String svrInfo[] = this.sc.getServerInfo().split(Constant.SYSTEM_SEETING_SERVER_DEFALUT_NAME_VERSION_SPLIT);
			ai.setServerName(svrInfo[0]);
			ai.setServerVersion(svrInfo.length > 1 ? svrInfo[1] : "unknown");
			
			ai.setTransactionCount(javaInfor.getTransactionCount());
			ai.setPid(javaInfor.getPID());
			ai.setDataBaseVersion(javaInfor.getDataBaseVersion());
			ai.setDataSourceDetails(javaInfor.getDataSourceDetails());
			
			ai.setStartupDate(df.format(javaInfor.getStartDate()));
			ai.setUnixMaxFileDescriptorCount(javaInfor.getUnixMaxFileDescriptorCount());
			ai.setUnixOpenFileDescriptorCount(javaInfor.getUnixOpenFileDescriptorCount());
			
			ai.setDesc(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_INSTANCE_KEY));
						
			// 5.转换系统信息到Json
			rootDesc = JacksonMapperUtil.objectToJson(ai);

			// 6.更新自身节点状态
			zk.setData(appServerPath, rootDesc.getBytes(), stat.getVersion());

		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		} catch (JsonMappingException e) {
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		} 

	}

	/**
	 * 构造SQL计数信息到队列
	 * @Methods Name buildSQLCountsInfo
	 * @Create In 2016年5月3日 By Jack
	 */
	public void buildSQLCountsInfo() {
		//构造 SQL 信息并发送
		try{
			String rootDesc;
			JdbcWrapper jw = JdbcWrapper.SINGLETON;
			if(jw != null){
				SQLInfo sqlInfo = SQLInfo.instance();
				sqlInfo.setSysCode(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_CODE));
				sqlInfo.setSysName(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_NAME));
				
				List<String> address = getAddress(this.port);
				if(address.size() >1){
					String ips = "";
					for(String item : address){
						ips += item + "</br>";
					}
					ips = ips.substring(0, ips.lastIndexOf("</"));
					sqlInfo.setSysIPS(ips);
				}else{
					sqlInfo.setSysIPS(address.get(0).toString());
				}
				
				sqlInfo.setDataBaseVersion(javaInfor.getDataBaseVersion());
				sqlInfo.setDataSourceDetails(javaInfor.getDataSourceDetails());
				sqlInfo.setActive_connection_count(jw.getActiveConnectionCount());
				sqlInfo.setActive_thread_count(jw.getActiveThreadCount());
				sqlInfo.setBuild_queue_length(jw.getBuildQueueLength());
				sqlInfo.setRunning_build_count(jw.getRunningBuildCount());
				sqlInfo.setTransaction_count(jw.getTransactionCount());
				sqlInfo.setUsed_connection_count(jw.getUsedConnectionCount());
				sqlInfo.setUpdateDate(df.format(new Date()));
				
				List<CounterRequest> sqlDetails = jw.getSqlCounter().getRequests();
				sqlInfo.setSqlDetails(sqlDetails);
				rootDesc = JacksonMapperUtil.objectToJson(sqlInfo);
				//发送消息到回收队列
				KafkaConnectManager.sendMsgToTopic(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_KAFKA_RECEIVER), 
					sqlInfo.getSysCode() + sqlInfo.getSysName(), rootDesc);
			}
		}catch(IOException e){
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		}
	}
	

	public void buildRequestCountInfo(){
		try{
			String rootDesc;
			RequestWrapper rw = RequestWrapper.SINGLETON;
			
			if (rw != null) {
				RequestInfo reqInfo = RequestInfo.instance();
				reqInfo.setSysCode(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_CODE));
				reqInfo.setSysName(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_CONTEXT_NAME));
				
				List<String> address = getAddress(this.port);
				if(address.size() >1){
					String ips = "";
					for(String item : address ){
						ips += item + "</br>";
					}
					ips = ips.substring(0, ips.lastIndexOf("</"));
					reqInfo.setSysIPS(ips);
				}else{
					reqInfo.setSysIPS(address.get(0).toString());
				}
				
				reqInfo.setUpdateDate(df.format(new Date()));
	
				List<CounterRequest> reqDetails = rw.getHttpCounter().getRequests();
				List<CounterRequest> errDetails = rw.getErrorCounter().getRequests();
				reqInfo.setRequestDetails(reqDetails);
				reqInfo.setErrorDetails(errDetails);
				rootDesc = JacksonMapperUtil.objectToJson(reqInfo);
				// 发送消息到回收队列
				KafkaConnectManager.sendMsgToTopic(SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_KAFKA_RECEIVER), reqInfo.getSysCode() + reqInfo.getSysName(), rootDesc);
	
			}
		}catch(IOException e){
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001013"));
			log.error("Details: " + e.getMessage());
		}
	}
	
	/**
	 * @Methods Name compareDate
	 * @Create In 2015年9月6日 By Jack void
	 */
	private void compareDate() {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
		try {
			Date cdS = new Date(System.currentTimeMillis());
			Date pdD = SLACountManager.instance().getPeerDate();

			Date currentDate = df.parse(df.format(cdS));
			Date startupDate = df.parse(df.format(pdD));

			if (currentDate.compareTo(startupDate) > 0) {
				SLACountManager.init();
			}
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001015"));
			log.error("Details: " + e.getMessage());
		}
	}

	/**
	 * 获取本机所有IP地址并附加本次实例启动的端口号
	 * 
	 * @Methods Name getIPsList
	 * @Create In 2015年8月26日 By Jack
	 * @param Integer
	 *            port 实例启动后监听的端口号
	 * @return List<String>
	 */
	private List<String> getIPsList(Integer port) {
		List<String> res = new ArrayList<String>();
		try {
			String enthernet = SystemPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFALUT_ETHERNET);
			// NetworkInterface.getByName("en0") == null ?
			// NetworkInterface.getByName("eth0") :
			// NetworkInterface.getByName("en0");
			NetworkInterface netInterfaces = enthernet != null ? NetworkInterface.getByName(enthernet) : NetworkInterface.getByName("en0");
			if (netInterfaces != null) {
				InetAddress ip = null;
				Enumeration nii = netInterfaces.getInetAddresses();
				if(nii.hasMoreElements()){
					while (nii.hasMoreElements()) {
						ip = (InetAddress) nii.nextElement();
						if (ip.getHostAddress().indexOf(":") == -1) {
							res.add(ip.getHostAddress() + ":" + String.valueOf(port.intValue()));
						}
					}
				}else{
					res.add(Constant.SYSTEM_SEETING_MONITOR_SETTING_DEFAULT_IP + ":" + String.valueOf(port.intValue()));
				}
			} else {
				log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001014"));
				res.add(Constant.SYSTEM_SEETING_MONITOR_SETTING_DEFAULT_IP + ":" + String.valueOf(port.intValue()));
			}
		} catch (SocketException e) {
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001014"));
			log.error("Details: " + e.getMessage());
			res.add(Constant.SYSTEM_SEETING_MONITOR_SETTING_DEFAULT_IP + ":" + String.valueOf(port.intValue()));
		}
		return res;
	}
	

	/**
	 * 获取本机所有IP地址并附加本次实例启动的端口号
	 * @Methods Name getAddress
	 * @Create In 2016年4月13日 By Jack
	 * @param port 实例启动后监听的端口号
	 * @return List<String> IP:Port列表
	 */
	private List<String> getAddress(Integer port) {
		List<String> res = new ArrayList<String>();
		try {
			for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
				NetworkInterface networkInterface = interfaces.nextElement();
				if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
					continue;
				}
				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
				if (addresses.hasMoreElements()) {
					InetAddress ip;
					while (addresses.hasMoreElements()) {
						ip = (InetAddress) addresses.nextElement();
						if (!ip.isSiteLocalAddress()) {
							continue;
						}
						if (ip.getHostAddress().indexOf(":") == -1) {
							res.add(ip.getHostAddress() + ":" + String.valueOf(port.intValue()));
						}
					}
				}
			}
		} catch (SocketException e) {
			log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001014"));
			log.error("Details: " + e.getMessage());
			res.add(Constant.SYSTEM_SEETING_MONITOR_SETTING_DEFAULT_IP + ":" + String.valueOf(port.intValue()));
		}
		if(res.isEmpty()){
			res.add(Constant.SYSTEM_SEETING_MONITOR_SETTING_DEFAULT_IP + ":" + String.valueOf(port.intValue()));
		}
		
		return res;
	}
}
