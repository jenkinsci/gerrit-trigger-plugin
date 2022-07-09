package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.dynamictrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerTimer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

/**
 * Keeps map between url and dynamic trigger configuration.
 * It's used to speed up execution time during updating
 * trigger jobs and reduce number of connections for the duplicated configs.
 */
public final class DynamicConfigurationCacheProxy {

    private static final Logger logger = LoggerFactory.getLogger(
        DynamicConfigurationCacheProxy.class);

    private static final DynamicConfigurationCacheProxy CACHE_PROXY = new DynamicConfigurationCacheProxy();
    private final Cache cache = new Cache();

    /**
     * A cache entry for the simplistic cache implementation
     */
    static class CacheEntry {
        private final long created;
        private final List<GerritProject> projects;

        /**
         * Creates a cache entry
         *
         * @param projects
         *   the project list from gerrit
         */
        public CacheEntry(List<GerritProject> projects) {
            this.created = System.currentTimeMillis();
            this.projects = projects;
        }

        /**
         * Checks if the cache entry is expired.
         *
         * @param ttl
         *   the timespan after which entries are regarded as expired.
         * @return
         *   true in case the entry is expired otherwise false.
         */
        public boolean isExpired(long ttl) {
            return (ttl > created);
        }

        /**
         * Returns the project list for this entry.
         *
         * @return
         *   the cached project list.
         */
        public List<GerritProject> getProjects() {
            return this.projects;
        }
    }

    /**
     * A very simplistic cache implementation.
     * The cleanup method needs to called on a regular basis.
     */
    static class Cache {
        private final Map<String, CacheEntry> cache = new HashMap<>();

        /**
         * Creates a new instance
         */
        public Cache() {
        }

        /**
         * Gets the entry list for the given url from the cache.
         * Will return a non in case the url is unknown or the entry
         * is too old/expired.
         *
         * @param url
         *   the url from which the project list was fetched
         * @param ttl
         *   the timestamp after which the entry is regarded as too old.
         *
         * @return
         *   the cached project list or none in case the cache does not
         *   know the url or the cache entry has expired.
         */
        public List<GerritProject> get(String url, long ttl) {
            synchronized (this.cache) {
                CacheEntry entry = cache.get(url);

                if (entry == null) {
                  return null;
                }

                if (entry.isExpired(ttl)) {
                  return null;
                }

                return entry.getProjects();
            }
        }

        /**
         * Adds a list of gerrit projects to the cache.
         *
         * @param url
         *   the url from which the project list was fetched.
         * @param entry
         *   CacheEntry object.
         *
         * @return
         *   the project list which was added to the cache.
         */
        public List<GerritProject> put(String url, CacheEntry entry) {
            synchronized (this.cache) {
                this.cache.put(url, entry);
            }

            return entry.getProjects();
        }

        /**
         * Cleans the cache, needs to be called on a regular base.
         *
         * It will remove all entries which are older than the ttl.
         * @param ttl
         *   the timestamp after which entries are regarded as expired.
         */
        public void cleanup(long ttl) {
            synchronized (this.cache) {
                Iterator<Map.Entry<String, CacheEntry> > it = this.cache.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<String, CacheEntry> entry = it.next();

                    if (!entry.getValue().isExpired(ttl)) {
                      continue;
                    }

                    logger.trace("Removing expired url {} from cache", entry.getKey());
                    it.remove();
                }
            }
        }

        /**
         * Flushes the cache and removes all entries.
         */
        public void clear() {
            synchronized (this.cache) {
                this.cache.clear();
            }
        }
    }

    /**
     * Private constructor.
     */
    private DynamicConfigurationCacheProxy() {
    }

    /**
     * Calculates the timestamp when an entry is regarded to old and should
     * be discarded.
     *
     * @return the maximal age.
     */
    public long getMaxTTL() {
        long updateInterval = TimeUnit.SECONDS.toMillis(
            GerritTriggerTimer.getInstance().calculateAverageDynamicConfigRefreshInterval());
        return System.currentTimeMillis() - updateInterval;
    }

    /**
     * Returns dynamic trigger config from the cache if it's available.
     * Otherwise send query.
     *
     * @param url url to dynamic trigger config.
     * @param parser the GerritDynamicUrlProcessor object.
     * @return list of gerrit projects.
     * @throws DynamicTriggerException if so.
     */
    public List<GerritProject> fetch(String url, GerritDynamicUrlProcessor parser) throws DynamicTriggerException {
        long ttl = this.getMaxTTL();
        List<GerritProject> projects = this.cache.get(url, ttl);

        if (projects != null) {
            // Use the idle time to do cleanup.
            logger.debug("Get dynamic projects from cache for URL: " + url);
            this.cache.cleanup(ttl);
            return projects;
        }

        logger.info("Get dynamic projects directly for URL: {}", url);
        return this.cache.put(url, new CacheEntry(parser.fetch(url)));
    }

    /**
     * Return global cache proxy objects.
     *
     * @return cache proxy object.
     */
    public static DynamicConfigurationCacheProxy getInstance() {
        return CACHE_PROXY;
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        this.cache.clear();
    }
}
