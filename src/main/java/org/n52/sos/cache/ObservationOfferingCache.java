/**
 * Copyright (C) 2012 52°North Initiative for Geospatial Open Source Software GmbH
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
package org.n52.sos.cache;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.n52.oxf.valueDomains.time.ITimePeriod;
import org.n52.sos.dataTypes.EnvelopeWrapper;
import org.n52.sos.dataTypes.ObservationOffering;
import org.n52.sos.db.AccessGDB;

public class ObservationOfferingCache extends AbstractEntityCache<ObservationOffering> {

	private static final String TOKEN_SEP = "@@";
	private static ObservationOfferingCache instance;

	public static synchronized ObservationOfferingCache instance() throws FileNotFoundException {
		if (instance == null) {
			instance = new ObservationOfferingCache();
		}
		
		return instance;
	}
	
	private ObservationOfferingCache() throws FileNotFoundException {
		super();
	}

	@Override
	protected String getCacheFileName() {
		return "observationOfferings.cache";
	}

	@Override
	protected Map<String, ObservationOffering> deserializeEntityCollection(
			FileInputStream fis) {
		Map<String, ObservationOffering> result = new HashMap<>();
		Scanner sc = new Scanner(fis);
		
		String line;
		while (sc.hasNext()) {
			line = sc.nextLine();
			String id = line.substring(0, line.indexOf("="));
			result.put(id, deserializeEntity(line.substring(line.indexOf("="), line.length())));
		}
		
		sc.close();
		
		return result;
	}

	@Override
	protected String serializeEntity(ObservationOffering entity) throws CacheException {
		StringBuilder sb = new StringBuilder();
		
		sb.append(entity.getId());
		sb.append(TOKEN_SEP);
		sb.append(entity.getName());
		sb.append(TOKEN_SEP);
		sb.append(entity.getProcedureIdentifier());
		sb.append(TOKEN_SEP);
		try {
			sb.append(EnvelopeEncoderDecoder.encode(entity.getObservedArea()));
		} catch (IOException e) {
			throw new CacheException(e);
		}
		sb.append(TOKEN_SEP);
		sb.append(Arrays.toString(entity.getObservedProperties()));
		sb.append(TOKEN_SEP);
		sb.append(TimePeriodEncoder.encode(entity.getTimeExtent()));
		
		return sb.toString();
	}

	@Override
	protected ObservationOffering deserializeEntity(String line) {
		String[] values = line.split(TOKEN_SEP);
		
		if (values == null || values.length != 6) {
			return null;
		}
		
		String id = values[0].trim();
		String name = values[1].trim();
		String proc = values[2].trim();
		EnvelopeWrapper env = EnvelopeEncoderDecoder.decode(values[3]);
		String[] props = decodeStringArray(values[4]);
		ITimePeriod time = TimePeriodEncoder.decode(values[5]);
		
		return new ObservationOffering(id, name, props, proc, env, time);
	}

	public void updateCache(AccessGDB geoDB) throws CacheException, IOException {
		if (!instance.requestUpdateLock()) {
			LOGGER.info("cache is currently already updating");
			return;
		}
		
		Collection<ObservationOffering> entities = geoDB.getOfferingAccess().getNetworksAsObservationOfferings();
		instance.storeEntityCollection(entities);
		
		instance.freeUpdateLock();		
	}


}
