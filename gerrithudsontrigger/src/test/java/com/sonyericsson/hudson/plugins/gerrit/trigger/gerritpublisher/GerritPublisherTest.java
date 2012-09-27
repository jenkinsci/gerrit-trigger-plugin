package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritpublisher;

import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Set;

public class GerritPublisherTest extends HudsonTestCase {

    public void testRoundTrip() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        GerritPublisher publisher = new GerritPublisher("this", "that");
        project.getPublishersList().add(publisher);
        submit(createWebClient().getPage(project,"configure").getFormByName("config"));
        GerritPublisher after = project.getPublishersList().get(GerritPublisher.class);
        assertEqualBeans(publisher, after, "reviewers,ownersFileName");
    }

    public void testGetReviewersAsSet() throws Exception {
        GerritPublisher publisher = new GerritPublisher("1,2,3,4,5", "anything");
        Set<String> reviewers = publisher.getReviewersAsSet();
        assertEquals(5, reviewers.size());
        assertTrue(reviewers.contains("1"));
        assertTrue(reviewers.contains("2"));
        assertTrue(reviewers.contains("3"));
        assertTrue(reviewers.contains("4"));
        assertTrue(reviewers.contains("5"));

        // Trimming and empty elements removal
        publisher = new GerritPublisher("1 ,   2, 3,,  ,4,5   ", "anything");
        reviewers = publisher.getReviewersAsSet();
        assertEquals(5, reviewers.size());
        assertTrue(reviewers.contains("1"));
        assertTrue(reviewers.contains("2"));
        assertTrue(reviewers.contains("3"));
        assertTrue(reviewers.contains("4"));
        assertTrue(reviewers.contains("5"));

        // Avoid null as much as possible
        publisher = new GerritPublisher("", "anything");
        reviewers = publisher.getReviewersAsSet();
        assertNotNull(reviewers);
        assertTrue(reviewers.isEmpty());
    }
}
