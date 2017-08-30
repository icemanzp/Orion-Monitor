/*
 * Copyright 2008-2016 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jack.netty.servlet.handler.adapter;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jack.netty.servlet.conf.Parameters;
import com.jack.netty.servlet.filter.Monitor;
import com.jack.netty.servlet.handler.wrapper.JdbcWrapper;


/**
 * Driver jdbc "proxy" 进行监测。
 *这是如果使用JDBC驱动，而不是一个数据源要报告的类驱动程序的。
 *在一份声明中JDBC属性“驱动程序”必须包含类的真正驱动程序的名称。
 *像JDBC URL，用户名或密码等性质，可申报。
 * @author Emeric Vernat
 */
public class JdbcDriver implements Driver {
	
	private static Logger logger = LoggerFactory.getLogger(Monitor.class);
	
	// cette classe est publique pour être déclarée dans une configuration jdbc
	static final JdbcDriver SINGLETON = new JdbcDriver();

	// initialisation statique du driver
	static {
		try {
			DriverManager.registerDriver(SINGLETON);
			logger.debug("JDBC driver registered");

			// on désinstalle et on réinstalle les autres drivers pour que le notre soit en premier
			// (notamment, si le jar du driver contient un fichier java.sql.Driver dans META-INF/services
			// pour initialiser automatiquement le driver contenu dans le jar)
			for (final Driver driver : Collections.list(DriverManager.getDrivers())) {
				if (driver != SINGLETON) {
					DriverManager.deregisterDriver(driver);
					DriverManager.registerDriver(driver);
				}
			}
		} catch (final SQLException e) {
			// ne peut arriver
			throw new IllegalStateException(e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		final String proxiedDriver = info.getProperty("driver");
		if (proxiedDriver == null) {
			// si pas de propriété driver alors ce n'est pas pour nous
			// (on passe ici lors du DriverManager.getConnection ci-dessous)
			return null;
		}
		try {
			// on utilise Thread.currentThread().getContextClassLoader() car le driver peut ne pas être
			// dans le même classLoader que les classes de javamelody
			Class.forName(proxiedDriver, true, Thread.currentThread().getContextClassLoader());
			// et non Class.forName(proxiedDriver);
		} catch (final ClassNotFoundException e) {
			// Rq: le constructeur de SQLException avec message et cause n'existe qu'en jdk 1.6
			throw new SQLException(e.getMessage(), e);
		}
		final Properties tmp = (Properties) info.clone();
		tmp.remove("driver");
		Parameters.initJdbcDriverParameters(url, tmp);
		return JdbcWrapper.SINGLETON.createConnectionProxy(DriverManager.getConnection(url, tmp));
	}

	/** {@inheritDoc} */
	@Override
	public boolean acceptsURL(String url) throws SQLException {
		// test sur dbcp nécessaire pour le cas où le monitoring est utilisé avec le web.xml global
		// et le répertoire lib global de tomcat et également pour les anomalies 1&2 (sonar, grails)
		// (rq: Thread.currentThread().getStackTrace() a été mesuré à environ 3 micro-secondes)
		for (final StackTraceElement element : Thread.currentThread().getStackTrace()) {
			if (element.getClassName().endsWith("dbcp.BasicDataSource")) {
				return false;
			}
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public int getMajorVersion() {
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public int getMinorVersion() {
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return new DriverPropertyInfo[0];
	}

	/** {@inheritDoc} */
	@Override
	public boolean jdbcCompliant() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[lastConnectUrl=" + Parameters.getLastConnectUrl()
				+ ", lastConnectInfo=" + Parameters.getLastConnectInfo() + ']';
	}

	/**
	 * Définition de la méthode getParentLogger ajoutée dans l'interface Driver en jdk 1.7.
	 * @return Logger
	 * @throws SQLFeatureNotSupportedException e
	 */
	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return java.util.logging.Logger.getLogger(Logger.ROOT_LOGGER_NAME) ;
	}
}
