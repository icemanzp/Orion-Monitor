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
package com.jack.netty.servlet.handler.spring;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Set;

import javax.sql.DataSource;

import com.jack.netty.servlet.conf.Parameters;
import com.jack.netty.servlet.handler.wrapper.JdbcWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.jndi.JndiObjectFactoryBean;

/**
 * Post-processor Spring pour une éventuelle DataSource défini dans le fichier xml Spring.
 * @author Emeric Vernat
 */
public class SpringDataSourceBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {
	
	private Logger logger = LoggerFactory.getLogger(SpringDataSourceBeanPostProcessor.class);
	
	private Set<String> excludedDatasources;
	// l'interface PriorityOrdered place la priorité assez haute dans le contexte Spring
	// quelle que soit la valeur de order
	private int order = LOWEST_PRECEDENCE;

	/**
	 * 定义被排除数据源的名字。
	 * 		 exemple:
			<bean id="springDataSourceBeanPostProcessor" class="net.bull.javamelody.SpringDataSourceBeanPostProcessor">
				<property name="excludedDatasources">
					<set>
						<value>excludedDataSourceName</value>
					</set>
				</property>
		 	</bean>
	 * @param excludedDatasources Set
	 */
	public void setExcludedDatasources(Set<String> excludedDatasources) {
		this.excludedDatasources = excludedDatasources;
	}

	/** {@inheritDoc} */
	@Override
	public int getOrder() {
		return order;
	}

	/**
	 * 在Spring上下文设置优先级。
	 * @param order int
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/** {@inheritDoc} */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	private boolean isExcludedDataSource(String beanName) {
		if (excludedDatasources != null && excludedDatasources.contains(beanName)) {
			logger.debug("Spring datasource excluded: " + beanName);
			return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof DataSource) {
			// 我们做isExcludedDataSource测试，如果你是一个数据源
			if (isExcludedDataSource(beanName) || Parameters.isNoDatabase()) {
				return bean;
			}

			final DataSource dataSource = (DataSource) bean;
			JdbcWrapper.registerSpringDataSource(beanName, dataSource);
			final DataSource result = JdbcWrapper.SINGLETON.createDataSourceProxy(beanName,
					dataSource);
			logger.debug("Spring datasource wrapped: " + beanName);
			return result;
		} else if (bean instanceof JndiObjectFactoryBean) {
			// 或的JndiObjectFactoryBean
			if (isExcludedDataSource(beanName) || Parameters.isNoDatabase()) {
				return bean;
			}

			// fix issue 20
			final Object result = createProxy(bean, beanName);
			logger.debug("Spring JNDI factory wrapped: " + beanName);
			return result;
		}

		// I tried here in the post-processor to fix "quartz jobs which are scheduled with spring
		// are not displayed in javamelody, except if there is the following property for
		// SchedulerFactoryBean in spring xml:
		// <property name="exposeSchedulerInRepository" value="true" /> ",

		// but I had some problem with Spring creating the scheduler
		// twice and so registering the scheduler in SchedulerRepository with the same name
		// as the one registered below (and Quartz wants not)
		//		else if (bean != null
		//				&& "org.springframework.scheduling.quartz.SchedulerFactoryBean".equals(bean
		//						.getClass().getName())) {
		//			try {
		//				// Remarque: on ajoute nous même le scheduler de Spring dans le SchedulerRepository
		//				// de Quartz, car l'appel ici de schedulerFactoryBean.setExposeSchedulerInRepository(true)
		//				// est trop tard et ne fonctionnerait pas
		//				final Method method = bean.getClass().getMethod("getScheduler", (Class<?>[]) null);
		//				final Scheduler scheduler = (Scheduler) method.invoke(bean, (Object[]) null);
		//
		//				final SchedulerRepository schedulerRepository = SchedulerRepository.getInstance();
		//				synchronized (schedulerRepository) {
		//					if (schedulerRepository.lookup(scheduler.getSchedulerName()) == null) {
		//						schedulerRepository.bind(scheduler);
		//						scheduler.addGlobalJobListener(new JobGlobalListener());
		//					}
		//				}
		//			} catch (final NoSuchMethodException e) {
		//				// si la méthode n'existe pas (avant spring 2.5.6), alors cela marche sans rien faire
		//				return bean;
		//			} catch (final InvocationTargetException e) {
		//				// tant pis
		//				return bean;
		//			} catch (final IllegalAccessException e) {
		//				// tant pis
		//				return bean;
		//			} catch (SchedulerException e) {
		//				// tant pis
		//				return bean;
		//			}
		//		}

		return bean;
	}

	private Object createProxy(final Object bean, final String beanName) {
		final InvocationHandler invocationHandler = new InvocationHandler() {
			/** {@inheritDoc} */
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				Object result = method.invoke(bean, args);
				if (result instanceof DataSource) {
					result = JdbcWrapper.SINGLETON.createDataSourceProxy(beanName,
							(DataSource) result);
				}
				return result;
			}
		};
		return JdbcWrapper.createProxy(bean, invocationHandler);
	}
}
