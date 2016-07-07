/*
 * The MIT License
 *
 * Copyright 2014 Ericsson.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.replication;


import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefReplicated;

/**
 * Replication cache for RefReplicated events.
 *
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 *
 */
public class ReplicationCache {

    /**
     * A factory class for ReplicationCache.
     */
    public static final class Factory {

        private static final Logger logger = LoggerFactory.getLogger(ReplicationCache.Factory.class);
        /**
         * Constructor
         */
        private Factory() {
        }

        /**
         * Create {@link ReplicationCache}.
         *
         * @return the instance of {@link ReplicationCache} or null.
         */
        public static ReplicationCache createCache() {
            ReplicationCache cache = new ReplicationCache();
            cache.initialize();
            return cache;
        }

        /**
         * Create {@link ReplicationCache}.
         *
         * @param expiration Cache expiration
         * @param unit the unit that expiration is expressed in
         * @return the instance of {@link ReplicationCache} or null.
         */
        public static ReplicationCache createCache(long expiration, TimeUnit unit) {
            ReplicationCache cache = new ReplicationCache(expiration, unit);
            if (!cache.initialize()) {
                logger.info("Initialized replication cache with default settings.");
                cache = new ReplicationCache();
                cache.initialize();
            }
            return cache;
        }
    }

    /**
     * Cache expiration in minutes.
     */
    public static final int DEFAULT_EXPIRATION_IN_MINUTES = (int)TimeUnit.HOURS.toMinutes(6);

    private static final Logger logger = LoggerFactory.getLogger(ReplicationCache.class);
    private final long expiration;
    private final TimeUnit unit;
    private long creationTime;
    private Cache<RefReplicatedId, RefReplicated> events = null;

    /**
     * Default constructor.
     */
    public ReplicationCache() {
        this(DEFAULT_EXPIRATION_IN_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Constructor use by default constructor and for unit tests.
     *
     * @param expiration Cache expiration
     * @param unit the unit that expiration is expressed in
     */
    public ReplicationCache(long expiration, TimeUnit unit) {
        if (expiration >= 0) {
            this.expiration = expiration;
        } else {
            this.expiration = DEFAULT_EXPIRATION_IN_MINUTES;
        }

        if (unit != null) {
            this.unit = unit;
        } else {
            this.unit = TimeUnit.MINUTES;
        }
    }

    /**
     * Set creation time for Replication Cache.
     * @param time when cache was created
     */
    public void setCreationTime(long time) {
        this.creationTime = time;
    }

    /**
     * Initialize cache.
     * @return true if success
     */
    public boolean initialize() {
        if (events == null) {
            try {
                events = CacheBuilder.newBuilder()
                        .expireAfterWrite(expiration, unit)
                        .build();
                logger.info("initialized replication cache with expiration in {}: {}", unit, expiration);
            } catch (Exception ex) {
                logger.warn("initialize failure in {}: {}", unit, expiration);
                return false;
            }
        }
        return true;
    }

    /**
     * Cache the specified RefReplicated.
     * @param refReplicated the event to cache
     */
    public void put(RefReplicated refReplicated) {
        if (events != null) {
            events.put(RefReplicatedId.fromRefReplicated(refReplicated), refReplicated);
        }
    }

    /**
     * Returns if the specified time stamp is expired.
     * Note that we also need to check if the event would
     * have been received before the cache was even created
     * as would be the case of a Jenkins restart.
     * @param timestamp the time stamp to check.
     * @return true if expired, otherwise false
     */
    public boolean isExpired(long timestamp) {
        return (System.currentTimeMillis() - timestamp) > unit.toMillis(expiration)
                || timestamp < creationTime;
    }

    /**
     * Return the cached RefReplicated associated with the specified parameters, if found.
     * @param gerritServer The gerritServer
     * @param gerritProject The gerritProject
     * @param ref The ref
     * @param slaveHost The slaveHost
     * @return the RefReplicated if found, otherwise null
     */
    public RefReplicated getIfPresent(String gerritServer, String gerritProject, String ref, String slaveHost) {
        if (events != null) {
            RefReplicatedId refReplicatedId = new RefReplicatedId(gerritServer, gerritProject, ref, slaveHost);
            return events.getIfPresent(refReplicatedId);
        } else {
            return null;
        }
    }

    /**
     * Id of RefReplicated to identify a RefReplicated event in the cache.
     */
    private static class RefReplicatedId {
        private String gerritServer;
        private String project;
        private String ref;
        private String targetNode;

        /**
         * Standard constructor.
         * @param gerritServer The gerrit server
         * @param project the project
         * @param ref the ref
         * @param targetNode the target node
         */
        public RefReplicatedId(String gerritServer, String project, String ref, String targetNode) {
            this.gerritServer = gerritServer;
            this.project = project;
            this.ref = ref;
            this.targetNode = targetNode;
        }

        @Override
        public int hashCode() {
            //CS IGNORE MagicNumber FOR NEXT 6 LINES. REASON: Autogenerated Code.
            //CS IGNORE AvoidInlineConditionals FOR NEXT 6 LINES. REASON: Autogenerated Code.
            final int prime = 31;
            int result = 1;
            result = prime * result + ((gerritServer == null) ? 0 : gerritServer.hashCode());
            result = prime * result + ((project == null) ? 0 : project.hashCode());
            result = prime * result + ((ref == null) ? 0 : ref.hashCode());
            result = prime * result + ((targetNode == null) ? 0 : targetNode.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RefReplicatedId other = (RefReplicatedId)obj;
            if (gerritServer == null) {
                if (other.gerritServer != null) {
                    return false;
                }
            } else if (!gerritServer.equals(other.gerritServer)) {
                return false;
            }
            if (project == null) {
                if (other.project != null) {
                    return false;
                }
            } else if (!project.equals(other.project)) {
                return false;
            }
            if (ref == null) {
                if (other.ref != null) {
                    return false;
                }
            } else if (!ref.equals(other.ref)) {
                return false;
            }
            if (targetNode == null) {
                if (other.targetNode != null) {
                    return false;
                }
            } else if (!targetNode.equals(other.targetNode)) {
                return false;
            }
            return true;
        }

        /**
         * Create a RefReplicatedId for the specified RefReplicated.
         * @param refReplicated The RefReplicated
         * @return the RefReplicatedId
         */
        private static RefReplicatedId fromRefReplicated(RefReplicated refReplicated) {
            String gerritServer = null;
            if (refReplicated.getProvider() != null) {
                gerritServer = refReplicated.getProvider().getName();
            }
            return new RefReplicatedId(gerritServer, refReplicated.getProject(), refReplicated.getRef(),
                    refReplicated.getTargetNode());
        }
    }
}
