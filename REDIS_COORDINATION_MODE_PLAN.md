# Redis Coordination Mode — Implementation Plan & Spec

Status: approved plan, not yet started.
Base branch: `distributed-storage`.
Author of decisions: rsandell, 2026-07-28. This document is self-contained: it embeds the
relevant findings about the existing Hazelcast implementation and SPI so an implementing
agent does not need to re-explore before starting (verify against the current branch state
though — see "Sync points").

---

## 1. Goal and target scenario

Add a new coordination mode `redis` alongside `local` and `hazelcast`, selected via
`-Dgerrit.trigger.coordination.mode=redis`, implemented as a new
`CoordinationModeProvider` extension. Redis (a single server the controllers can reach)
is the coordination medium.

**The target deployment is pure OSS and differs from Hazelcast's target:**

- The Hazelcast mode is tuned for CloudBees HA/HS: replicas share `JENKINS_HOME`, share
  the Jenkins URL (ingress), run in k8s (unique pod hostnames), and a load balancer can
  transfer queue items between replicas.
- The Redis mode targets **independent controllers** with identical configuration via
  JCasC + job generation, separate `JENKINS_HOME`s, possibly on the same bare-metal host,
  that know nothing about each other except Gerrit Trigger coordination.
- **Sticky event ownership, no queue transfers**: the controller that claims an event
  triggers and owns its builds until finished. Queue items never migrate.

Consequences (see §4 for what this removes vs. keeps).

## 2. Hard constraints and decisions (do not re-litigate)

1. **Client library: Jedis** (`redis.clients:jedis`), compile scope, exactly like
   `com.hazelcast:hazelcast` today. Synchronous API matches the existing synchronous
   storage code; `JedisPool` for the request path; one dedicated thread for pub/sub.
2. **All Redis tests are JUnit 5** (Jupiter). The Hazelcast tests are JUnit 4 and will be
   migrated separately later — do not migrate them as part of this work, and do not
   share JUnit 4 base classes.
3. **Do not change Hazelcast mode behavior.** In particular `HazelcastEventClaimStrategy`
   keeps plain `HOSTNAME` as instance id (sufficient in k8s; validated against HA/HS).
4. **Instance ID for Redis mode**: composed `<hostname>-<first 8 hex chars of Jenkins
   instance-identity hash>` (unique in both HA/HS and same-host-OSS topologies; note
   HA/HS replicas share JENKINS_HOME so identity alone is NOT unique, and hostname alone
   is not unique on shared bare metal). Overridable via system property
   `gerrit.trigger.coordination.instance.id`; random UUID fallback when Jenkins is
   unavailable (plain unit tests).
5. **Credentials in the Redis URL are deferred.** The agreed future design: in
   `redis://<userInfo>@host`, userInfo *without* a colon is never valid literal Redis
   auth (literal is always `user:pass` or `:pass`), so a colon-less userInfo will later
   be interpreted as a Jenkins credentials ID. For now: implement `RedisConfig` with a
   single `resolveUrl()` seam through which the raw property value passes, and document
   in its javadoc that system-property credentials are visible in Jenkins system info.
6. **Fail-open on Redis unavailability for claim strategies** (run the action anyway),
   mirroring Hazelcast — availability over consistency is a deliberate choice.
   Storage mutations on lock/connection failure: skip and log (also mirrors Hazelcast).
7. **Missed-events playback work is in flight** on `distributed-storage` and may add SPI
   surface. See "Sync points" (§8).

## 3. Existing architecture (reference; verified 2026-07-28)

### 3.1 SPI (`com.sonyericsson.hudson.plugins.gerrit.trigger.spi`)

- `CoordinationModeProvider` (ExtensionPoint): `isAvailable()`, `getModeName()`,
  `createStorage()`, `createClaimStrategy()`, `createEventClaimStrategy()`,
  `createQueueCancellationStrategy()`, `initialize() throws Exception`, `shutdown()`.
  Static helper `getConfiguredMode()` reads `gerrit.trigger.coordination.mode`
  (default `local`). Selection: `coordination.CoordinationMode` picks the first
  *available* provider by `@Extension(ordinal)` desc. `PluginImpl.start()` calls
  `initialize()` on non-local providers before selection (each provider must internally
  no-op unless the configured mode matches it), falls back to local on failure;
  `PluginImpl.stop()` calls `shutdown()` on all providers. **PluginImpl needs no changes
  for a new mode.**
- `BuildMemoryStorage` (abstract): lifecycle mutators `triggered/started/completed/
  retriggered/cancelled/setCancelling/forget/removeProject/setEntryCustomUrl/
  setEntryUnsuccessfulMessage`; queries `getMemoryImprint/isAllBuildsCompleted/
  isAllBuildsStarted/getBuildsStartedStats/getStatusReport/isTriggered/isBuilding(x2)/
  getBuilds/report/getAllEvents`; coordination hooks `requestCrossReplicaAbort(event,
  job, CauseOfInterruption)` (default no-op) and abstract `eventsMatch(e1, e2)`.
  No TTL in the SPI — cleanup is explicit via `forget()`. `getAllEvents()`/`report()`
  must return snapshots. **`BuildMemory.cancelOutdatedEvents` wraps
  `synchronized (storage)` around getAllEvents → eventsMatch → setCancelling — the
  storage instance is used as an external monitor; implementations must tolerate this
  and still provide their own cross-replica atomicity inside each method.**
- `NotificationClaimStrategy`: `withClaim(event, notificationType, jobIdentifier?,
  Runnable)` — claim key is `(event, type, jobIdentifier?)`; jobIdentifier null =
  per-event (aggregated build-completed), non-null = per-job (build-started).
  Acquire + run + auto-release; returns `ClaimResult`.
- `EventClaimStrategy`: `withClaim(event, Runnable)` — key is the event alone.
- `ClaimResult`/`ClaimResults`: result-handler chaining (`notClaimed(Runnable)`,
  `onError(Consumer<Exception>)`); outcomes `success()` / `notClaimed()` / `failed(e)`
  are mutually exclusive; `failed` does NOT trigger the notClaimed handler.
- `QueueCancellationStrategy`: `isLoadBalancedCancellation(Queue.LeftItem)`.

### 3.2 Serialization boundary

`MemoryImprint`/`Entry` convert to DTOs `MemoryImprintData`/`EntryData`
(`gerritnotifier/model/`) via `toData()/fromData()` — string identifiers only
(`Job.getFullName()`, `Run.getId()`), flags (`buildCompleted, cancelling, cancelled,
queueLeft`), `customUrl`, `unsuccessfulMessage`, timestamps. Live `Job`/`Run` are
re-resolved locally. The event itself is serialized to JSON with Gson via
`PolymorphicEventTypeAdapter` (wraps as `{"@type": className, "data": {...}}`) —
currently in the hazelcast package but pure Gson. Gson comes from the `gson-api`
plugin dependency (gerrit-events excludes its own gson).

### 3.3 Hazelcast mode (the template), key facts

- Client-to-sidecar only (`HazelcastClient.newHazelcastClient`), no embedded member,
  no EntryProcessors (sidecar lacks plugin classes). String map keys via
  `EventIdGenerator.generateEventId(event)` (deterministic across replicas, includes
  Gerrit server timestamp; change-based: `change-{project}-{changeId|num-N}-{branch}-
  {patchset}-{eventType}-{timestamp}`).
- `HazelcastBuildMemoryStorage`: map `gerrit-trigger-build-memory`
  (String → MemoryImprintData, no TTL); per-key `IMap.tryLock(key, 10s)` around
  read-modify-put; abort inbox map `gerrit-trigger-abort-inbox`
  (key `jobFullName:buildNumber`, value `NEW_PATCHSET|ABANDONED`, TTL 60s) with an
  `EntryAddedListener` per replica whose handler interrupts matching executors
  (with pipeline-not-yet-started polling via `PipelineAbortHelper`, 250ms × 12).
  Race compensators: deferred cross-replica abort 3s after `started()` when entry
  already `isCancelling`; deferred cancel-finalize 10s grace after `cancelled()`
  (queue-transfer disambiguation). `eventsMatch` = EventIdGenerator id equality.
- `HazelcastEventClaimStrategy`: map `gerrit-trigger-event-claims`, `putIfAbsent(id,
  EventClaim, 300s)`; on lost race, GET and compare `claimedBy` == own id (env
  `HOSTNAME` fallback `InetAddress.getLocalHost().getHostName()`, cached) — replica-level
  claim: all jobs on the claiming replica process. TTL property
  `gerrit.trigger.coordination.hazelcast.claim.ttl.seconds`. Fail-open.
- `HazelcastNotificationClaimStrategy`: map `gerrit-trigger-notification-flags`,
  `putIfAbsent(flagKey, TRUE, 10min)`; key `notified-{type}-{eventId}[-{jobIdentifier}]`.
  TTL property `...notification.ttl.minutes`. Fail-open.
- `HazelcastQueueCancellationStrategy`: returns `false` unconditionally (uses no
  Hazelcast).
- Config properties: `...hazelcast.client.addresses` (default `localhost:5702`),
  `...hazelcast.client.cluster.name`, `...hazelcast.expected.members`,
  `...hazelcast.cluster.wait.timeout.seconds`.
- Tests: `EmbeddedHazelcastTestServer` (real member in test JVM, random free port),
  `HazelcastServerTestListener` (JUnit *Platform* `TestExecutionListener`, registered in
  `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`,
  starts server + sets the address property when mode==hazelcast),
  `HazelcastTestRule` (JUnit 4, assume-skip unless profile), default-run
  `HazelcastCoordinationSmokeTest` (@ClassRule server + @LocalData + SshdServerMock),
  opt-in `BuildCancellationHazelcastIntegrationTest` (3 cancellation-race tests).
  Maven profile `test-hazelcast`: `reuseForks=false, forkCount=1`, sets the mode property.

### 3.4 Known baseline issue

`TopicAssociationTriggerTest.testTopicAssociationTrigger` fails deterministically under
`-Ptest-hazelcast` on `distributed-storage` (NPE: triggered job never built — event
claiming interferes with the test setup). **Pre-existing, not a regression signal.**
Expect the same under `-Ptest-redis`; if so, note it and move on — fixing it is a
separate task. Full profile suite runtime is ~29 min locally.

## 4. Redis design

### 4.1 Primitive mapping

| Concern | Redis form |
|---|---|
| Build memory | `gerrit-trigger:build-memory:<eventId>` → JSON `MemoryImprintData` (Gson; event via `PolymorphicEventTypeAdapter`, DTO fields plain Gson — **no custom serializer classes needed**) |
| Per-event mutation lock | `SET gerrit-trigger:lock:<eventId> <token> NX PX 10000`; release via Lua compare-token-and-DEL; on lock failure skip-and-log (mirror Hazelcast `LockOutcome`) |
| Event claim | `SET gerrit-trigger:event-claim:<eventId> <json EventClaim> NX EX <ttl>`; on NX failure `GET` and compare claimedBy to own instance id; default TTL 300 s, property `gerrit.trigger.coordination.redis.claim.ttl.seconds` |
| Notification flag | `SET gerrit-trigger:notification:<flagKey> 1 NX EX <ttl>`; same key shape as Hazelcast (`notified-{type}-{eventId}[-{job}]`); default 600 s, property `...redis.notification.ttl.minutes` |
| Cross-replica abort | `PUBLISH gerrit-trigger:abort-requests <json {buildKey, cause}>` + per-controller subscriber thread invoking the shared abort handler. Optionally also `SET gerrit-trigger:abort-inbox:<buildKey> <cause> EX 60` as a reconnect-poll backup (parity+: Hazelcast listeners also miss events while disconnected) |
| Enumeration (`getAllEvents`, `report`) | `SCAN MATCH gerrit-trigger:build-memory:*` + `MGET`. Start with SCAN; add a maintained index SET only if profiling demands (cancelOutdatedEvents runs per change-based event) |
| `eventsMatch` | `EventIdGenerator` id equality (identical to Hazelcast) |

Key prefix `gerrit-trigger:` must be overridable (property
`gerrit.trigger.coordination.redis.key.prefix`) for shared Redis instances.

### 4.2 Sticky-ownership simplifications (deliberate deviations from Hazelcast)

REMOVE (queue-transfer artifacts — document each omission in javadoc):
- the 10 s deferred cancel-finalize grace after `cancelled()`;
- `queueLeft`-as-ambiguous handling — with no load balancer, queue-left on the owner is
  a real cancellation; finalize immediately like local mode.

KEEP (not queue-transfer artifacts):
- the 3 s deferred abort in `started()` when the entry is already `cancelling`
  (controller B can claim PS2 and mark A's PS1 event cancelling while A's builds are
  still queued);
- the cross-replica abort channel + handler;
- `RedisQueueCancellationStrategy` returns `false` unconditionally (same as Hazelcast —
  consider one shared no-op class instead of a third copy).

ADD (Redis-specific): a **safety TTL** on build-memory keys (default 24 h, refreshed on
every write, property `gerrit.trigger.coordination.redis.buildmemory.ttl.hours`).
Rationale: Hazelcast sidecar state dies with the pod; Redis persists, so entries missed
by `forget()` would accumulate forever.

### 4.3 Configuration properties (new)

- `gerrit.trigger.coordination.redis.url` — single `redis://` / `rediss://` URI
  (host, port, db, literal auth, TLS). Passes through `RedisConfig.resolveUrl()` (the
  future credential-ID seam). Default `redis://localhost:6379`.
- `gerrit.trigger.coordination.redis.connection.timeout.millis` (default: Jedis default).
- `gerrit.trigger.coordination.redis.key.prefix` (default `gerrit-trigger:`).
- `gerrit.trigger.coordination.instance.id` (mode-agnostic override, see §2.4).
- TTL properties listed in §4.1/§4.2.

## 5. Work plan (phases = agent-assignable work packages)

### Phase 0 — shared extraction (PREREQUISITE for all others)

Move out of `coordination/hazelcast/` into a shared package
(suggested: `coordination/shared/`), updating imports; **behavior-identical, no logic
changes**:
1. `EventIdGenerator` (the SPI javadoc references it; both modes must produce identical
   ids or cross-mode semantics diverge).
2. `PolymorphicEventTypeAdapter` (pure Gson).
3. The abort-request handler out of `HazelcastBuildMemoryStorage`: executor-interrupt +
   `PipelineAbortHelper` polling logic, parameterized so both the Hazelcast entry-listener
   and the future Redis subscriber can call it.
4. NEW `CoordinationInstanceId` helper: `get()` returns, in order — the
   `gerrit.trigger.coordination.instance.id` property if set; else
   `<hostname>-<first 8 hex of SHA-256 of the Jenkins instance identity public key>`
   (use `org.jenkinsci.main.modules.instance_identity.InstanceIdentity` or
   `Jenkins.get().getLegacyInstanceId()` — verify which is available at the 2.479.3
   baseline); else (no Jenkins) a cached random UUID. **Do NOT wire it into
   `HazelcastEventClaimStrategy`** — Hazelcast keeps plain HOSTNAME (constraint §2.3).
Acceptance: full `mvn clean verify` green; `-Ptest-hazelcast` shows no *new* failures
vs. the §3.4 baseline; git history shows moves (git mv), not delete+add.

### Phase 1 — connection infrastructure + provider

New files in `coordination/redis/`:
- `RedisConfig` — properties from §4.3, `resolveUrl()` seam, javadoc security note.
- `RedisConnectionManager` — static idempotent `initialize()` (build `JedisPool` from
  config, validate with `PING`), `shutdown()`, `isInitialized()`, same volatile+lock
  pattern as `HazelcastManager`.
- `RedisConnectionHolder` — static holder mirroring `HazelcastInstanceProvider`
  (`setPool` throws if set, `getPoolOrThrow`, `clear`).
- `RedisCoordinationProvider extends CoordinationModeProvider`,
  `@Extension(ordinal = 100)`: `isAvailable()` = configured mode `redis` AND manager
  initialized; `initialize()` no-ops unless mode is redis (CRITICAL — it is called on
  every non-local provider regardless of mode); `getModeName()` = `"Redis (Distributed)"`;
  `shutdown()` closes pool and stops the subscriber thread (Phase 3). `create*()` methods
  can stub-throw until Phases 2–3 land if the phases are split across agents, but the
  branch must compile at every merge.
Acceptance: unit tests for config parsing + provider availability gating (no Jenkins
needed for config; use JenkinsRule/@WithJenkins only where extension lookup is tested).

### Phase 2 — claim strategies (depends on Phase 0 for instance id, Phase 1 for pool)

- `RedisEventClaimStrategy`: §4.1 semantics; `EventClaim`-equivalent JSON payload
  {eventId, claimedBy, claimedAt, eventType}; instance id from `CoordinationInstanceId`;
  fail-open on `JedisException`.
- `RedisNotificationClaimStrategy`: §4.1 semantics; fail-open.
- Queue cancellation: shared no-op (or trivial `RedisQueueCancellationStrategy`).
Acceptance: JUnit 5 unit tests against the embedded test server (Phase 4 items 1–2 are a
dependency — coordinate, or start those first): claim won/lost/same-instance-rerun,
TTL expiry, fail-open when server stopped, two distinct instance ids on one host.

### Phase 3 — RedisBuildMemoryStorage (SYNC POINT — see §8 before starting)

- All SPI methods; mutators = lock → GET → `MemoryImprint.fromData` → mutate →
  `toData` → SET (with safety TTL) → unlock; keep methods `synchronized` locally as well
  (external-monitor contract, §3.1).
- Gson instance configured with `PolymorphicEventTypeAdapter` for the event field.
- `requestCrossReplicaAbort()` publishes abort messages; subscriber thread (daemon,
  started by provider `initialize()`, stopped by `shutdown()`, reconnect loop with
  backoff) dispatches to the shared abort handler; deferred-abort compensator in
  `started()` kept; §4.2 simplifications applied and documented.
- `eventsMatch` via `EventIdGenerator`.
Acceptance: JUnit 5 unit tests against embedded server for every SPI method incl.
snapshot semantics of `getAllEvents`/`report`, lock-timeout skip path, TTL refresh.

### Phase 4 — test infrastructure (JUnit 5; items 1–2 can start right after Phase 1)

1. Test dependency `com.github.codemonstur:embedded-redis` (test scope);
   `EmbeddedRedisTestServer` on a random free port (mirror the Hazelcast one incl.
   static/idempotent start/stop).
2. `RedisServerTestListener` (JUnit Platform `TestExecutionListener`; add a line to the
   existing `META-INF/services/org.junit.platform.launcher.TestExecutionListener` file):
   when configured mode is `redis`, start server and set
   `gerrit.trigger.coordination.redis.url` to the chosen port.
3. `RedisTestExtension` (JUnit 5 `BeforeEach/AfterEachCallback`): `Assumptions.assumeTrue`
   the JVM started with mode=redis (i.e. `-Ptest-redis`), init manager if needed with
   teardown tracking, `FLUSHDB` between tests.
4. `RedisCoordinationSmokeTest` — **default-run** (no profile), JUnit 5 `@WithJenkins`:
   class-level embedded server + property setup *before* Jenkins boots (mirror the
   ClassRule-ordering trick with `@BeforeAll`), `@LocalData` gerrit config + SshdServerMock;
   assert `RedisBuildMemoryStorage` selected, mode name, one `PatchsetCreated` →
   triggered build completes. Note: smoke-test stream-events waits were recently raised
   to 20 s for busy CI agents — copy current values from `HazelcastCoordinationSmokeTest`.
5. Cancellation integration tests: extract the *scenario logic* of
   `BuildCancellationHazelcastIntegrationTest` (PS2-aborts-PS1, abandoned-aborts,
   third abort scenario) into a framework-agnostic helper class (plain methods taking
   JenkinsRule + mock server handles); write `BuildCancellationRedisIntegrationTest`
   (JUnit 5, opt-in via the extension) using it. Do NOT convert the Hazelcast test to
   JUnit 5 here; it may adopt the helper as-is from JUnit 4.
6. Maven profile `test-redis`: clone of `test-hazelcast`
   (`reuseForks=false, forkCount=1`, `gerrit.trigger.coordination.mode=redis`).

### Phase 5 — docs + verification gate

- Property documentation alongside the Hazelcast properties (README/docs — find where
  those are documented and match).
- Gate: `mvn clean verify` AND `mvn test -Ptest-hazelcast` AND `mvn test -Ptest-redis`
  all green except the §3.4 baseline failure (which must be reported explicitly, with
  evidence it also fails without the new code if it appears under test-redis).

### Phase 6 — floating: adopt missed-events-playback SPI additions

When the playback work lands on `distributed-storage` and adds abstract methods to
`CoordinationModeProvider` (or new strategy interfaces), implement them for Redis,
mirroring the Local/Hazelcast reference implementations. The compiler enforces this —
`RedisCoordinationProvider` will not build until done. Suggestion to carry upstream:
playback should reuse the event-claim strategy rather than invent a parallel mechanism.

## 6. Dividing the work across agents

Dependency graph:

```
Phase 0 ──► Phase 2 ──► Phase 4.4/4.5 ──► Phase 5
   │            ▲
   └──► Phase 1 ┤
        │       └── Phase 3 (after sync point) ──► Phase 4.4/4.5
        └──► Phase 4.1–4.3, 4.6
```

Suggested split (3 agents + a reviewer, or run sequentially in one session):

| Agent | Work packages | Needed capabilities |
|---|---|---|
| A: refactorer | Phase 0 | Read/Write/Edit + git (must use `git mv`); Bash with JDK 21 + Maven and **long-running command support (~30 min)** for the `-Ptest-hazelcast` gate; careful behavior-preservation discipline. Runs FIRST, alone. |
| B: redis-core | Phases 1, 2, 3 | Same tooling as A; Java + Jenkins-plugin-dev + Redis/Jedis knowledge; network access for the new Maven deps; must check the sync point (§8) before Phase 3. |
| C: test-infra | Phases 4.1–4.3, 4.6, then 4.4–4.5 | Same tooling; JUnit 5 / JUnit Platform / JenkinsRule(`@WithJenkins`) knowledge. 4.1–4.3+4.6 can run in parallel with B after Phase 1 defines the URL property; 4.4–4.5 only after B finishes. |
| D: verifier/reviewer | Phase 5 + code review of A–C | Bash with ~45 min-tolerant execution for three full suites; review focus: fail-open semantics preserved, no Hazelcast behavior change, JUnit 5 purity of new tests, §4.2 omissions documented. |

If B and C run concurrently on the same checkout they will conflict in `pom.xml` and the
services file — either serialize those two files' edits, or give each agent an isolated
git worktree and merge. All agents branch from (or stack onto) the Phase 0 result, not
from `distributed-storage` directly.

No agent needs MCP servers or external services beyond Maven Central/repo.jenkins-ci.org.
Every agent must know the §3.4 baseline so it doesn't chase the pre-existing failure.

## 7. Review checklist (for whoever reviews the PRs)

- [ ] Hazelcast behavior byte-for-byte unchanged (Phase 0 is moves only; instance-id
      helper NOT wired into Hazelcast).
- [ ] `RedisCoordinationProvider.initialize()` no-ops when mode ≠ redis.
- [ ] Claim strategies fail-open; storage fails skip-and-log; nothing throws into
      event-processing threads on Redis outage.
- [ ] Instance id: two controllers on one host get distinct ids; property override works.
- [ ] `getAllEvents()`/`report()` return snapshots (no live map views).
- [ ] Sticky-ownership omissions (§4.2) documented in javadoc where the Hazelcast
      equivalent has the compensator.
- [ ] Safety TTL refreshed on every build-memory write.
- [ ] Subscriber thread: daemon, named, reconnects with backoff, stops on shutdown.
- [ ] New tests are JUnit 5 only; no new JUnit 4 rules.
- [ ] Key prefix override honored by every key and the pub/sub channel.

## 8. Sync points / coordination with in-flight work

1. **Before Phase 3**: check `distributed-storage` for landed or imminent
   missed-events-playback changes (new SPI methods, changes to `BuildMemoryStorage` or
   `CoordinationModeProvider`). If the SPI grew, extend this spec's Phase 3/6 scope
   accordingly rather than implementing against the stale contract.
2. **Phase 0 should land early** — it touches the hazelcast package, and it is easier
   for the playback work to rebase over a completed move than the reverse.
3. Rebase the working branch on `distributed-storage` at each phase boundary.
