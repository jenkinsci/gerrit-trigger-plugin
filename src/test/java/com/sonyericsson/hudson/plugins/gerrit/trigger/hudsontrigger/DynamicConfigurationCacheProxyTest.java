package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DynamicConfigurationCacheProxy}.
 */
public class DynamicConfigurationCacheProxyTest {

    private static final long REFRESH_INTERVAL_N = -1000L;
    private static final int REFRESH_INTERNAL_P = 1000;
    private MockedStatic<GerritDynamicUrlProcessor> dynamicUrlProcessorMockedStatic;
    private MockedStatic<GerritTriggerTimer> gerritTriggerTimerMockedStatic;

    /**
     * Cleans the cache and sets mocks before every test.
     * @throws Exception if so.
     */
    @Before
    public void setUp() throws Exception {
        DynamicConfigurationCacheProxy.getInstance().clear();
        dynamicUrlProcessorMockedStatic = mockStatic(GerritDynamicUrlProcessor.class);
        gerritTriggerTimerMockedStatic = mockStatic(GerritTriggerTimer.class);
    }

    @After
    public void tearDown() throws Exception {
        dynamicUrlProcessorMockedStatic.close();
        gerritTriggerTimerMockedStatic.close();
    }

    /**
     * Sets refresh interval.
     * @param refreshInternal refresh interval for dynamic trigger
     */
    private void setRefreshInternal(long refreshInternal) {
        GerritTriggerTimer timer = mock(GerritTriggerTimer.class);
        gerritTriggerTimerMockedStatic.when(GerritTriggerTimer::getInstance).thenReturn(timer);
        when(timer.calculateAverageDynamicConfigRefreshInterval()).thenReturn(refreshInternal);
    }

    /**
     * Tests the case when cache is empty.
     * @throws Exception if so.
     */
    @Test
    public void fetchDirectlyWithoutCache() throws Exception {
        List<GerritProject> expected = Collections.singletonList(mock(GerritProject.class));
        when(GerritDynamicUrlProcessor.fetch(anyString())).thenReturn(expected);

        List<GerritProject> actual = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");

        assertEquals(expected, actual);
    }

    /**
     * Tests the case when cache is non-empty, but record is outdated.
     * @throws Exception if so.
     */
    @Test
    public void fetchDirectlyWithCache() throws Exception {
        List<GerritProject> gerritProjects1 = Collections.singletonList(mock(GerritProject.class));
        List<GerritProject> gerritProjects2 = Collections.singletonList(mock(GerritProject.class));
        dynamicUrlProcessorMockedStatic
                .when(() -> GerritDynamicUrlProcessor.fetch(anyString())).thenReturn(gerritProjects1)
                .thenReturn(gerritProjects2);
        setRefreshInternal(REFRESH_INTERVAL_N);

        List<GerritProject> res1 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");
        List<GerritProject> res2 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");

        assertNotEquals(res1, res2);
        assertEquals(gerritProjects1, res1);
        assertEquals(gerritProjects2, res2);
        dynamicUrlProcessorMockedStatic.verify(() -> GerritDynamicUrlProcessor.fetch(anyString()), times(2));
    }

    /**
     * Tests the case then cache is non-empty and record is still valid.
     * @throws Exception if so.
     */
    @Test
    public void fetchThroughCache() throws Exception {
        List<GerritProject> gerritProjects1 = Collections.singletonList(mock(GerritProject.class));
        List<GerritProject> gerritProjects2 = Collections.singletonList(mock(GerritProject.class));
        dynamicUrlProcessorMockedStatic
                .when(() -> GerritDynamicUrlProcessor.fetch(anyString()))
                .thenReturn(gerritProjects1, gerritProjects2);
        setRefreshInternal(REFRESH_INTERNAL_P);
        List<GerritProject> res1 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");
        List<GerritProject> res2 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");

        assertEquals(gerritProjects1, res1);
        assertEquals(gerritProjects1, res2);
        assertNotEquals(gerritProjects2, res2);

        dynamicUrlProcessorMockedStatic
                .verify(() -> GerritDynamicUrlProcessor.fetch(anyString()), times(1));
    }
}
