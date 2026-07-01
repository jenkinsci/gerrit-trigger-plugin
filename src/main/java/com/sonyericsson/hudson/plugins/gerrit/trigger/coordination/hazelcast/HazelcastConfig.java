/*
 * The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.hazelcast.client.config.ClientConfig;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration builder for Hazelcast cluster.
 * Creates appropriate configuration based on deployment environment (Kubernetes, TCP/IP, etc.).
 *
 */
public final class HazelcastConfig {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastConfig.class);

    /**
     * Default cluster name for Gerrit Trigger plugin Hazelcast cluster.
     */
    public static final String DEFAULT_CLUSTER_NAME = "gerrit-trigger-cluster";

    /**
     * System property to specify addresses for Hazelcast client (comma-separated host:port).
     * Default: "localhost:5702" — assumes a Hazelcast sidecar listening on port 5702 in the same pod.
     * Example: "localhost:5702"
     */
    public static final String CLIENT_ADDRESSES_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.client.addresses";

    /**
     * System property to specify the cluster name to connect to.
     * Must match the cluster name of the target Hazelcast cluster.
     * Default: {@link #DEFAULT_CLUSTER_NAME}.
     */
    public static final String CLIENT_CLUSTER_NAME_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.client.cluster.name";

    /**
     * Default address for Hazelcast client: local sidecar on port 5702.
     */
    public static final String DEFAULT_CLIENT_ADDRESS = "localhost:5702";

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastConfig() {
        // Utility class
    }

    /**
     * Creates a Hazelcast client configuration to connect to an existing cluster.
     * <p>
     * The client connects to the addresses specified by {@link #CLIENT_ADDRESSES_PROPERTY}
     * (default: {@link #DEFAULT_CLIENT_ADDRESS}) and joins the cluster whose name matches
     * {@link #CLIENT_CLUSTER_NAME_PROPERTY}.
     *
     * @return configured Hazelcast ClientConfig
     */
    public static ClientConfig createClientConfig() {
        ClientConfig config = new ClientConfig();

        String clusterName = System.getProperty(CLIENT_CLUSTER_NAME_PROPERTY, DEFAULT_CLUSTER_NAME);
        config.setClusterName(clusterName);
        logger.info("Hazelcast client cluster name: {}", clusterName);

        String addressesProperty = System.getProperty(CLIENT_ADDRESSES_PROPERTY, DEFAULT_CLIENT_ADDRESS);
        logger.info("Hazelcast client addresses: {}", addressesProperty);

        for (String address : addressesProperty.split(",")) {
            String trimmed = address.trim();
            if (!trimmed.isEmpty()) {
                config.getNetworkConfig().addAddress(trimmed);
            }
        }

        config.setProperty("hazelcast.logging.type", "slf4j");

        // Register Compact Serializers — must match member config for cross-JVM deserialization
        config.getSerializationConfig()
                .getCompactSerializationConfig()
                .addSerializer(new EventClaimSerializer())
                .addSerializer(new EntryDataSerializer())
                .addSerializer(new MemoryImprintDataSerializer());
        logger.debug("Registered Compact Serializers for EventClaim, EntryData, and MemoryImprintData");

        logger.info("Hazelcast client configuration created for cluster: {}", clusterName);
        return config;
    }

    /**
     * Generates a unique instance name for this Hazelcast client.
     * Includes Jenkins URL and hostname for identification.
     *
     * @return instance name
     */
    private static String generateInstanceName() {
        StringBuilder name = new StringBuilder("gerrit-trigger");

        // Add Jenkins root URL if available
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            String rootUrl = jenkins.getRootUrl();
            if (rootUrl != null && !rootUrl.isEmpty()) {
                // Extract hostname from URL
                try {
                    java.net.URI uri = new java.net.URI(rootUrl);
                    String host = uri.getHost();
                    if (host != null) {
                        name.append("-").append(host.replace('.', '-'));
                    }
                } catch (Exception e) {
                    logger.debug("Could not parse Jenkins root URL: {}", rootUrl, e);
                }
            }
        }

        // Add hostname
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            name.append("-").append(hostname.replace('.', '-'));
        } catch (Exception e) {
            logger.debug("Could not determine hostname", e);
        }

        return name.toString();
    }
}
