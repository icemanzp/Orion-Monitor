/**
 * @Probject Name: servlet-monitor-pud
 * @Path: com.wfj.netty.servlet.utilTomcatInfoUtil.java
 * @Create By Jack
 * @Create In 2016年3月25日 上午9:54:30
 * TODO
 */
package com.jack.netty.servlet.util;

import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jack.netty.servlet.conf.EnvPropertyConfig;

/**
 * @Class Name TomcatInfoUtil
 * @Author Jack
 * @Create In 2016年3月25日
 */
public class TomcatInfoUtil {
	private static Logger log = LoggerFactory.getLogger(TomcatInfoUtil.class);
	
	public static String getHttpPort(String defaultPort) {
		String portString = defaultPort;
        try {
        		
            MBeanServer server = null;
            if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
                server = MBeanServerFactory.findMBeanServer(null).get(0);
            }

            Set names = server.queryNames(new ObjectName("Catalina:type=Connector,*"), null);
            Iterator iterator = names.iterator();
            
            ObjectName name = null;
            while (iterator.hasNext()) {
                name = (ObjectName) iterator.next();

                String protocol = server.getAttribute(name, "protocol").toString();
				String scheme = server.getAttribute(name,"scheme").toString();
				
				if (protocol.toLowerCase().contains("http") && scheme.toLowerCase().contains("http")) {
                		portString = server.getAttribute(name, "port").toString();
                		break;
                }
//                String scheme = server.getAttribute(name, "scheme").toString();
//                String port = server.getAttribute(name, "port").toString();
//                System.out.println(protocol + " : " + scheme + " : " + port);
            }
        } catch (Exception e) {
        		log.error(EnvPropertyConfig.getContextProperty("env.setting.server.error.00001018"));
			log.error("Details: " + e.getMessage());
        }
        return portString;
    }
}
