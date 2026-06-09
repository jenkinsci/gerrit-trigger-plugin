# Gerrit Trigger Plugin

This plugin triggers builds on events from the Gerrit code review system by
retrieving events from the Gerrit command "stream-events", so the trigger is
pushed from Gerrit instead of pulled as scm-triggers usually are.

Various types of events can trigger a build, multiple builds can be triggered
by one event, and one consolidated report is sent back to Gerrit.

Multiple Gerrit server connections can be established per Jenkins instance.
Each job can be configured with one Gerrit server.


## Maintainers

* Robert Sandell
  - robert.sandell@cloudbees.com
  - sandell.robert@gmail.com

* Tomas Westling
  - tomas.westling@axis.com

## Community Resources
 * [Homepage and documentation](https://plugins.jenkins.io/gerrit-trigger)
 * [Open Issues](http://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27gerrit-trigger-plugin%27)
 * [Mailing Lists](http://jenkins-ci.org/content/mailing-lists)


# Environments

The maintainers' development, tests and production environments are
Ubuntu so we have no means of detecting or fixing any Windows issues.


# Build

The plugin depends on a [gerrit-events](https://github.com/sonyxperiadev/gerrit-events) component
that used to be part of this project but later broken out. Although we will try to avoid it,
sometimes you might need to _mvn install_ it locally if dependant changes there haven't been released yet.

The _(build-config)_ directory contains "special" CheckStyle configurations and the build will
fail during the verification phase if you don't follow them.

    mvn clean package

Run findbugs for future reference or to make sure you haven't introduced any
new warnings

    mvn findbugs:findbugs

Run checkstyle

    mvn checkstyle:checkstyle

# High Availability / High Scalability (HA/HS) Support

The plugin supports active/active HA/HS deployments where two or more Jenkins replicas
run in parallel. When enabled, a Hazelcast cluster coordinates the replicas so that:

- Each Gerrit event is processed by **exactly one** replica (event claiming)
- Build state is shared across replicas (distributed build memory)
- Gerrit feedback (votes and comments) is sent **exactly once** per build event

By default the plugin runs in **local mode** and requires no additional configuration.
Local mode is fully backward-compatible with single-instance Jenkins deployments.

Hazelcast mode is activated via a JVM system property. The plugin auto-discovers
cluster peers using Kubernetes service discovery (when running in Kubernetes) or a
static TCP/IP member list.


## Configuration Properties

All HA/HS settings are controlled by JVM system properties passed to Jenkins on startup.

| Property | Default | Description |
|---|---|---|
| `gerrit.trigger.coordination.mode` | `local` | Set to `hazelcast` to enable HA/HS coordination |
| `gerrit.trigger.coordination.hazelcast.instance.mode` | `member` | `member` (embedded cluster member) or `client` (connect to a Hazelcast sidecar container) |
| `gerrit.trigger.coordination.hazelcast.client.addresses` | `localhost:5702` | Comma-separated `host:port` list of sidecar addresses (client mode only) |
| `gerrit.trigger.coordination.hazelcast.client.cluster.name` | `gerrit-trigger-cluster` | Cluster name to connect to in client mode |
| `gerrit.trigger.coordination.hazelcast.cluster.name` | `gerrit-trigger-cluster` | Cluster name used when running as an embedded member |
| `gerrit.trigger.coordination.hazelcast.port` | `5702` | Hazelcast member port (member mode only) |
| `gerrit.trigger.coordination.hazelcast.port.count` | `10` | Number of ports to try when auto-incrementing |
| `gerrit.trigger.coordination.hazelcast.discovery.mode` | `auto` | Discovery mechanism: `kubernetes`, `tcp`, or `multicast` (testing only) |
| `gerrit.trigger.coordination.hazelcast.k8s.service.name` | `jenkins` | Kubernetes service name used for peer discovery |
| `gerrit.trigger.coordination.hazelcast.k8s.namespace` | `default` | Kubernetes namespace for peer discovery |
| `gerrit.trigger.coordination.hazelcast.tcp.members` | — | Static member list for TCP discovery, e.g. `replica-0.jenkins:5702,replica-1.jenkins:5702` |
| `gerrit.trigger.coordination.hazelcast.operation.timeout` | `30000` | Distributed operation timeout in milliseconds |

Port `5702` is used by default to avoid conflicts with CloudBees Core CI's internal
Hazelcast cluster, which occupies port `5701`.


## Configuration Examples

### Kubernetes — Client Mode with Hazelcast Sidecar (Recommended)

In Kubernetes HA/HS deployments the recommended setup runs a Hazelcast container as a
sidecar in each Jenkins pod. The plugin connects to it as a lightweight client, reusing
the cross-pod cluster the sidecar maintains.

Add the following JVM arguments to the Jenkins controller:

    -Dgerrit.trigger.coordination.mode=hazelcast
    -Dgerrit.trigger.coordination.hazelcast.instance.mode=client
    -Dgerrit.trigger.coordination.hazelcast.client.addresses=localhost:5702
    -Dgerrit.trigger.coordination.hazelcast.client.cluster.name=gerrit-trigger-cluster

Add the sidecar container to the controller pod spec:

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


### TCP/IP — Static Member List (Non-Kubernetes HA)

For HA deployments outside Kubernetes, configure a static list of member addresses:

    -Dgerrit.trigger.coordination.mode=hazelcast
    -Dgerrit.trigger.coordination.hazelcast.discovery.mode=tcp
    -Dgerrit.trigger.coordination.hazelcast.tcp.members=replica-0.jenkins:5702,replica-1.jenkins:5702


# License

    The MIT License

    Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
    Copyright 2012 Sony Mobile Communications AB. All rights reserved.

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.
