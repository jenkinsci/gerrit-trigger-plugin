# Distributed Event Management support

The plugin supports Distributed Event Management support where two or more replicas of a Jenkins(*) instance
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

Port `5702` is used by default to avoid potential conflicts with other Hazelcast cluster, which could occupy port `5701`.

Cluster name must be different for each logical instance. Multiple replicas of a single instance may connect to the same cluster name.

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

Add the following JVM arguments to the Jenkins instance, updating the client address to point to your standalone Hazelcast Kubernetes service (replace hazelcast-service.default.svc.cluster.local with your actual service DNS and namespace):

    -Dgerrit.trigger.coordination.mode=hazelcast
    -Dgerrit.trigger.coordination.hazelcast.client.addresses=hazelcast-service.default.svc.cluster.local:5702
    -Dgerrit.trigger.coordination.hazelcast.client.cluster.name=gerrit-trigger-cluster

In this topology:
- You do not need to add the sidecar container to the Jenkins pod spec.
- The Jenkins service account does not need RBAC permissions for peer discovery, as cluster management is handled entirely by the standalone Hazelcast nodes.
- You must deploy and manage the Hazelcast cluster independently (e.g. via the official Hazelcast Helm chart), ensuring you configure it to match your expected `HZ_CLUSTERNAME` and port (`5702`).

(*) Jenkins does not support multiple replicas for a single logical instance, this feature is not tested with Jenkins. This feature is provided for CloudBees CI (Enterprise Jenkins).
This feature is provided as a community effort and is not endorsed or officially supported by CloudBees.
