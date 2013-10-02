package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritpublisher.GerritPublisher;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.ChangeLogSet;
import hudson.util.DescribableList;
import junit.framework.TestCase;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AbstractBuild.class)
public class FindReviewersHelperTest extends TestCase {

    public void testPerform() throws Exception {
        GerritPublisher gerritPublisher = new GerritPublisher("me@me.com, you@you.com", ".owners");

        DescribableList mockDescribableList = mock(DescribableList.class);
        when(mockDescribableList.get(GerritPublisher.class)).thenReturn(gerritPublisher);

        AbstractProject mockProject = mock(AbstractProject.class);
        when(mockProject.getPublishersList()).thenReturn(mockDescribableList);

        URL url = this.getClass().getResource("workspace");
        File f;
        try {
            f = new File(url.toURI());
        } catch(URISyntaxException e) {
            f = new File(url.getPath());
        }

        FilePath workspace = new FilePath(f);

        AbstractBuild mockBuild = PowerMockito.mock(AbstractBuild.class);

        ChangeLogSet.Entry mockChangeEntry = mock(ChangeLogSet.Entry.class);
        when(mockChangeEntry.getAffectedPaths()).thenReturn(Arrays.asList("somefile.c", "subfolder/subsubfolder/someotherfile.c"));

        final Iterator mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        when(mockIterator.next()).thenReturn(mockChangeEntry);

        ChangeLogSet changeLogSet = new ChangeLogSet(mockBuild) {
            @Override
            public boolean isEmptySet() {
                return false;
            }

            @Override
            public Iterator iterator() {
                return mockIterator;
            }
        };

        PowerMockito.when(mockBuild.getWorkspace()).thenReturn(workspace);
        when(mockBuild.getChangeSet()).thenReturn(changeLogSet);

        BuildMemory.MemoryImprint.Entry mockEntry = mock(BuildMemory.MemoryImprint.Entry.class);
        when(mockEntry.getProject()).thenReturn(mockProject);
        when(mockEntry.getBuild()).thenReturn(mockBuild);

        BuildMemory.MemoryImprint mockMemoryImprint = mock(BuildMemory.MemoryImprint.class);
        when(mockMemoryImprint.getEntries()).thenReturn(new BuildMemory.MemoryImprint.Entry[] {mockEntry});

        Set<String> reviewers = FindReviewersHelper.perform(mockMemoryImprint);
        assertNotNull(reviewers);
        assertEquals(5, reviewers.size());
        assertTrue(reviewers.contains("me@me.com"));
        assertTrue(reviewers.contains("you@you.com"));
        assertTrue(reviewers.contains("him@him.com"));
        assertTrue(reviewers.contains("she@she.com"));
        assertTrue(reviewers.contains("they@they.com"));
    }
}
