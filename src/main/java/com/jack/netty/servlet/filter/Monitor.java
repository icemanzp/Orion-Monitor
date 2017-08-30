/**
 * @Probject Name: servlet-monitor
 * @Path: com.wfj.netty.servlet.filterMonitor.java
 * @Create By Jack
 * @Create In 2016年2月15日 下午2:52:09
 * TODO
 */
package com.wfj.netty.servlet.filter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wfj.netty.servlet.conf.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wfj.netty.servlet.MonitorChecker;
import com.wfj.netty.servlet.conf.EnvPropertyConfig;
import com.wfj.netty.servlet.conf.Parameters;
import com.wfj.netty.servlet.conf.SystemPropertyConfig;
import com.wfj.netty.servlet.dto.JavaInformations;
import com.wfj.netty.servlet.dto.ThreadInformations;
import com.wfj.netty.servlet.handler.factory.SLACountManager;
import com.wfj.netty.servlet.handler.wrapper.JdbcWrapper;
import com.wfj.netty.servlet.handler.wrapper.RequestWrapper;
import com.wfj.netty.servlet.infc.Health;

/**
 * @Class Name Monitor
 * @Author Jack
 * @Create In 2016年2月15日
 */
public class Monitor implements Filter {

	private Logger logger = LoggerFactory.getLogger(Monitor.class);
	
	private FilterConfig config;
	private Health hl;
	
	private boolean isDebug;
	private boolean servletApi2;

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#destroy()
	 */
	public void destroy() {
		// TODO Auto-generated method stub
		hl.shutdownHealth("Disable");
		hl = null;
		config = null;
		isDebug = true;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 * javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest req, ServletResponse rep, FilterChain fc) throws IOException, ServletException {
		// TODO Auto-generated method stub
		// 0. 初始化资源信息
		final HttpServletResponse resp = (HttpServletResponse) rep;
		final HttpServletRequest rest = (HttpServletRequest) req;
					
		// 1. 记录处理起始时间
		long beginTime = System.currentTimeMillis();
		long beginCupTime = ThreadInformations.getCurrentThreadCpuTime();
		
		try{
			// 2. 判断是否调试模式，如果是则直接处理返回不做统计
			if (!(req instanceof HttpServletRequest) || !(rep instanceof HttpServletResponse) || isDebug) {
				//调试模式直接转发不记录
				fc.doFilter(req, rep);
				return;
			} else {
				//非调试模式进入记录入栈数量
				SLACountManager.instance().getSumInboundRequestCounts().incrementAndGet();
				JdbcWrapper.ACTIVE_THREAD_COUNT.incrementAndGet();
				// 3. 进入监控处理，包括含有 SQL 的请求
				// 完成数据转发
				fc.doFilter(rest, resp);
	
				// 如果使用的是Servlet2及以下版本，则刷新相应返回，以使得数据可以被写入
				if (servletApi2 || !rest.isAsyncStarted()) {
					resp.flushBuffer();
				}
	
				// 4. 判断返回状态记录处理数量及时间
				// 只有返回小于400或者等于401的返回码才被认为是访问成功的请求，否则计入失败
				if (resp.getStatus() < HttpServletResponse.SC_BAD_REQUEST || resp.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
	
					// 只有处理成功，才能处理请求总数计数器+1；如果出现异常则不计数处理
					SLACountManager.instance().getSumDealRequestCounts().incrementAndGet();
					// 记录结束时间
					long endTime = System.currentTimeMillis();
					// 记录每个请求的实时处理时间
					long dealTime = endTime - beginTime;
					SLACountManager.instance().setPeerDealRequestTime(new AtomicLong(dealTime));
					// 记录总的请求处理时间
					long sumDealTime = SLACountManager.instance().getSumDealRequestTime().get() + dealTime;
					SLACountManager.instance().setSumDealRequestTime(new AtomicLong(sumDealTime));
					//会重复
					//RequestWrapper.SINGLETON.doError(resp.getStatus(), beginCupTime, beginTime);
				} else {
					// 记录结束时间
					long endTime = System.currentTimeMillis();
					// 记录每个异常请求的实时处理时间
					long dealTime = endTime - beginTime;
					long sumDealTime = SLACountManager.instance().getSumErrDealRequestTime().get() + dealTime;
	
					SLACountManager.instance().getSumErrDealRequestCounts().incrementAndGet();
					SLACountManager.instance().setSumErrDealRequestTime(new AtomicLong(sumDealTime));
				}
				// 记录出栈数量
				SLACountManager.instance().getSumOutboundRequestCounts().incrementAndGet();
				
				RequestWrapper.SINGLETON.doExecute((HttpServletRequest)req, (HttpServletResponse)rep, beginCupTime, beginTime);
			}
		}catch(Exception e){
			throwException(e, beginCupTime, beginTime);
		}finally{
			//5. 活跃线程数-1
			JdbcWrapper.ACTIVE_THREAD_COUNT.decrementAndGet();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig conf) throws ServletException {
		// TODO Auto-generated method stub
		// 1. 初始化资源
		this.config = conf;
		EnvPropertyConfig.init();
		SystemPropertyConfig.init();
		

		// 2.如果是调试模式则不启动
		isDebug = Boolean.valueOf(SystemPropertyConfig.getContextProperty("system.seeting.monitor.isDebug", "true"));
		if (isDebug)
			return;

		//以下为监控所需各个初始化项目
		//参数文件
		Parameters.getContextPath(config.getServletContext());
		Parameters.initialize(config);
		//数据库监控
		JdbcWrapper.SINGLETON.initServletContext(Parameters.getServletContext());
		if (!Parameters.isNoDatabase()) {
			JdbcWrapper.SINGLETON.rebindDataSources();
		} else {
			// si le paramètre no-database a été mis dans web.xml, des datasources jndi ont pu
			// être rebindées auparavant par SessionListener, donc on annule ce rebinding
			JdbcWrapper.SINGLETON.stop();
		}
		//系统信息
		boolean webXmlExists = false;
		boolean pomXmlExists = false;
		try {
			final InputStream webXmlAsStream = getWebXmlAsStream();
			if (webXmlAsStream != null) {
				webXmlAsStream.close();
				webXmlExists = true;
			}
			final InputStream pomXmlAsStream = getPomXmlAsStream();
			if (pomXmlAsStream != null) {
				pomXmlAsStream.close();
				pomXmlExists = true;
			}
		} catch (final IOException e) {
			logger.warn(e.toString(), e);
		}
		JavaInformations.setWebXmlExistsAndPomXmlExists(webXmlExists, pomXmlExists);
		JavaInformations javaInformations = JavaInformations.instance(conf.getServletContext(), true);
		this.servletApi2 = config.getServletContext().getMajorVersion() < 3;
		//请求信息
		RequestWrapper.SINGLETON.initServletContext(conf.getServletContext());
		
		// 3. 初始化应用端口号及实例号
        int port;
        if(javaInformations.getTomcatInformationsList().isEmpty()){
            port = Integer.valueOf(System.getProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_SERVER_PORT, Constant.SYSTEM_SEETING_SERVER_DEFAULT_SERVER_PORT_VALUE));
        }else {
            port = Integer.valueOf(javaInformations.getTomcatInformationsList().get(0).getHttpPort());
        }

		// . 初始化监控程序
		hl = new MonitorChecker(port, conf.getServletContext());
		SLACountManager.init();

		// 4. 启动监控
		hl.startHealth("Active");
	}

	private void throwException(Throwable t, long beginCupTime, long beginTime) throws IOException, ServletException {

		// 记录结束时间
		long endTime = System.currentTimeMillis();
		// 记录每个异常请求的实时处理时间
		long dealTime = endTime - beginTime;
		long sumDealTime = SLACountManager.instance().getSumErrDealRequestTime().get() + dealTime;

		SLACountManager.instance().getSumErrDealRequestCounts().incrementAndGet();
		SLACountManager.instance().setSumErrDealRequestTime(new AtomicLong(sumDealTime));

		RequestWrapper.SINGLETON.doError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, beginCupTime, beginTime);
		if (t instanceof Error) {
			throw (Error) t;
		} else if (t instanceof RuntimeException) {
			throw (RuntimeException) t;
		} else if (t instanceof IOException) {
			throw (IOException) t;
		} else if (t instanceof ServletException) {
			throw (ServletException) t;
		} else {
			throw new ServletException(t.getMessage(), t);
		}
	}
	
	private static InputStream getWebXmlAsStream() {
		final InputStream webXml = Parameters.getServletContext()
				.getResourceAsStream("/WEB-INF/web.xml");
		if (webXml == null) {
			return null;
		}
		return new BufferedInputStream(webXml);
	}

	private static InputStream getPomXmlAsStream() {
		final Set<?> mavenDir = Parameters.getServletContext().getResourcePaths("/META-INF/maven/");
		if (mavenDir == null || mavenDir.isEmpty()) {
			return null;
		}
		final Set<?> groupDir = Parameters.getServletContext()
				.getResourcePaths((String) mavenDir.iterator().next());
		if (groupDir == null || groupDir.isEmpty()) {
			return null;
		}
		final InputStream pomXml = Parameters.getServletContext()
				.getResourceAsStream(groupDir.iterator().next() + "pom.xml");
		if (pomXml == null) {
			return null;
		}
		return new BufferedInputStream(pomXml);
	}
}
