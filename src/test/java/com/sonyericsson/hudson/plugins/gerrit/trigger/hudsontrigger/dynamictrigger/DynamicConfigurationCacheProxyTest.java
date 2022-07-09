package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.dynamictrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerTimer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger
    .dynamictrigger.DynamicConfigurationCacheProxy.CacheEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests for {@link DynamicConfigurationCacheProxy}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ GerritDynamicUrlProcessor.class, GerritTriggerTimer.class })
public class DynamicConfigurationCacheProxyTest {

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
     * Test isExpired function.
     *
     * @throws Exception if so.
     */
    // CS IGNORE MagicNumber FOR NEXT 10 LINES. REASON: Test data.
    @Test
    public void testIsExpired() throws Exception {
        List<GerritProject> projectsMock =
            Collections.singletonList(mock(GerritProject.class));
        CacheEntry cacheEntry =
            new DynamicConfigurationCacheProxy.CacheEntry(projectsMock);

        final long nextDayTTL = System.currentTimeMillis() + 1000L * 60 * 60 * 24;
        final long lastDayTTL = System.currentTimeMillis() - 1000L * 60 * 60 * 24;

        assertTrue(cacheEntry.isExpired(nextDayTTL));
        assertFalse(cacheEntry.isExpired(lastDayTTL));
    }

    /**
     * Test cleanup function with non expired project.
     *
     * @throws Exception if so.
     */
    // CS IGNORE MagicNumber FOR NEXT 24 LINES. REASON: Test data.
    @Test
    public void testCleanup() throws Exception {
        List<GerritProject> projectsMock =
            Collections.singletonList(mock(GerritProject.class));
        DynamicConfigurationCacheProxy.Cache cache =
            new DynamicConfigurationCacheProxy.Cache();

        final long ttl = 42;

        DynamicConfigurationCacheProxy.CacheEntry cacheEntryExpired =
            new DynamicConfigurationCacheProxy.CacheEntry(projectsMock);
        Whitebox.setInternalState(cacheEntryExpired, "created", 1L);

        cache.put("foobar", cacheEntryExpired);
        cache.cleanup(ttl);

        assertNull(cache.get("foobar", anyLong()));

        DynamicConfigurationCacheProxy.CacheEntry cacheEntryNotExpired =
            new DynamicConfigurationCacheProxy.CacheEntry(projectsMock);
        Whitebox.setInternalState(cacheEntryExpired, "created", 1337L);

        cache.put("foobar", cacheEntryNotExpired);
        cache.cleanup(ttl);

        assertEquals(cache.get("foobar", ttl), projectsMock);
    }

    /**
     * Tests cache with unknown URL.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCacheGetNull() throws Exception {
        DynamicConfigurationCacheProxy.Cache cache =
            new DynamicConfigurationCacheProxy.Cache();
        assertNull(cache.get(anyString(), anyLong()));
    }

    /**
     * Tests cache with expired URL.
     *
     * @throws Exception if so.
     */
    // CS IGNORE MagicNumber FOR NEXT 12 LINES. REASON: Test data.
    @Test
    public void testCacheGetIsExpiredNull() throws Exception {
        DynamicConfigurationCacheProxy.CacheEntry entryMock =
            mock(DynamicConfigurationCacheProxy.CacheEntry.class);
        when(entryMock.isExpired(42L)).thenReturn(true);

        DynamicConfigurationCacheProxy.Cache cache =
            new DynamicConfigurationCacheProxy.Cache();
        cache.put("foobar", entryMock);

        assertNull(cache.get("foobar", 42L));
    }

    /**
     * Tests cache with known URL return list of projects.
     *
     * @throws Exception if so.
     */
    // CS IGNORE MagicNumber FOR NEXT 15 LINES. REASON: Test data.
    @Test
    public void testCacheGetNotExpiredProjects() throws Exception {
        List<GerritProject> expected = Collections.singletonList(mock(GerritProject.class));

        DynamicConfigurationCacheProxy.CacheEntry entryMock =
            mock(DynamicConfigurationCacheProxy.CacheEntry.class);
        when(entryMock.isExpired(42L)).thenReturn(false);
        when(entryMock.getProjects()).thenReturn(expected);

        DynamicConfigurationCacheProxy.Cache cache =
            new DynamicConfigurationCacheProxy.Cache();
        cache.put("foobar", entryMock);

        assertEquals(cache.get("foobar", 42L), expected);
    }

    /**
     * Tests fetch function after clear.
     *
     * @throws Exception if so.
     */
    @Test
    public void testFetchAfterClear() throws Exception {
        List<GerritProject> expected = Collections.singletonList(mock(GerritProject.class));
        GerritDynamicUrlProcessor parser = mock(GerritDynamicUrlProcessor.class);
        when(parser.fetch("someUrl")).thenReturn(expected);

        DynamicConfigurationCacheProxy instance =
            DynamicConfigurationCacheProxy.getInstance();

        assertEquals(expected, instance.fetch("someUrl", parser));
        instance.clear();
        assertEquals(expected, instance.fetch("someUrl", parser));

        verify(parser, times(2)).fetch("someUrl");
    }

    /**
     * Tests cache behavior.
     *
     * @throws Exception if so.
     */
    @Test
    public void testFetchCachePopulated() throws Exception {
        List<GerritProject> expected = Collections.singletonList(mock(GerritProject.class));
        GerritDynamicUrlProcessor parser = mock(GerritDynamicUrlProcessor.class);
        when(parser.fetch("someUrl")).thenReturn(expected);

        assertEquals(expected, DynamicConfigurationCacheProxy.getInstance()
            .fetch("someUrl", parser));
        assertEquals(expected, DynamicConfigurationCacheProxy.getInstance()
            .fetch("someUrl", parser));
        assertEquals(expected, DynamicConfigurationCacheProxy.getInstance()
            .fetch("someUrl", parser));

        verify(parser, times(1)).fetch("someUrl");
    }

    /**
     * Tests fetch parse error Exception.
     *
     * @throws Exception if so.
     */
    @Test
    public void testFetchParseErrorThrowsException() throws Exception {
        GerritDynamicUrlProcessor parser = mock(GerritDynamicUrlProcessor.class);
        when(parser.fetch(anyString())).thenThrow(new DynamicTriggerException("foobar"));

        assertThrows(DynamicTriggerException.class, () -> {
            DynamicConfigurationCacheProxy.getInstance().fetch(
                anyString(), parser);
        });
    }
}
