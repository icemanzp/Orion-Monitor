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
package com.wfj.netty.servlet.util; // NOPMD

import java.util.HashMap;
import java.util.Map;

import com.wfj.netty.servlet.dto.ConnectionInformations;
import com.wfj.netty.servlet.dto.Counter;
import com.wfj.netty.servlet.dto.CounterError;
import com.wfj.netty.servlet.dto.CounterRequest;
import com.wfj.netty.servlet.dto.CounterRequestContext;
import com.wfj.netty.servlet.dto.DatabaseInformations;
import com.wfj.netty.servlet.dto.ThreadInformations;

/**
 * Liste des alias XStream pour les conversions XML et JSON.
 * 
 * @author Emeric Vernat
 */
public class XStreamAlias {
	private XStreamAlias() {
		super();
	}

	public static Map<String, Class<?>> getMap() {
		final Map<String, Class<?>> result = new HashMap<String, Class<?>>();
		result.put("counter", Counter.class);
		result.put("request", CounterRequest.class);
		result.put("requestContext", CounterRequestContext.class);
		result.put("threadInformations", ThreadInformations.class);
		result.put("connectionInformations", ConnectionInformations.class);
		result.put("counterError", CounterError.class);
		result.put("databaseInformations", DatabaseInformations.class);
		return result;
	}
}
