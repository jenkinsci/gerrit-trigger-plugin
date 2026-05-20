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

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
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
     * Default Hazelcast port.
     */
    public static final int DEFAULT_PORT = 5702;

    /**
     * Default number of ports to try for auto-increment.
     */
    public static final int DEFAULT_PORT_COUNT = 10;

    /**
     * Default operation call timeout in milliseconds.
     */
    public static final String DEFAULT_OPERATION_TIMEOUT = "30000";

    /**
     * System property to specify cluster name.
     * Default: "gerrit-trigger-cluster"
     */
    public static final String CLUSTER_NAME_PROPERTY = "gerrit.trigger.coordination.hazelcast.cluster.name";

    /**
     * System property to specify Hazelcast port.
     * Default: 5702
     */
    public static final String PORT_PROPERTY = "gerrit.trigger.coordination.hazelcast.port";

    /**
     * System property to specify number of ports to try for auto-increment.
     * Default: 10
     */
    public static final String PORT_COUNT_PROPERTY = "gerrit.trigger.coordination.hazelcast.port.count";

    /**
     * System property to specify operation call timeout in milliseconds.
     * Default: 30000 (30 seconds)
     */
    public static final String OPERATION_TIMEOUT_PROPERTY = "gerrit.trigger.coordination.hazelcast.operation.timeout";

    /**
     * System property to specify discovery mode.
     * Values: "kubernetes", "tcp", "multicast" (for testing only).
     */
    public static final String DISCOVERY_MODE_PROPERTY = "gerrit.trigger.coordination.hazelcast.discovery.mode";

    /**
     * System property to specify Kubernetes service name.
     * Default: "jenkins"
     */
    public static final String K8S_SERVICE_NAME_PROPERTY = "gerrit.trigger.coordination.hazelcast.k8s.service.name";

    /**
     * System property to specify Kubernetes namespace.
     * Default: "default"
     */
    public static final String K8S_NAMESPACE_PROPERTY = "gerrit.trigger.coordination.hazelcast.k8s.namespace";

    /**
     * System property to specify TCP/IP members (comma-separated).
     * Example: "replica-0.jenkins:5701,replica-1.jenkins:5701"
     */
    public static final String TCP_MEMBERS_PROPERTY = "gerrit.trigger.coordination.hazelcast.tcp.members";

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastConfig() {
        // Utility class
    }

    /**
     * Creates a Hazelcast configuration suitable for the current environment.
     *
     * @return configured Hazelcast Config object
     */
    public static Config createConfig() {
        Config config = new Config();

        // Set cluster name (configurable via system property)
        String clusterName = System.getProperty(CLUSTER_NAME_PROPERTY, DEFAULT_CLUSTER_NAME);
        config.setClusterName(clusterName);
        logger.info("Hazelcast cluster name: {}", clusterName);

        // Set instance name (includes Jenkins URL for identification)
        String instanceName = generateInstanceName();
        config.setInstanceName(instanceName);
        logger.info("Hazelcast instance name: {}", instanceName);

        // Configure network and discovery
        configureNetwork(config);

        // Configure settings (all configurable via system properties)
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty("hazelcast.shutdownhook.enabled", "false"); // We manage shutdown

        String operationTimeout = System.getProperty(OPERATION_TIMEOUT_PROPERTY, DEFAULT_OPERATION_TIMEOUT);
        config.setProperty("hazelcast.operation.call.timeout.millis", operationTimeout);
        logger.info("Hazelcast operation timeout: {} ms", operationTimeout);

        // Register Compact Serializers for event claiming and build memory
        // This enables cross-JVM serialization compatibility with sidecar deployment
        config.getSerializationConfig()
                .getCompactSerializationConfig()
                .addSerializer(new EventClaimSerializer())
                .addSerializer(new EntryDataSerializer())
                .addSerializer(new MemoryImprintDataSerializer());
        logger.debug("Registered Compact Serializers for EventClaim, EntryData, and MemoryImprintData");

        logger.info("Hazelcast configuration created for cluster: {}", clusterName);

        return config;
    }

    /**
     * Configures network settings and discovery mechanism.
     *
     * @param config the Hazelcast config to configure
     */
    private static void configureNetwork(Config config) {
        NetworkConfig networkConfig = config.getNetworkConfig();

        // Set port (configurable via system property)
        int port = Integer.parseInt(System.getProperty(PORT_PROPERTY, String.valueOf(DEFAULT_PORT)));
        networkConfig.setPort(port);
        networkConfig.setPortAutoIncrement(true);

        // Set port count (configurable via system property)
        int portCount = Integer.parseInt(System.getProperty(PORT_COUNT_PROPERTY, String.valueOf(DEFAULT_PORT_COUNT)));
        networkConfig.setPortCount(portCount);

        logger.info("Hazelcast network: port={}, portCount={} (will try ports {}-{})",
                port, portCount, port, port + portCount - 1);

        JoinConfig joinConfig = networkConfig.getJoin();

        // Determine discovery mode
        String discoveryMode = System.getProperty(DISCOVERY_MODE_PROPERTY, "auto");
        logger.info("Hazelcast discovery mode: {}", discoveryMode);

        if ("multicast".equalsIgnoreCase(discoveryMode)) {
            // Multicast mode - primarily for testing
            configureMulticastDiscovery(joinConfig);
        } else if ("kubernetes".equalsIgnoreCase(discoveryMode) || isKubernetesEnvironment()) {
            configureKubernetesDiscovery(joinConfig, port);
        } else if ("tcp".equalsIgnoreCase(discoveryMode) || hasTcpMembersConfigured()) {
            configureTcpDiscovery(joinConfig);
        } else {
            // Fallback: Try Kubernetes, then TCP
            logger.info("Auto-detecting discovery mechanism...");
            if (isKubernetesEnvironment()) {
                configureKubernetesDiscovery(joinConfig, port);
            } else {
                configureTcpDiscovery(joinConfig);
            }
        }

        // Disable multicast unless explicitly enabled
        if (!"multicast".equalsIgnoreCase(discoveryMode)) {
            joinConfig.getMulticastConfig().setEnabled(false);
        }
    }

    /**
     * Configures Kubernetes discovery.
     *
     * @param joinConfig the join configuration
     * @param port the Hazelcast port to discover (filters out other Hazelcast instances on different ports)
     */
    private static void configureKubernetesDiscovery(JoinConfig joinConfig, int port) {
        String serviceName = System.getProperty(K8S_SERVICE_NAME_PROPERTY, "jenkins");
        String namespace = System.getProperty(K8S_NAMESPACE_PROPERTY, "default");

        logger.info("Configuring Kubernetes discovery: service={}, namespace={}, port={}",
                serviceName, namespace, port);

        joinConfig.getKubernetesConfig()
                .setEnabled(true)
                .setProperty("service-name", serviceName)
                .setProperty("namespace", namespace)
                .setProperty("service-port", String.valueOf(port));

        // Disable other discovery methods
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        joinConfig.getAzureConfig().setEnabled(false);
    }

    /**
     * Configures TCP/IP discovery with static member list.
     *
     * @param joinConfig the join configuration
     */
    private static void configureTcpDiscovery(JoinConfig joinConfig) {
        String tcpMembers = System.getProperty(TCP_MEMBERS_PROPERTY, "");

        if (tcpMembers.isEmpty()) {
            logger.warn("TCP discovery mode selected but no members configured. "
                    + "Set {} system property.", TCP_MEMBERS_PROPERTY);
            logger.warn("Example: -D{}=replica-0.jenkins:5701,replica-1.jenkins:5701",
                    TCP_MEMBERS_PROPERTY);
            // Use localhost as fallback for single-instance testing
            tcpMembers = "localhost:5701";
        }

        logger.info("Configuring TCP/IP discovery with members: {}", tcpMembers);

        joinConfig.getTcpIpConfig()
                .setEnabled(true)
                .addMember(tcpMembers);

        // Disable other discovery methods
        joinConfig.getKubernetesConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        joinConfig.getAzureConfig().setEnabled(false);
    }

    /**
     * Configures multicast discovery.
     * <p>
     * <strong>Warning:</strong> Multicast is NOT suitable for production use.
     * This mode is primarily for testing purposes where a simple discovery
     * mechanism is needed without Kubernetes or TCP configuration.
     * <p>
     * Multicast allows Hazelcast instances on the same network segment to
     * automatically discover each other.
     *
     * @param joinConfig the join configuration
     */
    private static void configureMulticastDiscovery(JoinConfig joinConfig) {
        logger.warn("Configuring multicast discovery - NOT SUITABLE FOR PRODUCTION, TESTING ONLY");

        joinConfig.getMulticastConfig()
                .setEnabled(true);

        // Disable other discovery methods
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getKubernetesConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        joinConfig.getAzureConfig().setEnabled(false);
    }

    /**
     * Checks if running in Kubernetes environment.
     *
     * @return true if Kubernetes environment detected
     */
    private static boolean isKubernetesEnvironment() {
        // Check for Kubernetes service account token
        return System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    /**
     * Checks if TCP members are configured via system property.
     *
     * @return true if TCP members property is set
     */
    private static boolean hasTcpMembersConfigured() {
        String tcpMembers = System.getProperty(TCP_MEMBERS_PROPERTY);
        return tcpMembers != null && !tcpMembers.trim().isEmpty();
    }

    /**
     * Generates a unique instance name for this Hazelcast member.
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
