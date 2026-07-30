# Distributed Events: Topology, Locking, and Guarantees

This document summarizes the options, caveats, and trade-offs behind the Hazelcast
distributed-coordination design (cluster topology, cluster-formation waiting, and the
missed-events catch-up lock), as a reference for deciding future work. It complements
[`README_DISTRIBUTED_EVENT_MANAGEMENT.md`](README_DISTRIBUTED_EVENT_MANAGEMENT.md), which
covers configuration; this doc covers the reasoning behind the design.

## 1. Cluster topology options

| | Sidecar-per-replica (default, documented) | Separate/standalone Hazelcast cluster |
|---|---|---|
| **Description** | One Hazelcast server co-located in the same pod as each Jenkins replica, joining only its Jenkins-managed peers. | A Hazelcast cluster managed independently (e.g. Helm chart), reached over the network as a client. |
| **Cluster formation timing** | Bound to the pod lifecycle — sidecar discovery (single-digit seconds) is dwarfed by Jenkins' own boot time (JVM start, CasC, plugin loading — tens of seconds). Client's first connection reliably observes a fully-formed cluster. | Decoupled from any one replica's startup. Formation can still be in progress (scaling, rebalancing, network delay) independently of when a given Jenkins replica boots — not bounded by anything this plugin controls. |
| **`HAZELCAST_EXPECTED_MEMBERS_PROPERTY` relevance** | Not needed; default (1) disables the wait. | Relevant — set to the expected member count so `PluginImpl.start()` waits before opening Gerrit connections. |
| **CP Subsystem / FencedLock feasibility** | Not practical — minimal HA is 2 replicas, and CP Subsystem needs an odd number ≥ 3 dedicated members for Raft quorum. Enabling it would require an otherwise-unnecessary 3rd member. | Feasible — cluster size and membership are already decoupled from replica count, so a dedicated ≥3-member CP group is a config choice, not a topology constraint. |
| **Operational ownership** | Managed as part of the Jenkins pod spec (this plugin's docs cover it). | Managed independently by the operator; this plugin only builds a client config and cannot configure or enable CP Subsystem itself. |

**Current decision:** the sidecar topology is the supported default. Standalone-cluster support
exists in config (`README_DISTRIBUTED_EVENT_MANAGEMENT.md`) but CP Subsystem / FencedLock is not
yet implemented (see [§4](#4-deferred-fencedlock-support)).

## 2. Locking mechanisms for cross-replica coordination

All existing distributed locks in this plugin (`HazelcastBuildMemoryStorage`,
`HazelcastMissedEventsCoordinationStrategy`) use the same pattern: **AP (partition-owner) locks**
via `IMap.tryLock(...)`, not Hazelcast's CP Subsystem.

| | AP `IMap` lock (current) | CP Subsystem `FencedLock` (not implemented) |
|---|---|---|
| **Consistency model** | "Whoever the current partition owner thinks holds the lock" — a per-partition-view answer. | Raft-consensus, majority-quorum among dedicated CP members. A minority-side partition cannot acquire or renew the lock. |
| **Split-brain / forming-cluster behavior** | Two not-yet-joined members can each acquire "the lock" against their own still-separate partition view — both proceed independently. | Cannot happen by construction — lock acquisition requires majority quorum. |
| **Crash safety** | `leaseSeconds` — an explicit lease auto-releases the lock if the holder crashes mid-operation. No fencing token: a paused-then-resumed holder can still act after its lease expired, believing it holds the lock. | Lock liveness tied to a CP session TTL (cluster-wide config, not per-call). `getFence()` gives a strictly increasing token; the *protected resource* must reject writes from a stale fence — extra plumbing, not automatic. |
| **Infra requirement** | None beyond the existing Hazelcast client/cluster. | Requires `CPSubsystemConfig.setCPMemberCount(3\|5\|7)` configured server-side; this plugin's client cannot enable it. Incompatible with a 2-replica minimal sidecar deployment. |
| **Code shape** | `map.tryLock(key, wait, unit, lease, unit)` / `finally { map.unlock(key) }`. | `lock.tryLock(wait, unit)` / `finally { lock.unlock() }` — structurally almost identical; the watermark storage itself can stay a plain `IMap`. |
| **When it matters** | Fine as long as the cluster's partition table has already converged before the lock is used — true at cold start on the sidecar topology (see §3). | Only meaningfully improves things for a separately-managed cluster where convergence isn't guaranteed and ≥3 dedicated members are acceptable. |

**Bottom line:** AP locks are sound *given a converged cluster*. They are not a split-brain-proof
primitive in general — the guarantee currently comes from the topology (§3), not the lock itself.

## 3. Cluster-formation waiting (`waitForClusterFormation`)

`HazelcastCoordinationProvider.initialize()` calls `waitForClusterFormation()` before
`PluginImpl.start()` proceeds to open any Gerrit SSH connections — see
`HazelcastCoordinationProvider.java:304`.

**What it does:** polls `hz.getCluster().getMembers().size()` every 500ms (up to
`HAZELCAST_CLUSTER_WAIT_TIMEOUT_PROPERTY`, default 30s) until it reaches
`HAZELCAST_EXPECTED_MEMBERS_PROPERTY` (default 1, which disables the wait entirely).

**What it guarantees:** membership count, observed via this replica's own connected sidecar. It
does **not** observe or guarantee partition-table convergence — that's a separate, unexposed
internal state. Member count reached ≠ AP locks are now safe to use; it's a proxy that happens to
be good enough at cold start on the sidecar topology (see below), not a general proof.

**Scope — this is a cold-start-only guard:**

| Scenario | Covered? |
|---|---|
| Cold start, sidecar topology | Yes — sidecar formation (seconds) finishes well before Jenkins' own boot (tens of seconds), so the client observes a fully-formed cluster regardless of this wait. The wait is effectively a no-op safety net here. |
| Cold start, standalone/separate cluster | Partially — the wait narrows the window where a replica's first `connectionEstablished()` races the cluster's own formation, but reaching the expected member count is not the same as the partition table having converged. |
| Mid-life reconnect (any topology) | **Not covered at all.** `waitForClusterFormation()` runs once, in `PluginImpl.start()`. A later Gerrit SSH reconnect (network blip, pod restart) triggers `connectionEstablished()` again with no equivalent guard, even if the local sidecar happens to be re-merging with its peers at that same moment. |

**No client-side "recheck discovery" primitive exists.** Verified against the Hazelcast 5.3.8 jar:
`com.hazelcast.cluster.Cluster` exposes only `getMembers()` and `addMembershipListener(...)` — no
`refresh()`/`rediscover()`. The client only reflects what its one connected sidecar/server
currently reports; there is nothing to "ask again" independently. A `MembershipListener` would
make detection push-based (instant) instead of polled (up to 500ms latency) and a
`LifecycleListener` could distinguish "genuinely 1 member" from "my own connection just dropped" —
useful latency/staleness improvements, but neither closes the mid-life-reconnect gap above.

## 4. Missed-events catch-up coordination

`MissedEventsCoordinationStrategy.coordinateCatchUp(...)` (`spi/MissedEventsCoordinationStrategy.java`)
runs on every `connectionEstablished()` — cold start *and* every later reconnect — and must
guarantee two properties, not just approximate them via lock timing:

1. **Mutual exclusion** — if the lock can't be acquired, return `LOCK_TIMEOUT` and do not fetch.
   Never fall back to an uncoordinated fetch.
2. **No redundant re-fetch** — after acquiring the lock, compare against the shared watermark;
   skip (`ALREADY_CAUGHT_UP`) if it's already ahead.

`HazelcastMissedEventsCoordinationStrategy` implements this with a single `IMap<String, Long>`
(keyed by server name) used as both the lock and the watermark store — lock → check watermark →
fetch only if behind → write new watermark → unlock, mirroring `HazelcastBuildMemoryStorage`'s
existing pattern. `LocalMissedEventsCoordinationStrategy` is the single-JVM equivalent (a plain
`ReentrantLock` + in-memory watermark map — no lease needed, since there's no cross-JVM
crash-without-release case in a single process).

**This design is sound for split-brain double-fetch prevention, given a converged cluster** — i.e.
given the sidecar topology's cold-start guarantee from §3. It is an AP lock, so it inherits every
caveat in §2.

### Known, accepted risks (not blockers — tracked here for visibility)

- **Boot-time catch-up vs. job-loading race (fixed).** `fetchAndTriggerMissedEvents` calls
  `server.triggerEvent(evt)` and unconditionally marks an event delivered with no confirmation any
  `GerritTrigger` listener actually received it. Since `GerritServer#startConnection()` fires as
  early as `InitMilestone.PLUGINS_STARTED` (needed so `GerritTrigger#start()` can register a
  listener as each job loads), the very first `connectionEstablished()` of a JVM's lifetime could
  replay a missed event before that job's own trigger had registered — silently dropping it with
  no retry, confirmed live (gerrit-demo-with-replica's HZ-021, both mc0 and mc1, ~150ms before
  `InitMilestone.JOB_CONFIG_ADAPTED`). Not distributed/Hazelcast-specific — this code path is
  identical regardless of coordination mode. Fixed via a one-shot `JobsLoadedGate`
  (`playback/JobsLoadedGate.java`) that `connectionEstablished()` waits on first, opened by a new
  `PluginImpl.gerritJobsLoaded()` hook at `InitMilestone.JOB_CONFIG_ADAPTED` (not `COMPLETED` —
  that milestone is documented as reserved for `Initializer#before()`, and using it as an `after`
  bound broke every `JenkinsRule`-based test in this plugin's own suite, JENKINS-37759). The gate
  is process-lifetime and one-shot, so it only guards a JVM's very first catch-up — a related but
  distinct trigger condition (a CasC job-reconciliation sweep tearing down and rebuilding a
  trigger well after boot) is not covered by this fix; see
  `gerrit-demo-with-replica/values/test-resources/CASC_RECONCILIATION_DROPPED_EVENTS.md` for the
  full writeup of both.
- **Mid-life reconnect race (§3).** Outside Jenkins' own boot sequence, a sidecar restart
  coinciding with a Gerrit SSH reconnect could still race cluster convergence. Rare — it requires
  two independent events to coincide — but possible. No mitigation currently exists beyond what §3
  describes.
- **Lease vs. long catch-up.** The lock's default lease is 300s, documented as a crash-only safety
  net "assumed to never fire in the normal path." The feature's own motivating scenario — every
  replica simultaneously unable to process events (a full outage) — is exactly the case most
  likely to produce a backlog whose `fetchAndTrigger(...)` call runs longer than 5 minutes, which
  would let the lease expire mid-fetch and break exclusion in the scenario this mechanism exists to
  protect. Configurable via `LOCK_LEASE_PROPERTY`; not auto-tuned to backlog size.
- **Downstream safety net.** Even if either risk above materializes and a duplicate catch-up
  fetch slips through, every event still passes through the existing per-event
  `EventClaimStrategy` before it can start a duplicate build. So the residual exposure is redundant
  REST calls to the events-log plugin (a cost problem), not duplicate builds (a correctness
  problem) — assuming replay-parsed and live-parsed events produce identical claim keys (not
  independently verified here).

## 5. Deferred: FencedLock support

The missed-events lock is not the only AP `IMap` lock in this codebase — every one of them shares
the same §2 caveat (safe only given a converged partition table) and would benefit from the same
CP Subsystem swap in principle. Scoping the actual candidates separately, since they differ enough
in shape and call frequency to matter for sizing:

### 5.1 `HazelcastMissedEventsCoordinationStrategy` (covered above)

Already scoped in detail above — a single call site, watermark-plus-lock on one `IMap`, an
explicit lease. This is the smallest, most self-contained candidate.

### 5.2 `HazelcastBuildMemoryStorage` (`withLock`/`tryLockWithTimeout`)

This is the plugin's highest-frequency lock: every build lifecycle transition (triggered, started,
completed, cancel-finalize, etc. — ~10 call sites at `HazelcastBuildMemoryStorage.java:453,533,600,
680,738,825,895,952,1057,1202,1245`) goes through the same two centralized private helpers
(`tryLockWithTimeout`, `withLock`). That centralization is good news for a future swap — the
primitive change is contained to one place — but two things make it a bigger lift than §5.1:

- **No lease at all.** `map.tryLock(key, timeout, unit)` here has no lease parameter (unlike the
  missed-events lock's explicit `leaseSeconds`). If a holder crashes mid-operation, the current
  behavior on lock release/recovery for an abandoned AP lock has not been verified in this codebase
  — worth confirming before assuming a lease-based CP session TTL is a drop-in equivalent.
- **Call frequency and blast radius.** This lock guards every build-state read-modify-write, not
  one periodic catch-up call. A CP session TTL misconfiguration or quorum hiccup here would affect
  every build transition on every replica, not just a rare reconnect path — the failure mode is
  more exposed than in §5.1.

Sizing: similar shape to §5.1 (swap the primitive behind the two existing helpers, add the same
kind of selection property), but the lease-semantics verification and blast-radius analysis above
should happen before treating it as a mechanical copy of the missed-events change.

### 5.3 Event/notification claim strategies — out of scope for a FencedLock swap

`HazelcastEventClaimStrategy` and `HazelcastNotificationClaimStrategy` do **not** use a lock at
all — they claim via a single atomic `IMap.putIfAbsent(...)` call (`HazelcastEventClaimStrategy.java:162`,
`HazelcastNotificationClaimStrategy.java:136`). They share the same underlying AP-consistency
caveat (a still-forming/split cluster can accept two "first" claims from two divergent partition
views), but there is no equivalent CP-backed map primitive to swap in: checked against the same
Hazelcast 5.3.8 `CPSubsystem` interface used above, it exposes only `getAtomicLong`,
`getAtomicReference`, `getCountDownLatch`, `getLock` (`FencedLock`), and `getSemaphore` — no
CP-backed map/`putIfAbsent` equivalent. Closing this gap would mean redesigning claims around a
`FencedLock` + `IAtomicReference` per key (or similar), which is a materially larger, more
invasive change than swapping an existing lock call — not a natural extension of the §5.1/§5.2
work, and not scoped further here.

### Common prerequisites (all of §5.1/§5.2)

1. CP Subsystem must be enabled server-side (`CPSubsystemConfig.setCPMemberCount(3|5|7)`); this
   plugin's client cannot enable or verify it. Selecting a CP-backed option against a cluster
   without it configured would fail at first use — likely during live traffic, not at plugin
   startup. Needs a fail-fast check at `initialize()` time with a documented fallback-vs-fail-closed
   decision.
2. A selection property per strategy (e.g. `...missedevents.lock.type`, default `imap`) and a
   branch in `HazelcastCoordinationProvider`.
3. Tests against an embedded Hazelcast instance with CP Subsystem enabled (a CP group of size 1 is
   sufficient to exercise lock semantics without real fault tolerance).
4. A docs subsection noting this only applies to a separately-managed, ≥3-CP-member cluster (§1).

**Net assessment:** §5.1 is small-to-medium and additive. §5.2 is the same shape but carries a
larger blast radius and an unverified crash-recovery assumption to close first. §5.3 is a different
kind of change entirely and shouldn't be bundled with the other two.
