# Distributed Event Management support

The plugin supports Distributed Event Management support where two or more replicas or nodes of a logical Jenkins(*) instance
run in parallel (sharing the Gerrit memory of the plugin). When enabled, a Hazelcast
cluster coordinates the instances so that:

- Each Gerrit event is processed by **exactly one** instance (event claiming)
- Build state is shared across instances (distributed build memory)
- Gerrit feedback (votes and comments) are sent **exactly once** per build event

By default, the plugin runs in **local mode** and requires no additional configuration.
Local mode is fully backward-compatible with single-instance Jenkins deployments.

Alternative coordination backends can be implemented by extending
[`CoordinationModeProvider`](../src/main/java/com/sonyericsson/hudson/plugins/gerrit/trigger/spi/CoordinationModeProvider.java)
— a Jenkins `ExtensionPoint` that wires together the storage, event-claiming, and
notification-claiming strategies for a given coordination mode. A higher `@Extension`
ordinal takes precedence over the built-in Hazelcast provider.

## Hazelcast implementation

Hazelcast mode is activated via a JVM system property. Jenkins connects as a lightweight
client to a Hazelcast sidecar container, reusing the cross-pod cluster the sidecar
maintains.

### Configuration Properties

All distributed storage settings are controlled by JVM system properties passed to Jenkins on startup.

| Property | Default | Description                                           |
|---|---|-------------------------------------------------------|
| `gerrit.trigger.coordination.mode` | `local` | Set to `hazelcast` to enable distributed coordination |
| `gerrit.trigger.coordination.hazelcast.client.addresses` | `localhost:5702` | Comma-separated `host:port` list of sidecar addresses |
| `gerrit.trigger.coordination.hazelcast.client.cluster.name` | `gerrit-trigger-cluster` | Cluster name to connect to                            |
| `gerrit.trigger.coordination.hazelcast.claim.ttl.seconds` | `300` | TTL, in seconds, for an event claim                   |

Port `5702` is used by default to avoid potential conflicts with other Hazelcast cluster, which could occupy port `5701`.

Cluster name must be different for each logical instance. Multiple replicas or nodes of a logical instance may configure the same cluster name. Different logical instances require separate cluster names.

### Claim TTL

When an instance claims a Gerrit event for processing, the claim is stored in hazelcast with a time-to-live
(TTL), after which it expires and the event becomes eligible to be claimed again. The TTL is
controlled by the `gerrit.trigger.coordination.hazelcast.claim.ttl.seconds` property and defaults
to 300 seconds (5 minutes).

**Current situation:** if a hazelcast replica is offline (or otherwise unable to complete processing) for
longer than the claim TTL, the claims it holds on events processed before the outage expire. When
Gerrit event playback replays those events after the outage, they are treated as unclaimed and are
picked up again, causing duplicate builds.

**Recommendation:** set `gerrit.trigger.coordination.hazelcast.claim.ttl.seconds` explicitly, and
size it to exceed the maximum downtime you expect a hazelcast replica could experience (e.g. a rolling
restart, node eviction, or extended network partition) before Gerrit event playback would replay
missed events for that period. Extending the TTL prevents old claims from expiring during a
prolonged outage and stops stale events from re-triggering builds once the replica returns.

Increasing the TTL trades off against how long a claim from a hazelcast replica that has permanently failed
(not just gone temporarily offline) blocks that event from being reprocessed by another instance.
Choose a value that reflects your actual expected downtime rather than leaving it at the default.

### Configuration Example

#### Kubernetes — Client Mode with Hazelcast Sidecar

The plugin can connect to Hazelcast cluster as a lightweight client. For example, if we are running 
K8s environment with the Jenkins instance inside a pod, we can have a side-container with Hazelcast
to set up the Hazelcast cluster. In this kind of cases, we would the a configuration setup similar
to the following one:

Add the following JVM arguments to the Jenkins instance:

    -Dgerrit.trigger.coordination.mode=hazelcast
    -Dgerrit.trigger.coordination.hazelcast.client.addresses=localhost:5702
    -Dgerrit.trigger.coordination.hazelcast.client.cluster.name=gerrit-trigger-cluster

Add the sidecar container to the instance pod spec:

```yaml
- name: hazelcast
  image: hazelcast/hazelcast:5.3.8
  ports:
    - containerPort: 5702
      name: hazelcast
  env:
    - name: JAVA_OPTS
      value: >-
        -Dhazelcast.config=/dev/stdin
        -Dhazelcast.local.publicAddress=$(POD_IP):5702
    - name: HZ_CLUSTERNAME
      value: gerrit-trigger-cluster
    - name: HZ_NETWORK_PORT_PORT
      value: "5702"
```

Grant the pod's service account read access to Kubernetes endpoints so Hazelcast can
discover its peers:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: hazelcast-gerrit-trigger
rules:
  - apiGroups: [""]
    resources: ["endpoints", "pods", "nodes", "services"]
    verbs: ["get", "list"]
  - apiGroups: ["discovery.k8s.io"]
    resources: ["endpointslices"]
    verbs: ["get", "list"]
```

#### Kubernetes — Client Mode with Separate Hazelcast Cluster

For larger deployments or strict separation of concerns, you can decouple the coordination layer by running a standalone Hazelcast cluster. Jenkins still connects as a lightweight client, but routes traffic to the separate cluster via a Kubernetes service instead of a sidecar.

Add the following JVM arguments to the Jenkins instance, updating the client address to point to your standalone Hazelcast Kubernetes service (replace hazelcast-service.default.svc.cluster.local with your actual service DNS and namespace, along with the cluster name for your logical instance):

    -Dgerrit.trigger.coordination.mode=hazelcast
    -Dgerrit.trigger.coordination.hazelcast.client.addresses=hazelcast-service.default.svc.cluster.local:5702
    -Dgerrit.trigger.coordination.hazelcast.client.cluster.name=gerrit-trigger-cluster-<LOGICAL_INSTANCE_NAME>

In this topology:
- You do not need to add the sidecar container to the Jenkins pod spec.
- The Jenkins service account does not need RBAC permissions for peer discovery, as cluster management is handled entirely by the standalone Hazelcast nodes.
- You must deploy and manage the Hazelcast cluster independently (e.g. via the official Hazelcast Helm chart), ensuring you configure it to match your expected `HZ_CLUSTERNAME` and port (`5702`).

## AMQP event delivery requirements

In a distributed (multi-replica) set up, Gerrit events are typically delivered to Jenkins via the
[RabbitMQ Consumer plugin](https://plugins.jenkins.io/rabbitmq-consumer/), which this plugin
integrates with via
[`RabbitMQMessageListenerImpl`](../src/main/java/com/sonyericsson/hudson/plugins/gerrit/trigger/impls/RabbitMQMessageListenerImpl.java).
That class reconstructs each event's Gerrit server identity entirely from AMQP message headers.

### The `gerrit-name` header is strictly required

Every AMQP message carrying a Gerrit event must include a `gerrit-name` header whose value matches
the name of a configured `GerritServer` in Jenkins. This is not optional in distributed mode:
[`PluginImpl#getServer(GerritTriggeredEvent)`](../src/main/java/com/sonyericsson/hudson/plugins/gerrit/trigger/PluginImpl.java)
resolves the event's `GerritServer` purely from this header, and if it is missing, empty, or does
not match a configured server name, resolution returns `null`. Only a warning is logged
(`Could not find server config for ... - no such server.`) — no exception is thrown and nothing is
surfaced to the operator.

Event-scoped processing that depends on identifying the originating Gerrit server - including
`BuildMemory#cancelOutdatedEvents()`, which aborts a job's previous, superseded-patchset build when
a new patchset event arrives - does not run once server resolution fails. A missing `gerrit-name`
header therefore causes outdated-build cancellation to silently do nothing: builds for
superseded patchsets are left running instead of being cancelled, and event deduplication breaks
in the same silent way.

Whatever component publishes Gerrit stream events onto the AMQP broker (e.g. a stream-events-to-AMQP
bridge) must be configured to set `gerrit-name` to the exact `GerritServer` name configured in
Jenkins for every message it publishes.

### The RabbitMQ Consumer plugin's compatibility with multi-replica deployments has not been verified

The distributed event-claiming model described above depends on **every** replica receiving **every** event
so that exactly one of them can claim it. The RabbitMQ Consumer plugin configures a single, fixed queue
name per consume item, with no way to bind a distinct queue per replica. Under standard AMQP
competing-consumer semantics, only one consumer would receive each message from that shared queue, which
would be expected to prevent every replica from seeing every event.

This has not been tested in distributed mode, so the actual behavior cannot be verified. Until it has
been, treat the RabbitMQ Consumer plugin as unproven for multi-replica set ups of this feature and
prefer a single Jenkins instance/replica.

## Configuration Options

Instances with a large number of jobs — including multi-replica deployments running under
this feature — can take longer than 30 minutes to finish loading job configurations on
startup. See
[Configuring the Startup Wait Timeout](README.adoc#_configuring_the_startup_wait_timeout)
in the main README for how to raise the `gerrit.trigger.playback.jobsLoadedGate.timeout.minutes`
system property above your instance's actual startup time, so missed-events catch-up doesn't
proceed before every replica's jobs have finished loading.

(*) Jenkins does not support multiple replicas or nodes for a single logical instance, this feature is not tested with Jenkins. This feature is provided for CloudBees CI (Enterprise Jenkins).
This feature is provided as a community effort and is not endorsed or officially supported by CloudBees.
