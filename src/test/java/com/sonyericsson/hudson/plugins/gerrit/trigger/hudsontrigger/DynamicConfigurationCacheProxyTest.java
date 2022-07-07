package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

/**
 * Tests for {@link DynamicConfigurationCacheProxy}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ GerritDynamicUrlProcessor.class, GerritTriggerTimer.class })
public class DynamicConfigurationCacheProxyTest {

    private static final long FORCE_REFRESH_INTERVAL = -1000L;

    /**
     * Cleans the cache and sets mocks before every test.
     * @throws Exception if so.
     */
    @Before
    public void setUp() throws Exception {
        DynamicConfigurationCacheProxy.getInstance().clear();
        PowerMockito.mockStatic(GerritDynamicUrlProcessor.class);
    }

    /**
     * Sets refresh interval.
     * @param refreshInternal refresh interval for dynamic trigger
     */
    private void setRefreshInternal(long refreshInternal) {
        PowerMockito.mockStatic(GerritTriggerTimer.class);
        GerritTriggerTimer timer = mock(GerritTriggerTimer.class);
        when(GerritTriggerTimer.getInstance()).thenReturn(timer);
        when(timer.calculateAverageDynamicConfigRefreshInterval()).thenReturn(refreshInternal);
    }

    /**
     * Test CacheEntry if entry is expired
     */
    @Test
    public void testCacheEntryIsExpired() {
        final long ttl = 1337;
        final long created = 42;
        List<GerritProject> projects = Collections.singletonList(mock(GerritProject.class));
        DynamicConfigurationCacheProxy proxy = DynamicConfigurationCacheProxy.getInstance();
        DynamicConfigurationCacheProxy.CacheEntry cacheEntry = proxy.new CacheEntry(projects);
        Whitebox.setInternalState(cacheEntry, "created", created);
        assertTrue(cacheEntry.isExpired(ttl));
    }

    /**
     * Test CacheEntry if entry is not expired
     */
    @Test
    public void testCacheEntryIsNotExpired() {
        final long ttl = 42;
        final long created = 1337;
        List<GerritProject> projects = Collections.singletonList(mock(GerritProject.class));
        DynamicConfigurationCacheProxy proxy = DynamicConfigurationCacheProxy.getInstance();
        DynamicConfigurationCacheProxy.CacheEntry cacheEntry = proxy.new CacheEntry(projects);
        Whitebox.setInternalState(cacheEntry, "created", created);
        assertFalse(cacheEntry.isExpired(ttl));
    }

    /**
     * Test CacheEntry getProjects
     */
    @Test
    public void testCacheEntryGetProjects() {
        List<GerritProject> projects = Collections.singletonList(mock(GerritProject.class));
        DynamicConfigurationCacheProxy proxy = DynamicConfigurationCacheProxy.getInstance();
        DynamicConfigurationCacheProxy.CacheEntry cacheEntry = proxy.new CacheEntry(projects);
        assertEquals(projects, cacheEntry.getProjects());
    }

    /**
     * Test Cache get item is empty and returns null
     */
    @Test
    public void testCacheGetEmptyProjectList() {
        DynamicConfigurationCacheProxy proxy = DynamicConfigurationCacheProxy.getInstance();
        DynamicConfigurationCacheProxy.Cache cache = proxy.new Cache();
        assertNull(cache.get(anyString(), anyLong()));
    }

    /**
     * Tests the case when cache is empty.
     * @throws Exception if so.
     */
    @Test
    public void testFetchDirectlyWithoutCache() throws Exception {
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
    public void testFetchDirectlyWithCache() throws Exception {
        List<GerritProject> gerritProjects1 = Collections.singletonList(mock(GerritProject.class));
        List<GerritProject> gerritProjects2 = Collections.singletonList(mock(GerritProject.class));
        when(GerritDynamicUrlProcessor.fetch(anyString())).thenReturn(gerritProjects1).thenReturn(gerritProjects2);
        setRefreshInternal(FORCE_REFRESH_INTERVAL);

        List<GerritProject> res1 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");
        List<GerritProject> res2 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");

        assertNotEquals(res1, res2);
        assertEquals(gerritProjects1, res1);
        assertEquals(gerritProjects2, res2);
        verifyStatic(times(2));
    }

    /**
     * Tests the case then cache is non-empty and record is still valid.
     * @throws Exception if so.
     */
    @Test
    public void testFetchThroughCache() throws Exception {
        PowerMockito.mockStatic(GerritDynamicUrlProcessor.class);
        List<GerritProject> gerritProjects1 = Collections.singletonList(mock(GerritProject.class));
        List<GerritProject> gerritProjects2 = Collections.singletonList(mock(GerritProject.class));
        when(GerritDynamicUrlProcessor.fetch(anyString())).thenReturn(gerritProjects1).thenReturn(gerritProjects2);

        List<GerritProject> res1 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");
        List<GerritProject> res2 = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache("someUrl");

        assertEquals(gerritProjects1, res1);
        assertEquals(gerritProjects1, res2);
        assertNotEquals(gerritProjects2, res2);

        verifyStatic();
    }
}
