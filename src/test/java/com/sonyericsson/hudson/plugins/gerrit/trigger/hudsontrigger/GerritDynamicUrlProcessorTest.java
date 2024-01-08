package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.List;

import static java.lang.System.lineSeparator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Test for {@link GerritDynamicUrlProcessor}.
 */
public class GerritDynamicUrlProcessorTest {

    private static final String PROJECT = "project";
    private static final String BRANCH = "branch";
    private static final String TOPIC = "topic";
    private static final String HASHTAG = "hashtag";
    private static final String FILE = "file";
    private static final String FORBIDDEN_FILE = "forbidden_file";

    /**
     * Test {@link GerritDynamicUrlProcessor#fetch(String)}}
     * <p>
     * Should throw {@link MalformedURLException} when url is null or empty.
     */
    @Test
    public void testGerritTriggerConfigUrlIsNull() {
        assertThrows(MalformedURLException.class, () -> {
            GerritDynamicUrlProcessor.fetch(null);
        });
        assertThrows(MalformedURLException.class, () -> {
            GerritDynamicUrlProcessor.fetch("");
        });
    }

    /**
     * Test {@link GerritDynamicUrlProcessor#fetch(String)} with full options.
     *
     * @throws IOException    if so.
     * @throws ParseException if so.
     */
    @Test
    public void testGerritTriggerConfigUrlFullOptions() throws IOException, ParseException {
        StringBuilder configContent = new StringBuilder();
        configContent.append(newLine("# comment"))
                .append(newLine("p~" + PROJECT))
                .append(newLine("; comment"))
                .append(newLine("b~" + BRANCH))
                .append(newLine("   "))
                .append(newLine("t~" + TOPIC))
                .append(newLine("h~" + HASHTAG))
                .append(newLine("f~" + FILE))
                .append(newLine("o~" + FORBIDDEN_FILE))
                .append(newLine("p=p1"))
                .append(newLine("b=b1"));

        List<GerritProject> projects = GerritDynamicUrlProcessor.fetch(
                generateDynamicTriggerConfigFile("testGerritTriggerConfigUrlFullOptions", configContent.toString())
        );

        assertEquals(2, projects.size());
        assertEquals(CompareType.REG_EXP, projects.get(0).getCompareType());
        assertEquals(PROJECT, projects.get(0).getPattern());
        assertEquals(CompareType.REG_EXP, projects.get(0).getBranches().get(0).getCompareType());
        assertEquals(BRANCH, projects.get(0).getBranches().get(0).getPattern());
        assertEquals(CompareType.REG_EXP, projects.get(0).getTopics().get(0).getCompareType());
        assertEquals(TOPIC, projects.get(0).getTopics().get(0).getPattern());
        assertEquals(CompareType.REG_EXP, projects.get(0).getHashtags().get(0).getCompareType());
        assertEquals(HASHTAG, projects.get(0).getHashtags().get(0).getPattern());
        assertEquals(CompareType.REG_EXP, projects.get(0).getFilePaths().get(0).getCompareType());
        assertEquals(FILE, projects.get(0).getFilePaths().get(0).getPattern());
        assertEquals(CompareType.REG_EXP, projects.get(0).getForbiddenFilePaths().get(0).getCompareType());
        assertEquals(FORBIDDEN_FILE, projects.get(0).getForbiddenFilePaths().get(0).getPattern());
        assertEquals(CompareType.PLAIN, projects.get(1).getCompareType());
        assertEquals("p1", projects.get(1).getPattern());
        assertEquals(CompareType.PLAIN, projects.get(1).getBranches().get(0).getCompareType());
        assertEquals("b1", projects.get(1).getBranches().get(0).getPattern());
    }

    /**
     * Test {@link GerritDynamicUrlProcessor#fetch(String)} with wrong content.
     *
     * @throws IOException    if so.
     * @throws ParseException if so.
     */
    @Test
    public void testGerritTriggerConfigUrlWithWrongContent() throws IOException, ParseException {
        String filename = "testGerritTriggerConfigUrlWithWrongContent";
        //Error pattern
        StringBuilder configContent = new StringBuilder();
        configContent.append(newLine("p~" + PROJECT))
                .append(newLine("a~advance"));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent.toString()));
        });

        //Error format
        StringBuilder configContent1 = new StringBuilder();
        configContent1.append(newLine("p " + PROJECT));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent1.toString()));
        });

        // branch before project
        StringBuilder configContent2 = new StringBuilder();
        configContent2.append(newLine("b~" + BRANCH))
                .append(newLine("p~" + PROJECT));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent2.toString()));
        });

        // topic before project
        StringBuilder configContent3 = new StringBuilder();
        configContent3.append(newLine("t~" + TOPIC))
                .append(newLine("p~" + PROJECT));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent3.toString()));
        });


        // hashtag before project
        StringBuilder configContent4 = new StringBuilder();
        configContent4.append(newLine("h~" + HASHTAG))
                .append(newLine("p~" + PROJECT));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent4.toString()));
        });


        // file before project
        StringBuilder configContent5 = new StringBuilder();
        configContent5.append(newLine("f~" + FILE))
                .append(newLine("p~" + PROJECT));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent5.toString()));
        });


        // forbidden file before project
        StringBuilder configContent6 = new StringBuilder();
        configContent6.append(newLine("o~" + FORBIDDEN_FILE))
                .append(newLine("p~" + PROJECT));
        assertThrows(ParseException.class, () -> {
            GerritDynamicUrlProcessor.fetch(generateDynamicTriggerConfigFile(filename, configContent6.toString()));
        });

    }

    /**
     * Generate a file with dynamic trigger config content.
     *
     * @param filename    dynamic trigger config file.
     * @param fileContent dynamic trigger config content.
     * @return file path
     * @throws IOException if so.
     */
    private String generateDynamicTriggerConfigFile(String filename, String fileContent) throws IOException {
        File file = File.createTempFile(filename, "txt");
        FileWriter fw = new FileWriter(file);
        fw.write(fileContent);
        fw.close();
        URI uri = file.toURI();
        return uri.toURL().toString();
    }

    /**
     * return string start with a new line
     * @param str input string
     * @return string start with a new line
     */
    private static String newLine(String str) {
        return lineSeparator() + str;
    }
}
