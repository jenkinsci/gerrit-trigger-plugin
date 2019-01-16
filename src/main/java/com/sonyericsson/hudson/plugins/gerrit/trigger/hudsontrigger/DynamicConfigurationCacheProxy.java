package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps map between url and dynamic trigger configuration.
 * It's used to speed up execution time during updating
 * trigger jobs and reduce number of connections for the duplicated configs.
 */
final class DynamicConfigurationCacheProxy {
    private static final DynamicConfigurationCacheProxy CACHE_PROXY = new DynamicConfigurationCacheProxy();
    private final Map<String, List<GerritProject>> cache = new HashMap<String, List<GerritProject>>();
    private static final Logger logger = LoggerFactory.getLogger(DynamicConfigurationCacheProxy.class);
    private final Map<String, Long> ttl = new HashMap<String, Long>();

    /**
     * Private constructor.
     */
    private DynamicConfigurationCacheProxy() {
    }

    /**
     * Returns dynamic trigger config from the cache if it's available.
     * Otherwise send query.
     *
     * @param url url to dynamic trigger config.
     * @return list of gerrit projects.
     * @throws IOException if so.
     * @throws ParseException if so.
     */
    synchronized List<GerritProject> fetchThroughCache(String url) throws IOException, ParseException {
        if (cache.containsKey(url) && !isExpired(url)) {
            logger.debug("Get dynamic projects from cache for URL: " + url);
            return cache.get(url);
        }

        logger.info("Get dynamic projects directly for URL: {}", url);
        List<GerritProject> gerritProjects = GerritDynamicUrlProcessor.fetch(url);
        ttl.put(url, System.currentTimeMillis());
        cache.put(url, gerritProjects);

        return gerritProjects;
    }

    /**
     * Return global cache proxy objects.
     *
     * @return cache proxy object.
     */
    static DynamicConfigurationCacheProxy getInstance() {
        return CACHE_PROXY;
    }

    /**
     * Check the need to update specified url.
     *
     * @param url url.
     * @return true if cached value is expired.
     */
    private boolean isExpired(String url) {
        Long lastTimeUpdated = ttl.get(url);
        if (lastTimeUpdated == null) {
            lastTimeUpdated = System.currentTimeMillis();
        }

        long updateInterval = GerritTriggerTimer.getInstance().calculateAverageDynamicConfigRefreshInterval();
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastTimeUpdated) > updateInterval;
    }

    /**
     * Clears the cache.
     */
    void clear() {
        ttl.clear();
        cache.clear();
    }
}
