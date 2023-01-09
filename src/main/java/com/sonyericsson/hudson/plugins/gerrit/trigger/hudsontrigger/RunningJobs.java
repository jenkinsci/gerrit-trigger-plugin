package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;


import static com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.getServerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginChangeAbandonedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* Class for maintaining and synchronizing the runningJobs info.
* Association between patches and the jobs that we're running for them.
*/
public class RunningJobs {

   private final GerritTrigger trigger;
   private Item job;

   private final Set<GerritTriggeredEvent> runningJobs =
           Collections.synchronizedSet(new HashSet<>());
   private static final Logger logger = LoggerFactory.getLogger(RunningJobs.class);

   /**
    * Constructor: embeds the trigger and it's underlying job into the tracked list.
    *
    * @param trigger - gerrit trigger that has multiple running jobs
    * @param job - underlying job of running build and triggers
    */
   public RunningJobs(GerritTrigger trigger, Item job) {
       this.trigger = trigger;
       this.job = job;
   }

   /**
     * @return the job
     */
    public Item getJob() {
        return job;
    }

    /**
     * @param job the job to set
     */
    public void setJob(Item job) {
        this.job = job;
    }

   /**
    * Called when trigger has cancellation policy associated with it.
    *
    *
    * @param event event that is trigger builds
    * @param jobName job name to match for specific cancellation
    * @param policy policy to decide cancelling build or not
    */
   public void cancelTriggeredJob(ChangeBasedEvent event, String jobName, BuildCancellationPolicy policy)
   {
       if (policy == null || !policy.isEnabled()) {
           return;
       }

       if ((event instanceof ManualPatchsetCreated && !policy.isAbortManualPatchsets())) {
          return;
       }

       this.cancelOutDatedEvents(event, policy, jobName);
   }

   /**
    * Checks scheduled job and cancels current jobs if needed.
    * I.e. cancelling the old build if configured to do so and removing and storing any references.
    * Only used by Server wide policy
    *
    * @param event the event triggering a new build.
    */
   public void scheduled(ChangeBasedEvent event) {
       IGerritHudsonTriggerConfig serverConfig = getServerConfig(event);
       if (serverConfig == null) {
           runningJobs.add(event);
           return;
       }

       BuildCancellationPolicy serverBuildCurrentPatchesOnly = serverConfig.getBuildCurrentPatchesOnly();

       if (!serverBuildCurrentPatchesOnly.isEnabled()
               || (event instanceof ManualPatchsetCreated
               && !serverBuildCurrentPatchesOnly.isAbortManualPatchsets())) {
           runningJobs.add(event);
           return;
       }

       this.cancelOutDatedEvents(event, serverBuildCurrentPatchesOnly, getJob().getFullName());
   }

   /**
    *
    * @param event event to check for
    * @param policy policy to determine cancellation of build for
    * @param jobName job name parameter to consider; if null, assumes all builds
    */
   private void cancelOutDatedEvents(ChangeBasedEvent event, BuildCancellationPolicy policy, String jobName)
   {
       List<ChangeBasedEvent> outdatedEvents = new ArrayList<>();
       synchronized (runningJobs) {
           Iterator<GerritTriggeredEvent> it = runningJobs.iterator();
           while (it.hasNext()) {
               GerritTriggeredEvent runningEvent = it.next();
               if (!(runningEvent instanceof ChangeBasedEvent)) {
                   continue;
               }

               ChangeBasedEvent runningChangeBasedEvent = ((ChangeBasedEvent)runningEvent);
               if (shouldIgnoreEvent(event, policy, runningChangeBasedEvent)) {
                   continue;
               }

               outdatedEvents.add(runningChangeBasedEvent);
               it.remove();
           }

           // add our new job
           if (!outdatedEvents.contains(event)) {
               if (event instanceof ChangeAbandoned) {
                   for (PluginGerritEvent e : trigger.getTriggerOnEvents()) {
                       if (e instanceof PluginChangeAbandonedEvent) {
                           runningJobs.add(event);
                           break;
                       }
                   }
               } else {
                   runningJobs.add(event);
               }
           }
       }

       // This step can't be done under the lock, because cancelling the jobs needs a lock on higher level.
       for (ChangeBasedEvent outdatedEvent : outdatedEvents) {
           logger.debug("Cancelling build for " + outdatedEvent);
           try {
               cancelMatchingJobs(outdatedEvent, jobName);
           } catch (Exception e) {
               // Ignore any problems with canceling the job.
               logger.error("Error canceling job", e);
           }
       }
   }

   /**
    * Determines if event should be ignored due to policy
    *
    * @param event event being evaluated
    * @param policy policy to determine cancellation
    * @param runningChangeBasedEvent existing event to compare against
    * @return true if event should be ignored for cancellation
    */
   private boolean shouldIgnoreEvent(ChangeBasedEvent event,
           BuildCancellationPolicy policy, ChangeBasedEvent runningChangeBasedEvent)
   {
       // Find all entries in runningJobs with the same Change #.
       // Optionally, ignore all manual patchsets and don't cancel builds due to
       // a retrigger of an older build.
       boolean abortBecauseOfTopic = trigger.abortBecauseOfTopic(event, policy, runningChangeBasedEvent);

       Change change = runningChangeBasedEvent.getChange();
       if (!abortBecauseOfTopic && !change.equals(event.getChange())) {
           return true;
       }

       boolean shouldCancelManual = (!(runningChangeBasedEvent instanceof ManualPatchsetCreated)
               || policy.isAbortManualPatchsets());

       if (!abortBecauseOfTopic && !shouldCancelManual) {
           return true;
       }

       boolean shouldCancelPatchsetNumber = policy.isAbortNewPatchsets()
               || Integer.parseInt(runningChangeBasedEvent.getPatchSet().getNumber())
               < Integer.parseInt(event.getPatchSet().getNumber());

       boolean isAbortAbandonedPatchset = policy.isAbortAbandonedPatchsets()
               && (event instanceof ChangeAbandoned);

       if (!abortBecauseOfTopic && !shouldCancelPatchsetNumber && !isAbortAbandonedPatchset) {
           return true;
       }

       return false;
   }

   /**
    * Tries to cancel any job, which was triggered by the given change event.
    * <p>
    * Since the event is always noted in the build cause, it is easy to
    * identify which specific builds shall be cancelled, without having
    * to dig down into the parameters, which might've been mutated by the
    * build while it was running. (This was the previous implementation)
    * <p>
    * We look in both the build queue and currently executing jobs.
    * This extra work is required due to race conditions when calling
    * Future.cancel() - see
    * https://issues.jenkins-ci.org/browse/JENKINS-13829
    *
    * @param event The event that originally triggered the build.
    * @param jobName  job name to match on.
    */
   private void cancelMatchingJobs(GerritTriggeredEvent event, String jobName) {
       try {
           if (!(this.job instanceof Queue.Task)) {
               logger.error("Error canceling job. The job is not of type Task. Job name: " + getJob().getName());
               return;
           }

           // Remove any jobs in the build queue.
           List<Queue.Item> itemsInQueue = Queue.getInstance().getItems((Queue.Task)getJob());
           for (Queue.Item item : itemsInQueue) {
               if (checkCausedByGerrit(event, item.getCauses())) {
                   if (jobName.equals(item.task.getName())) {
                       Queue.getInstance().cancel(item);
                   }
               }
           }

           // Interrupt any currently running jobs.
           Jenkins jenkins = Jenkins.get();
           for (Computer c : jenkins.getComputers()) {
               for (Executor e : c.getAllExecutors()) {
                   Queue.Executable currentExecutable = e.getCurrentExecutable();
                   if (!(currentExecutable instanceof Run<?, ?>)) {
                       continue;
                   }

                   Run<?, ?> run = (Run<?, ?>)currentExecutable;
                   if (!checkCausedByGerrit(event, run.getCauses())) {
                       continue;
                   }

                   String runningJobName = run.getParent().getFullName();
                   if (!jobName.equals(runningJobName)) {
                       continue;
                   }

                   e.interrupt(Result.ABORTED, new NewPatchSetInterruption());
               }
           }
       } catch (Exception e) {
           // Ignore any problems with canceling the job.
           logger.error("Error canceling job", e);
       }
   }

   /**
    * Checks if any of the given causes references the given event.
    *
    * @param event The event to check for. Checks for <i>identity</i>, not
    * <i>equality</i>!
    * @param causes the list of causes. Only {@link GerritCause}s are considered.
    * @return true if the list of causes contains a {@link GerritCause}.
    */
   private boolean checkCausedByGerrit(GerritTriggeredEvent event, Collection<Cause> causes) {
       for (Cause c : causes) {
           if (!(c instanceof GerritCause)) {
               continue;
           }
           GerritCause gc = (GerritCause)c;
           if (gc.getEvent() == event) {
               return true;
           }
       }
       return false;
   }

   /**
    * Removes any reference to the current build for this change.
    *
    * @param event the event which started the build we want to remove.
    * @return true if event was still running.
    */
   public boolean remove(ChangeBasedEvent event) {
       logger.debug("Removing future job " + event.getPatchSet().getNumber());
       return runningJobs.remove(event);
   }
}
