package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Comments {

    final Map<String, List<LineComment>> comments = new HashMap<String, List<LineComment>>();

    public Comments(List<CommentedFile> commentedFiles) {
        for(CommentedFile file : commentedFiles) {
            if(!comments.containsKey(file.getFileName())) {
                comments.put(file.getFileName(), new ArrayList<LineComment>());
            }
            comments.get(file.getFileName()).addAll(file.getLineComments());
        }
    }

}
