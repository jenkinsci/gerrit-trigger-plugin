package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.dependency.DependencyQueueTaskDispatcher;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer.ANY_SERVER;


/**
 * The Descriptor for the Trigger.
 */
@Extension
@Symbol("gerrit")
public final class GerritTriggerDescriptor extends TriggerDescriptor {

    private static final Logger logger = LoggerFactory.getLogger(GerritTriggerDescriptor.class);

    /**
     * Checks if the provided job type can support {@link GerritTrigger#getBuildUnsuccessfulFilepath()}.
     * I.e. if the job is an {@link AbstractProject}.
     *
     * @param job the job to check.
     * @return true if so.
     */
    public boolean isUnsuccessfulMessageFileSupported(Job job) {
        return job instanceof AbstractProject;
    }

    /**
     * Checks that the provided parameter is an empty string or an integer.
     *
     * @param value the value.
     * @return {@link FormValidation#validatePositiveInteger(String)}
     */
    public FormValidation doEmptyOrIntegerCheck(
            @QueryParameter("value")
            final String value) {
        if (value == null || value.length() <= 0) {
            return FormValidation.ok();
        } else {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.NotANumber());
            }
        }
    }

    /**
     * Provides auto-completion candidates for dependency jobs names.
     *
     * @param value the value.
     * @param self the current instance.
     * @param container the container.
     * @return {@link AutoCompletionCandidates}
     */
    public AutoCompletionCandidates doAutoCompleteDependencyJobsNames(@QueryParameter String value,
                                                                      @AncestorInPath Item self,
                                                                      @AncestorInPath ItemGroup container) {
        return AutoCompletionCandidates.ofJobNames(Job.class, value, self, container);
    }

    /**
     * Validates that the dependency jobs are legitimate and do not create cycles.
     *
     * @param value the string value.
     * @param project the current project.
     * @return {@link FormValidation}
     */
    public FormValidation doCheckDependencyJobsNames(@AncestorInPath Item project, @QueryParameter String value) {
        StringTokenizer tokens = new StringTokenizer(Util.fixNull(value), ",");
        // Check that all jobs are legit, actual projects.
        while (tokens.hasMoreTokens()) {
            String projectName = tokens.nextToken().trim();
            if (!projectName.equals("")) {
                Jenkins jenkins = Jenkins.get();
                Item item = jenkins.getItem(projectName, project, Item.class);
                if ((item == null) || !(item instanceof Job)) {
                    AbstractProject nearest = AbstractProject.findNearest(projectName);
                    String path = "<null>";
                    if (nearest != null) {
                        path = nearest.getFullName();
                    }
                    return FormValidation.error(
                            Messages.NoSuchJobExists(
                                    projectName,
                                    path));
                }
            }
        }
        //Check there are no cycles in the dependencies, by exploring all dependencies recursively
        //Only way of creating a cycle is if this project is in the dependencies somewhere.
        Set<Job> explored = new HashSet<Job>();
        List<Job> directDependencies = DependencyQueueTaskDispatcher.getProjectsFromString(value,
                project);
        if (directDependencies == null) {
            // no dependencies
            return FormValidation.ok();
        }
        for (Job directDependency : directDependencies) {
            if (directDependency.getFullName().equals(project.getFullName())) {
                return FormValidation.error(Messages.CannotAddSelfAsDependency());
            }
            java.util.Queue<Job> toExplore = new LinkedList<Job>();
            toExplore.add(directDependency);
            while (toExplore.size() > 0) {
                Job currentlyExploring = toExplore.remove();
                explored.add(currentlyExploring);
                GerritTrigger currentTrigger = GerritTrigger.getTrigger(currentlyExploring);
                if (currentTrigger == null) {
                    continue;
                }
                String currentDependenciesString = GerritTrigger.getTrigger(currentlyExploring).getDependencyJobsNames();
                List<Job> currentDependencies = DependencyQueueTaskDispatcher.getProjectsFromString(
                        currentDependenciesString, project);
                if (currentDependencies == null) {
                    continue;
                }
                for (Job dependency : currentDependencies) {
                    if (dependency.getFullName().equals(project.getFullName())) {
                        return FormValidation.error(Messages.AddingDependentProjectWouldCreateLoop(
                                directDependency.getFullName(), currentlyExploring.getFullName()));
                    }
                    if (!explored.contains(dependency)) {
                        toExplore.add(dependency);
                    }
                }
            }
        }
        return FormValidation.ok();
    }

    /**
     * Fill the server dropdown with the list of servers configured globally.
     *
     * @return list of servers.
     */
    public ListBoxModel doFillServerNameItems() {
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.AnyServer(), ANY_SERVER);
        List<String> serverNames = PluginImpl.getServerNames_();
        for (String s : serverNames) {
            items.add(s);
        }
        return items;
    }

    /**
     * Whether slave selection in jobs should be allowed.
     * If so, the user will see one more dropdown on the job config page, right under server selection dropdown.
     * @return true if so.
     */
    public boolean isSlaveSelectionAllowedInJobs() {
        //since we cannot create/remove drop down when the server is selected,
        //as soon as one of the server allow slave selection, we must display it.
        for (GerritServer server : PluginImpl.getServers_()) {
            ReplicationConfig replicationConfig = server.getConfig().getReplicationConfig();
            if (replicationConfig != null && replicationConfig.isEnableSlaveSelectionInJobs()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fill the Gerrit slave dropdown with the list of slaves configured with the selected server.
     * Expected to be called only when slave config is enabled at job level.
     *
     * @param serverName the name of the selected server.
     * @return list of slaves.
     */
    public ListBoxModel doFillGerritSlaveIdItems(@QueryParameter("serverName") final String serverName) {
        ListBoxModel items = new ListBoxModel();
        if (ANY_SERVER.equals(serverName)) {
            items.add(Messages.SlaveSelectionNotAllowedAnyServer(Messages.AnyServer()), "");
            return items;
        }
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server == null) {
            logger.warn(Messages.CouldNotFindServer(serverName));
            items.add(Messages.CouldNotFindServer(serverName), "");
            return items;
        }
        ReplicationConfig replicationConfig = server.getConfig().getReplicationConfig();
        if (replicationConfig == null) {
            items.add(Messages.ReplicationNotConfigured(), "");
            return items;
        } else if (!replicationConfig.isEnableReplication()) {
            items.add(Messages.ReplicationNotConfigured(), "");
            return items;
        } else if (!replicationConfig.isEnableSlaveSelectionInJobs()) {
            items.add(Messages.SlaveSelectionInJobsDisabled(), "");
            return items;
        }
        for (GerritSlave slave : replicationConfig.getGerritSlaves()) {
            //if GerritTrigger.gerritSlaveId is configured, the selected value will be the good one because of
            //the stapler/jelly magic. The problem is when job was not saved since replication was configured,
            //we want the selected slave to be the default slave defined at admin level but I did not find a way
            //to do this. Jelly support default value returned by a descriptor method but I did not find a way to
            //pass the selected server to this method.
            //To work around the issue, we always put the default slave first in the list.
            if (slave.getId().equals(replicationConfig.getDefaultSlaveId())) {
                items.add(0, new ListBoxModel.Option(slave.getName(), slave.getId()));
            } else {
                items.add(slave.getName(), slave.getId());
            }
        }
        return items;
    }

    /**
     * Checks that the provided parameter is nonempty and a valid URL.
     *
     * @param value the value.
     * @return {@link FormValidation#ok()}
     */
    public FormValidation doUrlCheck(
            @QueryParameter("value")
            final String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error(Messages.EmptyError());
        }
        try {
            URL url = new URL(value); // Check for protocol errors
            url.toURI(); // Perform some extra checking
            return FormValidation.ok();
        } catch (MalformedURLException | URISyntaxException e) {
            return FormValidation.error(Messages.BadUrlError());
        }
    }

    /**
     * Fill the dropdown for notification levels.
     * @param serverName the server name.
     * @return the values.
     */
    public ListBoxModel doFillNotificationLevelItems(@QueryParameter("serverName") final String serverName) {
        Map<Notify, String> levelTextsById = GerritServer.notificationLevelTextsById();
        ListBoxModel items = new ListBoxModel(levelTextsById.size() + 1);
        items.add(getOptionForNotificationLevelDefault(serverName, levelTextsById));
        for (Map.Entry<Notify, String> level : levelTextsById.entrySet()) {
            items.add(new ListBoxModel.Option(level.getValue(), level.getKey().toString()));
        }
        return items;
    }

    /**
     * Reads the default option for the notification level, usually from the server config.
     *
     * @param serverName the server name.
     * @param levelTextsById a map with the localized level texts.
     * @return the default option.
     */
    private static ListBoxModel.Option getOptionForNotificationLevelDefault(
            final String serverName, Map<Notify, String> levelTextsById) {
        if (ANY_SERVER.equals(serverName)) {
            // We do not know which server is selected, so we cannot tell the
            // currently active default value.  It might be the global default,
            // but also a different value.
            return new ListBoxModel.Option(Messages.NotificationLevel_DefaultValue(), "");
        } else if (serverName != null) {
            GerritServer server = PluginImpl.getServer_(serverName);
            if (server != null) {
                Notify level = server.getConfig().getNotificationLevel();
                if (level != null) {
                    String levelText = levelTextsById.get(level);
                    if (levelText == null) { // new/unknown value
                        levelText = level.toString();
                    }
                    return new ListBoxModel.Option(Messages.NotificationLevel_DefaultValueFromServer(levelText), "");
                }
            }
        }

        // fall back to global default
        String defaultText = levelTextsById.get(Config.DEFAULT_NOTIFICATION_LEVEL);
        return new ListBoxModel.Option(Messages.NotificationLevel_DefaultValueFromServer(defaultText), "");
    }

    /**
     * Default Constructor.
     */
    public GerritTriggerDescriptor() {
        super(GerritTrigger.class);
    }

    @Override
    public boolean isApplicable(Item item) {
        return (item instanceof ParameterizedJobMixIn.ParameterizedJob);
    }

    @Override
    public String getDisplayName() {
        return Messages.TriggerDisplayName();
    }

    @Override
    public String getHelpFile() {
        return "/plugin/gerrit-trigger/help-whatIsGerritTrigger.html";
    }

    /**
     * A list of CompareTypes for the UI.
     *
     * @return A list of CompareTypes
     */
    public CompareType[] getCompareTypes() {
        return CompareType.values();
    }

    /**
     * Getter for the list of PluginGerritEventDescriptors.
     * @return the list.
     */
    public List<PluginGerritEvent.PluginGerritEventDescriptor> getGerritEventDescriptors() {
        ExtensionList<PluginGerritEvent.PluginGerritEventDescriptor> extensionList =
                Jenkins.get().getExtensionList(PluginGerritEvent.PluginGerritEventDescriptor.class);
        return extensionList;
    }
}
