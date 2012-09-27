package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritpublisher.GerritPublisher;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.Set;

/**
 * A helper class to find the reviewers of a Gerrit change.
 *
 * @author Emanuele Zattin
 */
@SuppressWarnings("unchecked")
public class FindReviewersHelper {
    public static Set<String> perform(BuildMemory.MemoryImprint memoryImprint) {
        Set<String> reviewers = Sets.newHashSet();
        for (BuildMemory.MemoryImprint.Entry entry : memoryImprint.getEntries()) {
            DescribableList describableList = entry.getProject().getPublishersList();
            GerritPublisher gerritPublisher =
                    ((DescribableList<Publisher,Descriptor<Publisher>>)describableList).get(GerritPublisher.class);

            reviewers.addAll(gerritPublisher.getReviewersAsSet());

            if (!gerritPublisher.getOwnersFileName().isEmpty()) {
                // Find the files affected by the commit
                for (Object change : entry.getBuild().getChangeSet()) {
                    // generics gone wrong :(
                    if (change instanceof ChangeLogSet.Entry) {
                        ChangeLogSet.Entry changeEntry = (ChangeLogSet.Entry) change;
                        FilePath workspace = entry.getBuild().getWorkspace();
                        for (String path : changeEntry.getAffectedPaths()) {
                            FilePath filePath = new FilePath(workspace, path);
                            try {
                                // Ok, enough nesting :)
                                if (!filePath.exists()) {
                                    continue;
                                }
                                if (!filePath.isDirectory()) {
                                    if (filePath.sibling(gerritPublisher.getOwnersFileName()).exists()) {
                                        String fileContents =
                                                filePath.sibling(gerritPublisher.getOwnersFileName()).readToString();
                                        Iterable<String> reviewersInOwnerFile = Splitter.on(CharMatcher.anyOf(",;\n"))
                                                .omitEmptyStrings()
                                                .trimResults()
                                                .split(fileContents);
                                        reviewers.addAll(Sets.newHashSet(reviewersInOwnerFile));
                                        continue;
                                    } else {
                                        filePath = filePath.getParent();
                                    }
                                }
                                while (!filePath.sibling(gerritPublisher.getOwnersFileName()).exists()) {
                                    if (filePath.equals(workspace)) {
                                        continue;
                                    }
                                    filePath = filePath.getParent();
                                }
                                String fileContents =
                                        filePath.sibling(gerritPublisher.getOwnersFileName()).readToString();
                                Iterable<String> reviewersInOwnerFile = Splitter.on(CharMatcher.anyOf(",;\n"))
                                        .omitEmptyStrings()
                                        .trimResults()
                                        .split(fileContents);
                                reviewers.addAll(Sets.newHashSet(reviewersInOwnerFile));

                            } catch (InterruptedException e) {
                                throw Throwables.propagate(e);
                            } catch (IOException e) {
                                throw Throwables.propagate(e);
                            }
                        }
                    }

                }
            }
        }

        return reviewers;
    }
}
