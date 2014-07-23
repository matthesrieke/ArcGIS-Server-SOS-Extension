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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.n52.sos.db.AccessGDB;
import org.n52.util.CommonUtilities;
import org.n52.util.logging.Logger;

public class CacheScheduler {
	
	public static Logger LOGGER = Logger.getLogger(CacheScheduler.class.getName());

	private static CacheScheduler instance;

	private static final long ONE_HOUR_MS = 1000 * 60 * 60;
	public static final long FIFTEEN_MINS_MS = 1000 * 60 * 15;
	
	private Timer cacheTimer;
	private AccessGDB geoDB;
	
	private List<AbstractEntityCache<?>> candidates = new ArrayList<>();

	private boolean updateCacheOnStartup;

	public long lastSchedulerThread;

	private Timer monitorTimer;

	public static synchronized void init(AccessGDB geoDB, boolean updateCacheOnStartup) {
		if (instance == null) {
			instance = new CacheScheduler(geoDB, updateCacheOnStartup);
		}
	}
	
	public static synchronized CacheScheduler instance() {
		return instance;
	}
	
	private CacheScheduler(AccessGDB geoDB, boolean updateCacheOnStartup) {
		this.geoDB = geoDB;
		this.updateCacheOnStartup = updateCacheOnStartup;
		
		/*
		 * first use the PUMC, others might depend on it
		 */
		try {
			candidates.add(PropertyUnitMappingCache.instance());
		} catch (FileNotFoundException e) {
			LOGGER.warn(e.getMessage(), e);
		}

		try {
			candidates.add(ObservationOfferingCache.instance());
		} catch (FileNotFoundException e) {
			LOGGER.warn(e.getMessage(), e);
		}
		
		this.cacheTimer = new Timer(true);
		this.monitorTimer = new Timer(true);
		
		if (!updateCacheOnStartup) {
			LOGGER.info("Update cache on startup disabled!");
		}
		else {
			try {
				List<AbstractEntityCache<?>> requiresUpdates = cacheUpdateRequired();
				if (!requiresUpdates.isEmpty()) {
					LOGGER.info(String.format("Cache update required for: %s", requiresUpdates.toString()));
					/*
					 * now
					 */
					this.cacheTimer.schedule(new UpdateCacheTask(requiresUpdates), 0);	
				}
				else {
					LOGGER.info("No cache update required. Last update not longer ago than minutes "+FIFTEEN_MINS_MS/(1000*60));
				}
			} catch (FileNotFoundException e) {
				LOGGER.warn(e.getMessage(), e);
				LOGGER.warn("could not initialize cache. disabling scheduled updates.");
				return;
			}			
		}
		
		/*
		 * every 4am
		 */
		Calendar c = new GregorianCalendar();
		c.add(Calendar.DAY_OF_MONTH, 1);
		c.set(Calendar.HOUR_OF_DAY, 4);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		
		Random random = new Random();
		c.add(Calendar.SECOND, 5 + (random.nextInt(11)*2));
		
		this.cacheTimer.scheduleAtFixedRate(new UpdateCacheTask(candidates), c.getTime(), ONE_HOUR_MS * 24);
		
//		Calendar c = new GregorianCalendar();
//		c.add(Calendar.MINUTE, 2);
//		c.set(Calendar.SECOND, 0);
//		
//		Random random = new Random();
//		c.add(Calendar.SECOND, 5 + (random.nextInt(11)*2));
//		
//		this.cacheTimer.scheduleAtFixedRate(new UpdateCacheTask(candidates), c.getTime(), ONE_HOUR_MS);
		
		LOGGER.severe("Next scheduled cache update: "+c.getTime().toString());
	}
	
	
	public List<AbstractEntityCache<?>> getCandidates() {
		return candidates;
	}

	public boolean isUpdateCacheOnStartup() {
		return updateCacheOnStartup;
	}

	private List<AbstractEntityCache<?>> cacheUpdateRequired() throws FileNotFoundException {
		List<AbstractEntityCache<?>> result = new ArrayList<>();
		
		for (AbstractEntityCache<?> cand : candidates) {
			if (cand.requiresUpdate()) {
				result.add(cand);
			}
		}
		
		return result;
	}

	public void shutdown() {
		this.cacheTimer.cancel();
		this.monitorTimer.cancel();
		try {
			freeCacheUpdateLock();
		} catch (IOException e) {
			LOGGER.warn(e.getMessage(), e);
		}
//		for (AbstractEntityCache<?> aec : candidates) {
//			try {
//				aec.getSingleInstance().freeUpdateLock();
//			} catch (FileNotFoundException e) {
//				LOGGER.warn(e.getMessage(), e);
//			}	
//		}
		
	}
	
	private class UpdateCacheTask extends TimerTask {

		private List<AbstractEntityCache<?>> candidates;

		public UpdateCacheTask(List<AbstractEntityCache<?>> candidates) {
			this.candidates = candidates;
		}

		@Override
		public void run() {
			scheduleLockMonitor();
			
			try {
				synchronized (CacheScheduler.class) {
					if (!retrieveCacheUpdateLock()) {
						LOGGER.info("chache updating locked. skipping");
						return;
					}
				}

				CacheScheduler.this.lastSchedulerThread = Thread.currentThread().getId();
				LOGGER.info("update cache... using thread "+ lastSchedulerThread);
				
				for (AbstractEntityCache<?> aec : this.candidates) {
					aec.updateCache(geoDB);
				}
				
				freeCacheUpdateLock();
				
				LOGGER.info("all caches updated!");					
			} catch (IOException | CacheException | RuntimeException e) {
				LOGGER.warn(e.getMessage(), e);
			}
			
		}

		private void scheduleLockMonitor() {
			CacheScheduler.this.monitorTimer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					LOGGER.info("Monitoring cache update using thread "+Thread.currentThread().getId());
					Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
					
					Thread target = null;
					for (Thread t : stacks.keySet()) {
						if (t.getId() == CacheScheduler.this.lastSchedulerThread) {
							target = t;
							break;
						}
					}
					
					if (target != null) {
						LOGGER.info("lastSchedulerThread status: " + target.getState());
						LOGGER.info("lastSchedulerThread isalive: " + target.isAlive());
						LOGGER.info("lastSchedulerThread isinterrupted: " + target.isInterrupted());
					}
					else {
						LOGGER.warn("Could not find lastSchedulerThread in current stack traces");
					}
					
					try {
						if (!retrieveCacheUpdateLock()) {
							LOGGER.warn("The cache update took obviously too long. Freeing the lock, trying to interrupt cache update.");
							target.interrupt();
						}
					} catch (IOException e) {
						LOGGER.warn(e.getMessage(), e);
					}
					finally {
						try {
							freeCacheUpdateLock();
						} catch (IOException e) {
							LOGGER.warn(e.getMessage(), e);
						}
					}
					
				}
			}, ONE_HOUR_MS/2);
		}
		
	}

	private synchronized boolean retrieveCacheUpdateLock() throws IOException {
		File lockFile = resolveCacheLockFile();
		
		if (!lockFile.exists()) {
			boolean worked = lockFile.createNewFile();
			if (worked) {
				return true;
			}
			else {
				LOGGER.info("Could not create cache.lock file!");
				return false;
			}
		}
		
		return false;
	}

	private synchronized void freeCacheUpdateLock() throws IOException {
		File lockFile = resolveCacheLockFile();
		
		if (lockFile.exists()) {
			lockFile.delete();
		}
	}

	private File resolveCacheLockFile() throws FileNotFoundException {
		File dir = CommonUtilities.resolveCacheBaseDir();
		File lockFile = new File(dir, "cache.lock");
		return lockFile;
	}
	
}
