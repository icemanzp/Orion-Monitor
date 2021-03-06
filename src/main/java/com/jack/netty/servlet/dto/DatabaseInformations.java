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
package com.jack.netty.servlet.dto;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.naming.NamingException;
import javax.sql.DataSource;

import com.jack.netty.servlet.conf.Parameter;
import com.jack.netty.servlet.conf.Parameters;
import com.jack.netty.servlet.handler.wrapper.JdbcWrapper;
import com.jack.netty.servlet.util.I18N;

/**
 * 数据库的资料。
 * 
 * @author Emeric Vernat
 */
public class DatabaseInformations implements Serializable {
	private static final long serialVersionUID = -6105478981257689782L;

	enum Database {
		// base 与返回的名字已知数据
		// connection.getMetaData().getDatabaseProductName()
		// (灵感 Hibernate)
		POSTGRESQL("PostgreSQL"), MYSQL("MySQL"), MYSQL4("MySQL"), ORACLE("Oracle"), DB2("DB2 UDB for AS/400", "DB2/"), H2("H2"), HSQLDB("HSQL Database Engine"), SQLSERVER("Microsoft SQL Server"), SYBASE(
			"Sybase SQL Server", "Adaptive Server Enterprise"), INFORMIX("Informix Dynamic Server");

		private static final String RESOURCE_BUNDLE_BASE_NAME = Parameters.getResourcePath("databaseInformations").replace('/', '.').substring(1);

		private List<String> databaseNames;

		Database(String... databaseNames) {
			this.databaseNames = Arrays.asList(databaseNames);
		}

		List<String> getRequestNames() {
			final List<String> tmp;
			switch (this) {
			case POSTGRESQL:
				tmp = Arrays.asList("pg_stat_activity", "pg_locks", "pg_database", "pg_tablespace", "pg_stat_database", "pg_stat_user_tables", "pg_stat_user_indexes", "pg_statio_user_tables",
					"pg_statio_user_indexes", "pg_statio_user_sequences", "pg_settings");
				break;
			case MYSQL:
				tmp = Arrays.asList("processlist", "databases", "variables", "global_status", "innodb_status");
				break;
			case MYSQL4:
				// 的查询的名称是相同的，但在SQL
				//匹配“innodb_status”
				//是不是MySQL的和MySQL5+4之间相同的（问题195）
				tmp = Arrays.asList("processlist", "databases", "variables", "global_status", "innodb_status");
				break;
			case ORACLE:
				tmp = Arrays.asList("sessions", "locks", "sqlTimes", "foreignKeysWithoutIndexes", "invalidObjects", "disabledConstraints", "instance", "database", "nlsParameters",
					"tablespaceFreespace", "datafileIo", "tablespaceExtents", "ratios", "parameters", "rollbackSegmentStatistics", "statistics", "events");
				break;
			case DB2:
				tmp = Arrays.asList("mon_current_sql", "mon_db_summary", "mon_lockwaits", "mon_service_subclass_summary", "mon_current_uow", "mon_workload_summary", "mon_get_connection",
					"current_queries");
				break;
			case H2:
				tmp = Arrays.asList("memory", "sessions", "locks", "settings");
				break;
			case HSQLDB:
				tmp = Arrays.asList("system_sessions", "system_cacheinfo", "system_properties", "system_schemas");
				break;
			case SQLSERVER:
				tmp = Arrays.asList("version", "connections");
				break;
			case SYBASE:
				tmp = Arrays.asList("sp_who", "connections", "sp_lock", "lock", "running_stored_procedure", "used_temporary_tables", "used_tables", "sp_version");
				break;
			case INFORMIX:
				tmp = Arrays.asList("version", "sessions", "resources_by_user", "current_queries", "config");
				break;
			default:
				throw new IllegalStateException();
			}
			return addPrefix(tmp);
		}

		private List<String> addPrefix(List<String> requests) {
			final List<String> list = new ArrayList<String>(requests.size());
			final String prefix = this.toString().toLowerCase(Locale.ENGLISH) + '.';
			for (final String requestName : requests) {
				list.add(prefix + requestName);
			}
			return list;
		}

		public String getUrlIdentifier() {
			if (this == MYSQL4) {
				return MYSQL.toString().toLowerCase(Locale.ENGLISH);
			}
			return this.toString().toLowerCase(Locale.ENGLISH);
		}

		public String getRequestByName(String requestName) {
			return ResourceBundle.getBundle(RESOURCE_BUNDLE_BASE_NAME).getString(requestName);
		}

		public List<String> getDatabaseNames() {
			return databaseNames;
		}

		private boolean isRecognized(String databaseName, String url) {
			for (final String name : getDatabaseNames()) {
				if (databaseName.startsWith(name)) {
					return true;
				}
			}
			return url != null && url.contains(getUrlIdentifier());
		}

		public static Database getDatabaseForConnection(Connection connection) throws SQLException {
			final DatabaseMetaData metaData = connection.getMetaData();
			final String databaseName = metaData.getDatabaseProductName();
			final String url = metaData.getURL();
			for (final Database database : Database.values()) {
				if (database.isRecognized(databaseName, url)) {
					if (database == MYSQL && metaData.getDatabaseMajorVersion() <= 4) {
						// si mysql et version 4 alors c'est MYSQL4 et non MYSQL
						return MYSQL4;
					}
					return database;
				}
			}
			throw new IllegalArgumentException(I18N.getFormattedString("type_base_de_donnees_inconnu", databaseName));
		}
	}

	private final Database database;
	@SuppressWarnings("all")
	private final List<String> requestNames;
	private final int selectedRequestIndex;
	private final String[][] result;

	public DatabaseInformations(int selectedRequestIndex) throws SQLException, NamingException {
		super();
		this.selectedRequestIndex = selectedRequestIndex;
		final Connection connection = getConnection();
		assert connection != null;
		try {
			database = Database.getDatabaseForConnection(connection);
			requestNames = database.getRequestNames();
			final String request = database.getRequestByName(requestNames.get(selectedRequestIndex));
			result = executeRequest(connection, request, null);
		} finally {
			connection.close();
		}
	}

	public static int parseRequestIndex(String requestIndex) {
		if (requestIndex != null) {
			return Integer.parseInt(requestIndex);
		}
		return 0;
	}

	public int getNbColumns() {
		final String selectedRequestName = getSelectedRequestName();
		if ("oracle.statistics".equals(selectedRequestName)) {
			return 2;
		} else if ("oracle.events".equals(selectedRequestName)) {
			return 2;
		} else if ("mysql.variables".equals(selectedRequestName)) {
			return 2;
		} else if ("mysql.global_status".equals(selectedRequestName)) {
			return 4;
		} else if ("h2.settings".equals(selectedRequestName)) {
			return 2;
		}
		return 1;
	}

	public int getSelectedRequestIndex() {
		return selectedRequestIndex;
	}

	public String getSelectedRequestName() {
		return requestNames.get(getSelectedRequestIndex());
	}

	public String[][] getResult() {
		return result; // NOPMD
	}

	public List<String> getRequestNames() {
		return requestNames;
	}

	private static String[][] executeRequest(Connection connection, String request, List<?> parametersValues) throws SQLException {
		final PreparedStatement statement = connection.prepareStatement(request);
		try {
			if (parametersValues != null) {
				int i = 1;
				for (final Object parameterValue : parametersValues) {
					statement.setObject(i, parameterValue);
					i++;
				}
			}
			return executeQuery(statement);
		} catch (final SQLException e) {
			if (e.getErrorCode() == 942 && e.getMessage() != null && e.getMessage().startsWith("ORA-")) {
				final String userName = connection.getMetaData().getUserName();
				final String message = I18N.getFormattedString("oracle.grantSelectAnyDictionnary", userName);
				throw new SQLException(message, e);
			}
			throw e;
		} finally {
			statement.close();
		}
	}

	private static String[][] executeQuery(PreparedStatement statement) throws SQLException {
		final ResultSet resultSet = statement.executeQuery();
		try {
			final ResultSetMetaData metaData = resultSet.getMetaData();
			final int columnCount = metaData.getColumnCount();
			final List<String[]> list = new ArrayList<String[]>();
			String[] values = new String[columnCount];
			for (int i = 1; i <= columnCount; i++) {
				values[i - 1] = metaData.getColumnName(i) + '\n' + metaData.getColumnTypeName(i) + '(' + metaData.getColumnDisplaySize(i) + ')';
			}
			list.add(values);

			while (resultSet.next()) {
				values = new String[columnCount];
				for (int i = 1; i <= columnCount; i++) {
					values[i - 1] = resultSet.getString(i);
				}
				list.add(values);
			}
			return list.toArray(new String[list.size()][]);
		} finally {
			resultSet.close();
		}
	}

	private static Connection getConnection() throws SQLException, NamingException {
		// on commence par voir si le driver jdbc a été utilisé
		// car s'il n'y a pas de datasource une exception est déclenchée
		if (Parameters.getLastConnectUrl() != null) {
			final Connection connection = DriverManager.getConnection(Parameters.getLastConnectUrl(), Parameters.getLastConnectInfo());
			connection.setAutoCommit(false);
			return connection;
		}

		// on cherche une datasource avec InitialContext
		// (le nom de la dataSource recherchée dans JNDI est du genre jdbc/Xxx
		// qui est le nom standard d'une DataSource)
		final Collection<DataSource> dataSources = JdbcWrapper.getJndiAndSpringDataSources().values();
		for (final DataSource dataSource : dataSources) {
			try {
				final Connection connection = dataSource.getConnection();
				// on ne doit pas changer autoCommit pour la connection d'une
				// DataSource
				// (ou alors il faudrait remettre l'autoCommit après, issue 189)
				// connection.setAutoCommit(false);
				return connection;
			} catch (final Exception e) {
				// si cette dataSource ne fonctionne pas, on suppose que la
				// bonne dataSource est une des suivantes
				// (par exemple, sur GlassFish il y a des dataSources par défaut
				// qui ne fonctionne pas forcément)
				continue;
			}
		}
		if (!dataSources.isEmpty()) {
			// this will probably throw an exception like above
			return dataSources.iterator().next().getConnection();
		}
		return null;
	}

	public static String explainPlanFor(String sqlRequest) throws SQLException, NamingException {
		final Connection connection = getConnection();
		if (connection != null) {
			try {
				final Database database = Database.getDatabaseForConnection(connection);
				if (database == Database.ORACLE) {
					// 如果甲骨文，要求执行计划用表
					//默认PLAN_TABLE
					//使用
					//“解释平面设置statement_id = <statement_id>的......”
					//（MySQL或PostgreSQL，如果你可以“解释......”
					//除bindés参数将不被接受
					//查询和更新/插入/删除其一）。
					//（如DB2，语法是“解释平面...”）

					//如果MySQL只是阅读的ResultSet
					//的executeQuery（“解释...”）
					//这可以从显示在表
					//的String [] []
					//甲骨文，但你必须去读取表PLAN_TABLE
					// (http://www.java2s.com/Open-Source/Java-Document/Database-Client/squirrel-sql-2.6.5a/net/sourceforge/squirrel_sql/plugins/oracle/explainplan/ExplainPlanExecuter.java.htm)
					//哈希码是足够唯一关键，因为那里
					//位执行计划
					//同时显示，和在任何情况下
					// CounterRequest.getId（）过长
					//默认为Oracle表
					//（$ SYS.PLAN_TABLE .STATEMENT_ID具有30的长度）
					final String statementId = String.valueOf(sqlRequest.hashCode());
					final String explainRequest = buildExplainRequest(sqlRequest, statementId);
					// exécution de la demande
					final Statement statement = connection.createStatement();
					try {
						statement.execute(explainRequest);
					} finally {
						statement.close();
					}

					// récupération du résultat
					return getPlanOutput(connection, statementId);
				}
			} finally {
				if (!connection.getAutoCommit()) {
					connection.rollback();
				}
				connection.close();
			}
		}
		return null;
	}

	private static String buildExplainRequest(String sqlRequest, String statementId) {
		// rq : il semble qu'une requête explain plan ne puisse avoir la requête
		// en paramètre bindé
		// (donc les requêtes "explain ..." seront ignorées dans JdbcWrapper)
		int i = 1;
		String request = sqlRequest;
		if (Parameters.getParameter(Parameter.SQL_TRANSFORM_PATTERN) != null) {
			// si les requêtes SQL peuvent avoir été transformées par
			// SQL_TRANSFORM_PATTERN,
			// alors on remplace le '$' par '?' en espérant avec un plan
			// d'exécution même simplifié
			// (sinon, il serait impossible d'avoir un plan d'exécution pour
			// certaines requêtes SQL
			// transformées par SQL_TRANSFORM_PATTERN)
			request = request.replace(Counter.TRANSFORM_REPLACEMENT_CHAR, '?');
		}
		// utilisation de la table PLAN_TABLE par défaut
		// (il faut que cette table soit créée auparavant dans oracle
		// et elle peut être créée par : @$ORACLE_HOME/rdbms/admin/catplan.sql
		// ou par @$ORACLE_HOME/rdbms/admin/utlxplan.sql si oracle 9g ou avant)
		String explainRequest = "explain plan set statement_id = '" + statementId + "' for " + request;

		// dans le cas où la requête contient ';' (requêtes multiples), je ne
		// sais pas si explain
		// plan considère que cela fait partie de la requête à analyser où si
		// certaines versions
		// d'oracle considèrent que cela vient après l'explain plan; par
		// sécurité on interdit cela
		if (explainRequest.indexOf(';') != -1) {
			explainRequest = explainRequest.substring(0, explainRequest.indexOf(';'));
		}

		// on remplace les paramètres bindés "?" par ":n"
		int index = explainRequest.indexOf('?');
		while (index != -1) {
			explainRequest = explainRequest.substring(0, index) + ':' + i + explainRequest.substring(index + 1);
			i++;
			index = explainRequest.indexOf('?');
		}
		return explainRequest;
	}

	private static String getPlanOutput(Connection connection, String statementId) throws SQLException {
		// table PLAN_TABLE par défaut et format par défaut
		final String planTableRequest = "select * from table(dbms_xplan.display(null,?, null))";
		final String[][] planTableOutput = executeRequest(connection, planTableRequest, Collections.singletonList(statementId));
		final StringBuilder sb = new StringBuilder();
		for (final String[] row : planTableOutput) {
			for (final String value : row) {
				sb.append(value);
			}
			sb.append('\n');
		}
		if (sb.indexOf("-") != -1) {
			sb.delete(0, sb.indexOf("-"));
		}
		return sb.toString();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[database=" + database + ']';
	}
}
