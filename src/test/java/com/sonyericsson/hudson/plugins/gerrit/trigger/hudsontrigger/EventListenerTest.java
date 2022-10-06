package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.JenkinsAwareGerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractProject;
import hudson.model.Job;
import jenkins.model.Jenkins;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Tests for {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener}.
 */
public class EventListenerTest {

    private EventListener listener;
    private AbstractProject project;
    private GerritTrigger trigger;
    private Jenkins jenkins;
    private JenkinsAwareGerritHandler handler;
    private MockedStatic<Jenkins> jenkinsMockedStatic;
    private MockedStatic<PluginImpl> pluginMockedStatic;
    private MockedStatic<ToGerritRunListener> gerritRunListenerMockedStatic;

    /**
     * Setup all the mocks.
     */
    @Before
    public void setup() {
        project = mock(AbstractProject.class);
        doReturn("MockProject").when(project).getFullName();
        listener = new EventListener(project);
        listener = spy(listener);
        trigger = mock(GerritTrigger.class);
        doNothing().when(listener).schedule(same(trigger), any(GerritCause.class), any(GerritTriggeredEvent.class));

        jenkins = mock(Jenkins.class);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
        when(jenkins.getItemByFullName("MockProject", AbstractProject.class)).thenReturn(project);
        when(jenkins.getItemByFullName("MockProject", Job.class)).thenReturn(project);
        Setup.setTrigger(trigger, project);
        when(trigger.isInteresting(any(GerritTriggeredEvent.class))).thenReturn(true);
        handler = new JenkinsAwareGerritHandler(1);
        handler.addListener(listener);

        pluginMockedStatic = mockStatic(PluginImpl.class);
        pluginMockedStatic.when(PluginImpl::getInstance).thenReturn(null);

        ToGerritRunListener gerritRunListener = mock(ToGerritRunListener.class);
        gerritRunListenerMockedStatic = mockStatic(ToGerritRunListener.class);
        gerritRunListenerMockedStatic.when(ToGerritRunListener::getInstance).thenReturn(gerritRunListener);
    }

    @After
    public void tearDown() throws Exception {
        jenkinsMockedStatic.close();
        pluginMockedStatic.close();
        gerritRunListenerMockedStatic.close();
    }

    /**
     * Tests that {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * is called/reflected when a {@link PatchsetCreated} event arrives.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGerritEventGerritEvent() throws Exception {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated();
        handler.notifyListeners(patchsetCreated);
        verify(listener).gerritEvent(same(patchsetCreated));
        verify(listener).schedule(same(trigger), isExactClass(GerritCause.class), same(patchsetCreated));
    }

    /**
     * Tests that {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * is called/reflected when a {@link ChangeMerged} event arrives.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGerritEventChangeMerged() throws Exception {
        ChangeMerged changeMerged = Setup.createChangeMerged();
        handler.notifyListeners(changeMerged);
        verify(listener).gerritEvent(same(changeMerged));
        verify(listener).schedule(same(trigger), isExactClass(GerritCause.class), same(changeMerged));
    }


    /**
     * Tests that {@link EventListener#gerritEvent(ManualPatchsetCreated)}
     * is called/reflected when a {@link ManualPatchsetCreated} event arrives.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGerritEventManualPatchsetCreated() throws Exception {
        ManualPatchsetCreated manualPatchsetCreated = Setup.createManualPatchsetCreated();
        handler.notifyListeners(manualPatchsetCreated);
        verify(listener).gerritEvent(same(manualPatchsetCreated));
        verify(listener).schedule(same(trigger), isExactClass(GerritManualCause.class), same(manualPatchsetCreated));
    }

    /**
     * Tests that {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded)}
     * is called/reflected when a {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded} event arrives.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGerritEventCommentAdded() throws Exception {
        CommentAdded commentAdded = Setup.createCommentAdded();
        when(trigger.commentAddedMatch(same(commentAdded))).thenReturn(true);

        handler.notifyListeners(commentAdded);
        verify(listener).gerritEvent(same(commentAdded));
        verify(trigger).commentAddedMatch(same(commentAdded));
        verify(listener).schedule(same(trigger), isExactClass(GerritCause.class), same(commentAdded));
    }

    /**
     * Matcher that tests the exact class name of a method argument.
     *
     * @param klass the class name to compare to.
     *
     * @return a argThat(matcher)
     */
    private GerritCause isExactClass(final Class<? extends GerritCause> klass) {
        return argThat(new BaseMatcher<GerritCause>() {
            Class<? extends GerritCause> theClass = klass;

            @Override
            public boolean matches(Object item) {
                return item != null && item.getClass().getName().equals(theClass.getName());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Not an exact " + theClass.getName());
            }
        });
    }
}
