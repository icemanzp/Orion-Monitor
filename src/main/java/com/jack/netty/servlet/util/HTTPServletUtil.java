/**
 * @Probject Name: WFJ-Base-Server-Dev
 * @Path: com.wfj.netty.utilHTTPServletUtil.java
 * @Create By Jack
 * @Create In 2016年7月26日 下午3:50:26
 * TODO
 */
package com.jack.netty.servlet.util;

import com.jack.netty.servlet.conf.EnvPropertyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jack.netty.servlet.conf.Constant;

/**
 * @Class Name HTTPServletUtil
 * @Author Jack
 * @Create In 2016年7月26日
 */
public class HTTPServletUtil {

	private static Logger log = LoggerFactory.getLogger(HTTPServletUtil.class);
	protected static final String URI_ENCODING = EnvPropertyConfig.getContextProperty(Constant.SYSTEM_SEETING_SERVER_DEFAULT_URI_ENCODING);
	
	
	public static final class Names{
		public static final String X_WFJ_POWERED_BY = "X-Powered-By";
		
		public static final String X_WFJ_CUSTOMER_RESPONSE_HEADER_MONITOR_BEGINTIME = "X-WFJ-Customer-Response-Header-Monitor-Begintime";
		
		public static final String X_WFJ_CUSTOMER_RESPONSE_HEADER_MONITOR_ERROR_BEGINTIME = "X-WFJ-Customer-Response-Header-Monitor-Error-Begintime";
		
		public static final String X_WFJ_CUSTOMER_RESPONSE_HEADER_MONITOR_BEGINCUPTIME = "X-WFJ-Customer-Response-Header-Monitor-BeginCupTime";
	}
}
