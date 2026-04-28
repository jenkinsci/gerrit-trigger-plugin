package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Item;
import hudson.model.Job;
import java.util.Collections;
import java.util.HashSet;
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
    * @deprecated since the memory was unifed into BuildMemory
    *
    * Constructor: embeds the trigger and it's underlying job into the tracked list.
    *
    * @param trigger - gerrit trigger that has multiple running jobs
    * @param job - underlying job of running build and triggers
    */
   @Deprecated
   public RunningJobs(GerritTrigger trigger, Item job) {
       this.trigger = trigger;
       this.job = job;
   }

   /**
    * @deprecated since the memory was unifed into BuildMemory
    *
    * @return the job
    */
   @Deprecated
   public Item getJob() {
       return job;
   }

    /**
     * @deprecated since the memory was unifed into BuildMemory
     *
     * @param job the job to set
     */
    @Deprecated
    public void setJob(Item job) {
        this.job = job;
    }

   /**
    * @deprecated since the memory was unifed into BuildMemory
    *
    * Called when trigger has cancellation policy associated with it.
    *
    *
    * @param event event that is trigger builds
    * @param jobName job name to match for specific cancellation
    * @param policy policy to decide cancelling build or not
    */
   @Deprecated
   public void cancelTriggeredJob(ChangeBasedEvent event, String jobName, BuildCancellationPolicy policy)
   {
       Jenkins jenkins = Jenkins.getInstanceOrNull();
       if (jenkins == null) {
           throw new RuntimeException("Jenkins should not be null");
       }
       ToGerritRunListener.getInstance().getMemory().cancelTriggeredJob(
               event,
               policy,
               this.trigger,
               jenkins.getItemByFullName(jobName, Job.class));
   }

   /**
    * @deprecated since the memory was unifed into BuildMemory
    *
    * Checks scheduled job and cancels current jobs if needed.
    * I.e. cancelling the old build if configured to do so and removing and storing any references.
    * Only used by Server wide policy
    *
    * @param event the event triggering a new build.
    */
   @Deprecated
   public void scheduled(ChangeBasedEvent event) {
       ToGerritRunListener.getInstance().getMemory().scheduled(event, this.trigger, (Job)this.job);
   }

    /**
     * @deprecated since the memory was unifed into BuildMemory
     *
     * Adds the event to the running jobs.
     *
     * @param event The ChangeBasedEvent.
     */
   @Deprecated
   public void add(ChangeBasedEvent event) {
       throw new RuntimeException("add method on RunningJobs should not be used. RunningJobs usage is deprecated");
   }

   /**
    * @deprecated since the memory was unifed into BuildMemory
    *
    * Removes any reference to the current build for this change.
    *
    * @param event the event which started the build we want to remove.
    * @return true if event was still running.
    */
   @Deprecated
   public boolean remove(ChangeBasedEvent event) {
       throw new RuntimeException("remove method on RunningJobs should not be used. RunningJobs usage is deprecated");
   }
}
