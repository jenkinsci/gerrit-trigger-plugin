/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.EntryData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.MemoryImprintData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.AbandonedPatchsetInterruption;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.NewPatchSetInterruption;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.PipelineAbortHelper;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventIdGenerator;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import jenkins.model.CauseOfInterruption;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hazelcast-backed implementation of BuildMemoryStorage for distributed scenarios.
 * <p>
 * Uses distributed IMap for storing build memory across multiple Jenkins replicas.
 * All operations use distributed locks ({@code map.tryLock(key, timeout, unit)}) to prevent race conditions.
 * <p>
 * <b>Note on EntryProcessors:</b> EntryProcessors are intentionally not used. In Hazelcast
 * client mode the processor class executes on the Hazelcast sidecar JVM, which does not have
 * Jenkins or plugin classes on its classpath, causing {@link ClassNotFoundException} at runtime.
 * Distributed locks provide equivalent atomicity guarantees without this constraint.
 * <p>
 * This implementation is automatically selected when:
 * <ul>
 *   <li>Coordination mode is set to 'hazelcast' via system property</li>
 *   <li>Hazelcast instance is available and running</li>
 * </ul>
 * <p>
 * <b>Serialization Strategy (MemoryImprint ↔ MemoryImprintData):</b>
 * <p>
 * The API type
 * ({@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint})
 * knows how to convert itself to and from its plain data form
 * ({@link MemoryImprintData}) via
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint#toData()}
 * and
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint#fromData(MemoryImprintData)}:
 * <ul>
 *   <li><b>Write Path</b>: Business logic → MemoryImprint → {@code toData()} → MemoryImprintData → Hazelcast IMap</li>
 *   <li><b>Read Path</b>: Hazelcast IMap → MemoryImprintData → {@code fromData()} → MemoryImprint → Business logic</li>
 * </ul>
 * <p>
 * The {@link MemoryImprintData} DTO carries the event as a live object; turning it into a JSON
 * string on the wire (with polymorphic type preservation) is handled by
 * {@link MemoryImprintDataSerializer} — the only Hazelcast-specific serialization boundary. This
 * keeps both the API type and the DTO unaware of storage concerns.
 *
 * @see HazelcastCoordinationProvider
 * @see MemoryImprintData
 * @see MemoryImprintDataSerializer
 * @see PolymorphicEventTypeAdapter
 */
public class HazelcastBuildMemoryStorage extends BuildMemoryStorage {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastBuildMemoryStorage.class);

    /**
     * Hazelcast map name for distributed build memory.
     */
    private static final String MAP_NAME = "gerrit-trigger-build-memory";

    /**
     * Hazelcast map name for cross-replica build abort requests.
     * <p>
     * When a replica needs to abort a build running on another replica, it puts an entry
     * {@code "jobFullName:buildNumber"} into this map. Each Jenkins replica has a listener
     * on this map and will abort any matching build running on its local executors.
     * <p>
     * This approach works in Hazelcast CLIENT mode where {@code executeOnMember()} would
     * target Hazelcast server sidecars (not Jenkins replicas) and fail with
     * {@code ClassNotFoundException} because Jenkins classes are absent from the sidecar JVM.
     */
    private static final String ABORT_INBOX_MAP_NAME = "gerrit-trigger-abort-inbox";

    /**
     * TTL for abort inbox entries in seconds (1 minute).
     * Entries expire automatically to prevent memory leaks in case the target build
     * has already finished or is not found on any replica.
     */
    private static final long ABORT_INBOX_TTL_SECONDS = 60;

    /**
     * Abort inbox value indicating the build was superseded by a new patchset.
     * The receiving replica will use {@link NewPatchSetInterruption} as the cause.
     */
    private static final String CAUSE_NEW_PATCHSET = "NEW_PATCHSET";

    /**
     * Abort inbox value indicating the patchset was abandoned.
     * The receiving replica will use {@link AbandonedPatchsetInterruption} as the cause.
     */
    private static final String CAUSE_ABANDONED = "ABANDONED";

    /**
     * Delay in seconds before writing a deferred cross-replica abort inbox entry.
     * <p>
     * When {@code started()} detects that an entry is already marked {@code isCancelling=true}
     * (race condition: build started after the abort decision was made), the abort inbox entry
     * is written after this delay so the CPS engine has had time to attach a
     * FlowExecution before the interrupt arrives.
     */
    private static final long DEFERRED_ABORT_DELAY_SECONDS = 3L;

    /**
     * Grace period in seconds before treating an ambiguous queue-item cancellation as final.
     * <p>
     * {@link HazelcastQueueCancellationStrategy#isLoadBalancedCancellation} cannot reliably tell
     * a genuine Gerrit-triggered cancellation apart from a benign cross-replica queue-item
     * relocation - Jenkins preserves no marker on {@code LeftItem} either way (see that method's
     * own doc comment). Rather than finalize immediately whenever {@code isCancelling} happens to
     * already be set, {@link #cancelled} waits this long for a possible {@link #started} call to
     * arrive from whichever replica the item actually landed on before concluding the build was
     * genuinely, permanently cancelled.
     * <p>
     * This narrows the race rather than closing it: observed real-world delay between a
     * cancellation decision and the relocated build's own started() call has been as long as ~6s
     * in production logs, so this is set comfortably above that - but a sufficiently slow
     * relocation can still, in principle, outlast this window.
     */
    private static final long CANCEL_FINALIZE_GRACE_SECONDS = 10L;

    /**
     * Poll interval in milliseconds used by {@link #handleAbortRequest} while waiting for a
     * Pipeline build's CPS execution to start before delivering the interrupt.
     */
    private static final long ABORT_RETRY_POLL_MS = 250L;

    /**
     * Maximum number of poll attempts in {@link #handleAbortRequest} before interrupting
     * regardless of CPS execution state.
     * <p>
     * Total maximum wait = {@link #ABORT_RETRY_POLL_MS} * {@code ABORT_MAX_RETRIES}
     * = 250 ms * 12 = 3 seconds.
     */
    private static final int ABORT_MAX_RETRIES = 12;

    /**
     * Maximum time in seconds to wait when acquiring a distributed lock.
     * <p>
     * Using {@link IMap#tryLock(Object, long, TimeUnit)} instead of {@link IMap#lock(Object)}
     * prevents threads from blocking indefinitely when a lock is stuck (e.g. after an
     * interrupted lock acquisition that left the Hazelcast server holding the lock).
     */
    private static final int LOCK_ACQUIRE_TIMEOUT_SECONDS = 10;

    /**
     * The Hazelcast instance to use for distributed storage.
     */
    private final HazelcastInstance hazelcastInstance;

    /**
     * Distributed mode storage (coordination mode).
     * Lazy-initialized when first accessed.
     * Marked volatile for thread-safe double-checked locking pattern.
     * Uses String keys (event IDs) instead of BuildMemoryKey to avoid Hazelcast classloader
     * issues when deserializing plugin-specific key classes via Java serialization.
     */
    private transient volatile IMap<String, MemoryImprintData> distributedMemory = null;

    /**
     * Constructor.
     *
     * @param hazelcastInstance the Hazelcast instance to use
     */
    public HazelcastBuildMemoryStorage(@NonNull HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        registerAbortInboxListener();
    }

    /**
     * Registers a listener on the abort inbox IMap so this replica can receive and process
     * cross-replica build abort requests submitted by other Jenkins replicas.
     * <p>
     * The listener fires in this JVM (which has Jenkins on its classpath), so it can safely
     * look up jobs and interrupt executors. This is the correct approach for Hazelcast CLIENT
     * mode where {@code executeOnMember()} would instead run code on the Hazelcast sidecar.
     */
    private void registerAbortInboxListener() {
        if (hazelcastInstance == null) {
            return;
        }
        IMap<String, String> abortInbox = hazelcastInstance.getMap(ABORT_INBOX_MAP_NAME);
        abortInbox.addEntryListener((EntryAddedListener<String, String>)event -> {
            String abortKey = event.getKey();
            int lastColon = abortKey.lastIndexOf(':');
            if (lastColon < 0) {
                logger.warn("Abort-inbox: invalid key (no colon separator): {}", abortKey);
                return;
            }
            String jobName = abortKey.substring(0, lastColon);
            String buildId = abortKey.substring(lastColon + 1);
            String causeType = event.getValue();
            handleAbortRequest(jobName, buildId, causeType);
        }, true);  // includeValue=true: the cause type string is needed by handleAbortRequest
        logger.debug("Registered abort-inbox listener on map: {}", ABORT_INBOX_MAP_NAME);
    }

    /**
     * Handles an abort request received via the abort inbox IMap.
     * Looks up the build on this replica and interrupts it if still running.
     * <p>
     * The {@code causeType} string determines which {@link CauseOfInterruption} subclass
     * is used so that the aborted build is annotated with the correct reason:
     * <ul>
     *   <li>{@value #CAUSE_ABANDONED} → {@link AbandonedPatchsetInterruption}</li>
     *   <li>{@value #CAUSE_NEW_PATCHSET} (or any other value) → {@link NewPatchSetInterruption}</li>
     * </ul>
     *
     * @param jobName   full name of the job
     * @param buildId   build number as string
     * @param causeType cause type string ({@value #CAUSE_ABANDONED} or {@value #CAUSE_NEW_PATCHSET})
     */
    private static void handleAbortRequest(String jobName, String buildId, String causeType) {
        handleAbortRequest(jobName, buildId, causeType, ABORT_MAX_RETRIES);
    }

    /**
     * @param jobName     full name of the Jenkins job
     * @param buildId     build number as a string
     * @param causeType   abort cause type ({@link #CAUSE_NEW_PATCHSET} or {@link #CAUSE_ABANDONED})
     * @param retriesLeft remaining poll attempts before interrupting regardless of CPS state;
     *                    decremented on each reschedule until it reaches zero
     */
    private static void handleAbortRequest(String jobName, String buildId,
                                           String causeType, int retriesLeft) {
        try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return;
            }
            Job<?, ?> job = jenkins.getItemByFullName(jobName, Job.class);
            if (job == null) {
                return;
            }
            Run<?, ?> build = job.getBuildByNumber(Integer.parseInt(buildId));
            if (build == null || !build.isBuilding()) {
                return;
            }

            // For Pipeline builds, wait until the CPS execution has started (i.e.
            // FlowExecution is attached to its FlowExecutionOwner) before delivering the
            // interrupt. Interrupting during CPS initialisation has no effect — the interrupt
            // flag is set before FlowExecution exists, so it is silently lost. See
            // PipelineAbortHelper for why waiting for FlowExecution alone is sufficient.
            // We poll every ABORT_RETRY_POLL_MS for up to ABORT_MAX_RETRIES attempts
            // (ABORT_RETRY_POLL_MS * ABORT_MAX_RETRIES = 3 s total maximum wait).
            boolean notYetStarted;
            try {
                notYetStarted = PipelineAbortHelper.isPipelineNotYetStarted(build);
            } catch (NoClassDefFoundError e) {
                // workflow-api not installed — not a pipeline, safe to interrupt now
                notYetStarted = false;
            }
            if (notYetStarted) {
                if (retriesLeft > 0) {
                    logger.debug("Abort-inbox: build={}/{} CPS not yet started, retrying in {}ms ({} attempts left)",
                            jobName, buildId, ABORT_RETRY_POLL_MS, retriesLeft);
                    Timer.get().schedule(
                            () -> handleAbortRequest(jobName, buildId, causeType, retriesLeft - 1),
                            ABORT_RETRY_POLL_MS, TimeUnit.MILLISECONDS);
                    return;
                }
                logger.info("Abort-inbox: build={}/{} CPS still not started after {} attempts, interrupting anyway",
                        jobName, buildId, ABORT_MAX_RETRIES);
            }

            CauseOfInterruption cause;
            if (CAUSE_ABANDONED.equals(causeType)) {
                cause = new AbandonedPatchsetInterruption();
            } else {
                cause = new NewPatchSetInterruption();
            }
            // Iterate all executors to find the one currently running this build.
            // Using build.getExecutor() is unreliable for Pipeline jobs: the flyweight
            // executor (controller-side CPS orchestrator) may be parked/suspended while
            // the actual work runs on an agent executor. Iterating computers mirrors the
            // same approach used in BuildMemory.cancelMatchingJobs() for local cancellation.
            boolean interrupted = false;
            for (Computer c : jenkins.getComputers()) {
                for (Executor e : c.getAllExecutors()) {
                    if (build.equals(e.getCurrentExecutable())) {
                        e.interrupt(Result.ABORTED, cause);
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                logger.info("Abort-inbox: interrupted job={} build={} cause={}", jobName, buildId, causeType);
            } else {
                logger.debug("Abort-inbox: no executor found for job={} build={} (may not be running locally)",
                        jobName, buildId);
            }
        } catch (Exception e) {
            logger.error("Abort-inbox: failed to abort job={} build={}", jobName, buildId, e);
        }
    }

    /**
     * Gets or initializes the distributed memory map using thread-safe double-checked locking.
     * <p>
     * Uses volatile field and synchronized block to ensure only one thread initializes
     * the map while avoiding synchronization overhead on subsequent accesses.
     *
     * @return distributed memory map, or null if Hazelcast unavailable
     */
    private IMap<String, MemoryImprintData> getDistributedMemory() {
        // First check (no locking) - fast path for already-initialized case
        if (distributedMemory == null) {
            synchronized (this) {
                // Second check (with locking) - ensures only one thread initializes
                if (distributedMemory == null) {
                    if (hazelcastInstance != null) {
                        distributedMemory = hazelcastInstance.getMap(MAP_NAME);
                        logger.debug("Initialized distributed BuildMemory map: {} (size: {})",
                                MAP_NAME, distributedMemory.size());
                    } else {
                        logger.warn("Hazelcast unavailable, distributed memory not available");
                    }
                }
            }
        }
        return distributedMemory;
    }

    /**
     * Attempts to acquire a distributed lock with a bounded timeout.
     * <p>
     * Unlike {@link IMap#lock(Object)}, this method will not block indefinitely.
     * If the lock cannot be acquired within {@link #LOCK_ACQUIRE_TIMEOUT_SECONDS} seconds
     * (e.g. a previous holder was interrupted mid-operation and left the lock unreleased),
     * this method returns {@code false} so the caller can skip the operation rather than
     * deadlocking a Gerrit event worker thread.
     * <p>
     * If interrupted while waiting, the thread's interrupt status is restored before returning.
     *
     * @param map the distributed map that owns the lock
     * @param key the key to lock
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     */
    private static boolean tryLockWithTimeout(IMap<String, MemoryImprintData> map, String key) {
        try {
            return map.tryLock(key, LOCK_ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for distributed lock on key: {}", key);
            return false;
        }
    }

    /**
     * Code to run while a distributed lock is held, passed to {@link #withLock}.
     */
    @FunctionalInterface
    private interface LockedAction {
        void run();
    }

    /**
     * Outcome of a {@link #withLock} call, letting the caller react if the lock was never
     * acquired (in which case the {@link LockedAction} did not run at all).
     */
    private static final class LockOutcome {
        private final boolean acquired;

        private LockOutcome(boolean acquired) {
            this.acquired = acquired;
        }

        /**
         * Runs {@code action} only if the lock could not be acquired.
         *
         * @param action ran when the lock was not acquired within {@link #LOCK_ACQUIRE_TIMEOUT_SECONDS}
         */
        void onFailure(Runnable action) {
            if (!acquired) {
                action.run();
            }
        }
    }

    /**
     * Runs {@code action} while holding the distributed lock on {@code key}, always releasing
     * the lock afterwards. If the lock cannot be acquired within
     * {@link #LOCK_ACQUIRE_TIMEOUT_SECONDS}, {@code action} is not run at all; use the returned
     * {@link LockOutcome#onFailure} to react to that case (e.g. logging and skipping the
     * operation).
     * <p>
     * {@code action} is responsible for catching and logging its own exceptions - this method
     * only manages lock acquisition and release, not business-logic error handling.
     *
     * @param map    the distributed map that owns the lock
     * @param key    the key to lock
     * @param action the code to run while the lock is held
     * @return a {@link LockOutcome} - chain {@link LockOutcome#onFailure} to react to lock failure
     */
    private LockOutcome withLock(IMap<String, MemoryImprintData> map, String key, LockedAction action) {
        if (!tryLockWithTimeout(map, key)) {
            return new LockOutcome(false);
        }
        try {
            action.run();
        } finally {
            map.unlock(key);
        }
        return new LockOutcome(true);
    }

    // ===== Implement BuildMemoryStorage abstract methods =====

    @Override
    @CheckForNull
    public synchronized MemoryImprint getMemoryImprint(@NonNull GerritTriggeredEvent event) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return null;
        }

        String key = EventIdGenerator.generateEventId(event);
        MemoryImprintData data = map.get(key);
        if (data != null) {
            return MemoryImprint.fromData(data);
        }
        return null;
    }

    @Override
    public synchronized void triggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot record triggered - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock ensures only one replica modifies this entry at a time.
        // This is critical when multiple projects are triggered by the same event simultaneously.
        // Without atomic operations, concurrent threads can overwrite each other's entries,
        // causing some project entries to be lost from BuildMemory (which breaks cancellation logic).
        // Note: EntryProcessor is NOT used here because in client mode the processor class would need
        // to exist on the Hazelcast sidecar member's classpath, causing ClassNotFoundException.
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null) {
                    data = new MemoryImprintData();
                    data.setEvent(event);
                }
                boolean found = false;
                if (data.getEntries() != null) {
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    EntryData newEntry = new EntryData();
                    newEntry.setProjectFullName(projectFullName);
                    data.addEntry(newEntry);
                }
                map.put(key, data);
                if (!found) {
                    logger.trace("Triggered event stored in distributed memory: {} for project: {}",
                            key, projectFullName);
                } else {
                    logger.trace("Project {} already triggered for event: {}", projectFullName, key);
                }
            } catch (Exception e) {
                logger.error("Failed to store triggered event in distributed memory for project: {} event: {}",
                        projectFullName, key, e);
            }
        }).onFailure(() -> logger.error(
                "Could not acquire distributed lock for key {} within {}s - skipping triggered()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
    }

    @Override
    public synchronized void started(@NonNull GerritTriggeredEvent event, @NonNull Run build) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark started - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = build.getParent().getFullName();
        String buildId = build.getId();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        long startedTimestamp = System.currentTimeMillis();
        // Track whether a deferred cross-replica abort needs to be sent after the lock is released.
        // This handles the race condition where requestCrossReplicaAbort() was called before this
        // build's buildId was written to Hazelcast (buildId was null at that time, so no abort
        // inbox entry was written). We detect this by checking isCancelling on the entry after
        // writing the buildId, and then write the abort inbox entry here.
        AtomicBoolean pendingCrossReplicaAbort = new AtomicBoolean(false);
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null) {
                    data = new MemoryImprintData();
                }
                boolean found = false;
                if (data.getEntries() != null) {
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            entryData.setBuildId(buildId);
                            entryData.setStartedTimestamp(startedTimestamp);
                            // The build actually started on a replica — clear any queueLeft flag that
                            // was set when the queue item was moved by a load balancer. The entry is
                            // now actively building and must be visible to cancelOutdatedBuilds again.
                            entryData.setQueueLeft(false);
                            found = true;
                            // If this entry is already marked for cancellation, we need to trigger
                            // cross-replica abort now that buildId is known.
                            if (entryData.isCancelling()) {
                                pendingCrossReplicaAbort.set(true);
                            }
                            break;
                        }
                    }
                }
                if (!found) {
                    EntryData newEntry = new EntryData();
                    newEntry.setProjectFullName(projectFullName);
                    newEntry.setBuildId(buildId);
                    newEntry.setStartedTimestamp(startedTimestamp);
                    data.setEvent(event);
                    data.addEntry(newEntry);
                }
                map.put(key, data);
                if (!found) {
                    logger.warn("Build started without being registered first (distributed mode).");
                }
                logger.trace("Build started event stored in distributed memory: {}", key);
            } catch (Exception e) {
                logger.error("Failed to mark build started in distributed memory: project={}, build={}, event={}",
                        projectFullName, buildId, key, e);
            }
        }).onFailure(() -> logger.error("Could not acquire distributed lock for key {} within {}s - skipping started()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));

        // Deferred cross-replica abort: if the entry was already marked for cancellation when this
        // build started, requestCrossReplicaAbort() previously found buildId=null and could not
        // write the abort inbox entry. Now that buildId is known, schedule the abort signal with
        // a short delay so the CPS pipeline has time to complete initialization before the
        // interrupt is delivered. Firing the interrupt too early (during CPS init, before any
        // step begins) has no effect — the interrupt flag is set on the wrong thread context.
        if (pendingCrossReplicaAbort.get() && hazelcastInstance != null) {
            final HazelcastInstance hz = hazelcastInstance;
            final String abortKey = projectFullName + ":" + buildId;
            logger.info("Scheduling deferred cross-replica abort in {}s (started race): job={} build={}",
                    DEFERRED_ABORT_DELAY_SECONDS, projectFullName, buildId);
            Timer.get().schedule(() -> {
                try {
                    IMap<String, String> abortInbox = hz.getMap(ABORT_INBOX_MAP_NAME);
                    abortInbox.put(abortKey, CAUSE_NEW_PATCHSET, ABORT_INBOX_TTL_SECONDS, TimeUnit.SECONDS);
                    logger.info("Queued deferred cross-replica abort (started race): job={} build={} cause={}",
                            projectFullName, buildId, CAUSE_NEW_PATCHSET);
                } catch (Exception ex) {
                    logger.warn("Failed to write deferred abort inbox entry: key={}", abortKey, ex);
                }
            }, DEFERRED_ABORT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public synchronized void completed(@NonNull GerritTriggeredEvent event, @NonNull Run build) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark completed - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = build.getParent().getFullName();
        String buildId = build.getId();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        long completedTimestamp = System.currentTimeMillis();
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null) {
                    data = new MemoryImprintData();
                }
                boolean found = false;
                if (data.getEntries() != null) {
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            if (entryData.getBuildId() == null) {
                                entryData.setBuildId(buildId);
                            }
                            entryData.setCompletedTimestamp(completedTimestamp);
                            entryData.setBuildCompleted(true);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    EntryData newEntry = new EntryData();
                    newEntry.setProjectFullName(projectFullName);
                    newEntry.setBuildId(buildId);
                    newEntry.setCompletedTimestamp(completedTimestamp);
                    newEntry.setBuildCompleted(true);
                    data.setEvent(event);
                    data.addEntry(newEntry);
                }
                map.put(key, data);
                if (!found) {
                    logger.debug("Build completed without being registered first (distributed mode).");
                }
                logger.trace("Build completed event stored in distributed memory: {}", key);
            } catch (Exception e) {
                logger.error("Failed to mark build completed in distributed memory: project={}, build={}, event={}",
                        projectFullName, buildId, key, e);
            }
        }).onFailure(() -> logger.error(
                "Could not acquire distributed lock for key {} within {}s - skipping completed()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
    }

    @Override
    public synchronized void retriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project,
                            @CheckForNull List<Run> otherBuilds) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot record retriggered - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null) {
                    data = new MemoryImprintData();
                    data.setEvent(event);
                    if (otherBuilds != null) {
                        for (Run otherBuild : otherBuilds) {
                            EntryData entryData = new EntryData();
                            entryData.setProjectFullName(otherBuild.getParent().getFullName());
                            entryData.setBuildId(otherBuild.getId());
                            entryData.setBuildCompleted(!otherBuild.isBuilding());
                            data.addEntry(entryData);
                        }
                    }
                }
                boolean found = false;
                if (data.getEntries() != null) {
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            entryData.setBuildId(null);
                            entryData.setBuildCompleted(false);
                            entryData.setStartedTimestamp(null);
                            entryData.setCompletedTimestamp(null);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    EntryData newEntry = new EntryData();
                    newEntry.setProjectFullName(projectFullName);
                    data.addEntry(newEntry);
                }
                map.put(key, data);
                logger.trace("Retriggered event stored in distributed memory: {}", key);
            } catch (Exception e) {
                logger.error("Failed to record retriggered in distributed memory: project={}, event={}",
                        projectFullName, key, e);
            }
        }).onFailure(() -> logger.error(
                "Could not acquire distributed lock for key {} within {}s - skipping retriggered()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
    }

    @Override
    public synchronized void cancelled(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark cancelled - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        // Set when this queue exit is ambiguous (isCancelling was already true) and needs a
        // deferred re-check after the lock is released - see CANCEL_FINALIZE_GRACE_SECONDS.
        AtomicBoolean scheduleFinalizeCheck = new AtomicBoolean(false);
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null) {
                    data = new MemoryImprintData();
                }
                boolean found = false;
                boolean modified = false;
                if (data.getEntries() != null) {
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            found = true;
                            if (!entryData.isBuildCompleted()) {
                                if (entryData.isCancelling()) {
                                    // Ambiguous: the intent was set via setCancelling() first (e.g. by
                                    // cancelOutdatedBuilds), but this queue exit itself could equally be
                                    // a genuine Gerrit-triggered cancellation OR a benign cross-replica
                                    // queue-item relocation - isLoadBalancedCancellation() can't tell
                                    // them apart (see its own doc comment; Jenkins preserves no marker
                                    // on LeftItem either way). Don't finalize yet: mark queueLeft (not
                                    // completed) and leave isCancelling=true so started()'s own
                                    // deferred-abort compensator still fires correctly if this really is
                                    // a relocation and the build reports in from wherever it landed.
                                    // scheduleDeferredCancelFinalize (below, after the lock is released)
                                    // decides the real outcome after a grace period.
                                    entryData.setQueueLeft(true);
                                    scheduleFinalizeCheck.set(true);
                                } else if (entryData.getBuildId() == null) {
                                    // No prior cancellation intent AND build has not started anywhere.
                                    // This is a potential load-balanced queue move (build will restart
                                    // on another instance) or a direct Queue.doCancelItem before start.
                                    // Mark queueLeft=true but do NOT set buildCompleted=true so the
                                    // IMap entry is preserved for cross-instance PS2-aborts-PS1 scenarios.
                                    // NOTE: if buildId IS already set, started() already ran on another
                                    // instance — leave the entry completely untouched so it stays visible
                                    // to cancelOutdatedBuilds on that instance.
                                    entryData.setQueueLeft(true);
                                    logger.info("Marking queueLeft (pre-cancellation-intent queue exit, no "
                                            + "buildId yet) for project={} event={} - possible load-balanced "
                                            + "relocation with no started() call yet", projectFullName, key);
                                } else {
                                    logger.debug("cancelled() called after started() for project={} event={}: "
                                            + "build already running (buildId={}), ignoring late onLeft.",
                                            projectFullName, key, entryData.getBuildId());
                                }
                                modified = true;
                            } else {
                                logger.debug("Skipping cancelled() for project={} event={}: "
                                        + "already completed, buildId={}.",
                                        projectFullName, key, entryData.getBuildId());
                            }
                            break;
                        }
                    }
                }
                if (!found) {
                    logger.debug("cancelled() called for untracked project={} event={}: skipping.",
                            projectFullName, key);
                }
                if (modified) {
                    map.put(key, data);
                }
                logger.trace("Cancelled event stored in distributed memory: {}", key);
            } catch (Exception e) {
                logger.error("Failed to mark cancelled in distributed memory: project={}, event={}",
                        projectFullName, key, e);
            }
        }).onFailure(() -> logger.error(
                "Could not acquire distributed lock for key {} within {}s - skipping cancelled()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));

        if (scheduleFinalizeCheck.get()) {
            scheduleDeferredCancelFinalize(event, project, key, projectFullName);
        }
    }

    /**
     * Re-checks a provisionally-cancelled entry after {@link #CANCEL_FINALIZE_GRACE_SECONDS} and
     * finalizes it as a genuine cancellation only if no {@link #started} call arrived for it in
     * the meantime.
     * <p>
     * Exists because {@link HazelcastQueueCancellationStrategy#isLoadBalancedCancellation} cannot
     * distinguish a genuine Gerrit-triggered cancellation from a benign cross-replica queue-item
     * relocation - without this grace period, a relocated-but-still-alive build was getting marked
     * completed/forgotten (triggering premature Gerrit "No Builds Executed" feedback) before it
     * had a chance to call {@code started()} on whichever replica it landed on, silently skipping
     * the deferred cross-replica abort this class already schedules for the reverse race (see
     * {@link #started}).
     * <p>
     * Deliberately narrows the race rather than closing it - see {@link #CANCEL_FINALIZE_GRACE_SECONDS}.
     *
     * @param event the event whose entry may need finalizing
     * @param project the project/job the entry is for
     * @param key the distributed map key for event
     * @param projectFullName project's full name
     */
    private void scheduleDeferredCancelFinalize(
            @NonNull GerritTriggeredEvent event, @NonNull Job project, String key, String projectFullName) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }
        Timer.get().schedule(() -> {
            AtomicBoolean finalized = new AtomicBoolean(false);
            withLock(map, key, () -> {
                try {
                    MemoryImprintData data = map.get(key);
                    if (data == null || data.getEntries() == null) {
                        return;
                    }
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            if (entryData.isCancelling() && !entryData.isBuildCompleted()
                                    && entryData.getBuildId() == null) {
                                logger.info("Finalizing deferred cancellation for project={} event={}: "
                                        + "no started() call arrived within {}s grace period - genuine cancel.",
                                        projectFullName, key, CANCEL_FINALIZE_GRACE_SECONDS);
                                entryData.setCancelled(true);
                                entryData.setCancelling(false);
                                entryData.setCompletedTimestamp(System.currentTimeMillis());
                                entryData.setBuildCompleted(true);
                                map.put(key, data);
                                finalized.set(true);
                            } else {
                                logger.debug("Deferred cancel-finalize check for project={} event={}: "
                                        + "buildId={} isCancelling={} isBuildCompleted={} - a build "
                                        + "reported in elsewhere or already completed; treating as a "
                                        + "relocation, not a genuine cancellation.",
                                        projectFullName, key, entryData.getBuildId(), entryData.isCancelling(),
                                        entryData.isBuildCompleted());
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed deferred cancel-finalize check: project={}, event={}",
                            projectFullName, key, e);
                }
            }).onFailure(() -> logger.error("Could not acquire distributed lock for key {} within {}s - skipping "
                    + "deferred cancel-finalize check", key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
            if (finalized.get()) {
                // The original GerritQueueListener#onLeft call that led here checked
                // isAllBuildsCompleted() synchronously, before this deferred finalize ran, so it
                // saw "not yet complete" and skipped the Gerrit-feedback/forget step. Re-invoke it
                // now that the entry is actually finalized, exactly as it would have run
                // synchronously had this been unambiguous from the start.
                ToGerritRunListener runListener = ToGerritRunListener.getInstance();
                if (runListener != null) {
                    runListener.allBuildsCompleted(event, new GerritCause(event, false), TaskListener.NULL);
                }
            }
        }, CANCEL_FINALIZE_GRACE_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void setCancelling(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark cancelling - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data != null && data.getEntries() != null) {
                    boolean updated = false;
                    for (EntryData entryData : data.getEntries()) {
                        if (projectFullName.equals(entryData.getProjectFullName())) {
                            // Not gated on !isQueueLeft(): an ambiguous queue exit (possible
                            // load-balanced relocation) is not proof this entry is done, and must
                            // remain eligible to be marked cancelling so a relocated-then-started
                            // build still gets cross-replica-aborted (see BuildMemory's identical
                            // reasoning in cancelOutdatedEvents()).
                            if (!entryData.isBuildCompleted() && !entryData.isCancelling()
                                    && !entryData.isCancelled()) {
                                entryData.setCancelling(true);
                                updated = true;
                            }
                        }
                    }
                    if (updated) {
                        map.put(key, data);
                    }
                }
                logger.trace("Cancelling flag set in distributed memory for event: {}", key);
            } catch (Exception e) {
                logger.error("Failed to set cancelling flag in distributed memory: project={}, event={}",
                        projectFullName, key, e);
            }
        }).onFailure(() -> logger.error(
                "Could not acquire distributed lock for key {} within {}s - skipping setCancelling()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
    }

    @Override
    public synchronized void requestCrossReplicaAbort(@NonNull GerritTriggeredEvent event, @NonNull Job project,
                                                       @NonNull CauseOfInterruption cause) {
        if (hazelcastInstance == null) {
            return;
        }

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = project.getFullName();
        String causeType;
        if (cause instanceof AbandonedPatchsetInterruption) {
            causeType = CAUSE_ABANDONED;
        } else {
            causeType = CAUSE_NEW_PATCHSET;
        }

        // Collect build IDs that were marked as cancelling for this project/event.
        // setCancelling() has already set isCancelling=true in the distributed map.
        List<String> buildIdsToAbort = new ArrayList<>();
        MemoryImprintData data = map.get(key);
        if (data != null && data.getEntries() != null) {
            for (EntryData entryData : data.getEntries()) {
                if (projectFullName.equals(entryData.getProjectFullName())
                        && entryData.isCancelling()
                        && entryData.getBuildId() != null) {
                    buildIdsToAbort.add(entryData.getBuildId());
                }
            }
        }

        // Put abort requests into the distributed abort inbox for cross-replica cancellation.
        // Each Jenkins replica has a listener on this map (registered in the constructor) and will
        // abort any matching build running on its local executors.
        // NOTE: executeOnMember() cannot be used in Hazelcast CLIENT mode because it runs the task
        // on the Hazelcast sidecar JVM, which does not have Jenkins classes on its classpath.
        if (!buildIdsToAbort.isEmpty()) {
            IMap<String, String> abortInbox = hazelcastInstance.getMap(ABORT_INBOX_MAP_NAME);
            for (String buildId : buildIdsToAbort) {
                String abortKey = projectFullName + ":" + buildId;
                abortInbox.put(abortKey, causeType, ABORT_INBOX_TTL_SECONDS, TimeUnit.SECONDS);
                logger.info("Queued cross-replica abort: job={} build={} cause={}", projectFullName, buildId, causeType);
            }
        }
    }

    @Override
    public synchronized void forget(@NonNull GerritTriggeredEvent event) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        map.remove(key);
        logger.trace("Forgot event from distributed memory: {}", key);
    }

    @Override
    public synchronized void removeProject(@NonNull Job project) {
        String projectFullName = project.getFullName();

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        // ATOMIC OPERATION - Distributed lock per key. EntryProcessor not used (ClassNotFoundException in client mode).
        // Collect keys first to avoid ConcurrentModificationException
        java.util.Set<String> keys = new java.util.HashSet<>(map.keySet());

        for (String key : keys) {
            withLock(map, key, () -> {
                try {
                    MemoryImprintData data = map.get(key);
                    if (data == null || data.getEntries() == null) {
                        return;
                    }
                    boolean removed = data.getEntries().removeIf(
                            entryData -> projectFullName.equals(entryData.getProjectFullName()));
                    if (removed) {
                        if (data.getEntries().isEmpty()) {
                            map.delete(key);
                            logger.trace("Removed empty entry for project {} from distributed memory: {}",
                                projectFullName, key);
                        } else {
                            map.put(key, data);
                            logger.trace("Removed project {} from distributed memory entry: {}",
                                projectFullName, key);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to remove project from distributed memory entry: project={}, key={}",
                            projectFullName, key, e);
                    // Continue processing other keys
                }
            }).onFailure(() -> logger.error(
                    "Could not acquire distributed lock for key {} within {}s - skipping removeProject() entry",
                    key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
        }
    }

    @Override
    public synchronized boolean isAllBuildsCompleted(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        return imprint != null && imprint.isAllBuildsCompleted();
    }

    @Override
    public synchronized boolean isAllBuildsStarted(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        return imprint != null && imprint.isAllBuildsSet();
    }

    @Override
    @CheckForNull
    public synchronized BuildsStartedStats getBuildsStartedStats(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint != null) {
            return imprint.getBuildsStartedStats();
        }
        return null;
    }

    @Override
    @CheckForNull
    public synchronized String getStatusReport(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint != null) {
            return imprint.getStatusReport();
        }
        return null;
    }

    @Override
    public synchronized boolean isTriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint == null) {
            return false;
        }
        String fullName = project.getFullName();
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            if (entry.isProject(fullName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean isBuilding(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint == null) {
            return false;
        }
        String fullName = project.getFullName();
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            if (entry.isProject(fullName)) {
                if (entry.getBuild() != null) {
                    return !entry.isBuildCompleted();
                } else {
                    return !entry.isCancelling() && !entry.isCancelled() && !entry.isQueueLeft();
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean isBuilding(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint == null) {
            return false;
        }
        // An event is still "building" if at least one entry is not yet in a terminal state.
        // queueLeft entries (load-balanced moves or direct doCancelItem) are treated as
        // inactive — they are not running on this replica. This allows isBuilding(event)
        // to return false for the unit-test case (direct doCancelItem) while preserving
        // the IMap key for cross-replica PS2-aborts-PS1 scenarios.
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            if (!entry.isBuildCompleted() && !entry.isQueueLeft()) {
                return true;
            }
        }
        return false;
    }

    @Override
    @CheckForNull
    public synchronized List<Run> getBuilds(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint != null) {
            List<Run> list = new LinkedList<>();
            for (MemoryImprint.Entry entry : imprint.getEntries()) {
                if (entry.getBuild() != null) {
                    list.add(entry.getBuild());
                }
            }
            return list;
        }
        return null;
    }

    @Override
    public void setEntryCustomUrl(@NonNull GerritTriggeredEvent event, @NonNull Run r,
                                   @CheckForNull String customUrl) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set custom URL - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = r.getParent().getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null || data.getEntries() == null) {
                    logger.warn("Could not set custom URL - event not found: {}", event);
                    return;
                }
                boolean found = false;
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        entryData.setCustomUrl(customUrl);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    map.put(key, data);
                    logger.trace("Recording custom URL for {}: {}", event, customUrl);
                } else {
                    logger.warn("Could not set custom URL - event not found: {}", event);
                }
            } catch (Exception e) {
                logger.error("Failed to set custom URL in distributed memory: project={}, event={}, url={}",
                        projectFullName, key, customUrl, e);
            }
        }).onFailure(() -> logger.error(
                "Could not acquire distributed lock for key {} within {}s - skipping setEntryCustomUrl()",
                key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
    }

    @Override
    public void setEntryUnsuccessfulMessage(@NonNull GerritTriggeredEvent event, @NonNull Run r,
                                            @CheckForNull String unsuccessfulMessage) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set unsuccessful message - Hazelcast unavailable");
            return;
        }

        String key = EventIdGenerator.generateEventId(event);
        String projectFullName = r.getParent().getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        withLock(map, key, () -> {
            try {
                MemoryImprintData data = map.get(key);
                if (data == null || data.getEntries() == null) {
                    logger.warn("Could not set unsuccessful message - event not found: {}", event);
                    return;
                }
                boolean found = false;
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        entryData.setUnsuccessfulMessage(unsuccessfulMessage);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    map.put(key, data);
                    logger.trace("Recording unsuccessful message for {}: {}", event, unsuccessfulMessage);
                } else {
                    logger.warn("Could not set unsuccessful message - event not found: {}", event);
                }
            } catch (Exception e) {
                logger.error("Failed to set unsuccessful message in distributed memory:"
                                + " project={}, event={}, message={}",
                        projectFullName, key, unsuccessfulMessage, e);
            }
        }).onFailure(() -> logger.error("Could not acquire distributed lock for key {} within {}s"
                + " - skipping setEntryUnsuccessfulMessage()", key, LOCK_ACQUIRE_TIMEOUT_SECONDS));
    }

    @Override
    @NonNull
    public synchronized BuildMemoryReport report() {
        BuildMemoryReport report = new BuildMemoryReport();

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return report;
        }

        // Read all entries from distributed memory
        for (Map.Entry<String, MemoryImprintData> mapEntry : map.entrySet()) {
            MemoryImprintData data = mapEntry.getValue();
            GerritTriggeredEvent event = data.getEvent();

            if (event != null) {
                MemoryImprint imprint = MemoryImprint.fromData(data);
                List<MemoryImprint.Entry> triggered = new LinkedList<MemoryImprint.Entry>();
                for (MemoryImprint.Entry tr : imprint.getEntries()) {
                    triggered.add(tr.clone());
                }
                report.put(event, triggered);
            }
        }
        return report;
    }

    @Override
    @NonNull
    public synchronized Map<GerritTriggeredEvent, MemoryImprint> getAllEvents() {
        Map<GerritTriggeredEvent, MemoryImprint> result = new HashMap<>();

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return result;
        }

        // Convert all entries
        for (Map.Entry<String, MemoryImprintData> entry : map.entrySet()) {
            MemoryImprintData data = entry.getValue();
            if (data != null) {
                GerritTriggeredEvent event = data.getEvent();
                if (event != null) {
                    MemoryImprint imprint = MemoryImprint.fromData(data);
                    result.put(event, imprint);
                }
            }
        }

        logger.trace("Returning {} events from distributed memory", result.size());
        return result;
    }

    @Override
    public boolean eventsMatch(@NonNull GerritTriggeredEvent event1, @NonNull GerritTriggeredEvent event2) {
        // In distributed mode, use logical comparison via EventIdGenerator
        // because events may be deserialized from Hazelcast, creating new object instances
        String id1 = EventIdGenerator.generateEventId(event1);
        String id2 = EventIdGenerator.generateEventId(event2);
        return id1.equals(id2);
    }
}
