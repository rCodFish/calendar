package com.example.meetings.unitTests;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class DiscoveryServiceTest {

    private static final Instant T1 = Instant.parse("2025-09-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2025-09-02T10:00:00Z");
    private static final Instant T3 = Instant.parse("2025-09-03T10:00:00Z");

    private DiscoveredEvent event(String source, String id, String url, Instant start) {
        return new DiscoveredEvent(source, id, "Title", null, start, null, url, null);
    }

    // --- blank / null query ---

    @Test
    void search_returnsEmptyForNullQuery() {
        DiscoveryService service = new DiscoveryService(List.of());
        assertThat(service.search(null)).isEmpty();
    }

    @Test
    void search_returnsEmptyForBlankQuery() {
        DiscoveryService service = new DiscoveryService(List.of());
        assertThat(service.search("   ")).isEmpty();
    }

    // --- provider skipping ---

    @Test
    void search_skipsUnconfiguredProviders() {
        EventProvider unconfigured = mock(EventProvider.class);
        when(unconfigured.isConfigured()).thenReturn(false);

        DiscoveryService service = new DiscoveryService(List.of(unconfigured));
        List<DiscoveredEvent> results = service.search("concert");

        verify(unconfigured, never()).search(anyString());
        assertThat(results).isEmpty();
    }

    @Test
    void search_queriesConfiguredProviders() {
        EventProvider provider = mock(EventProvider.class);
        when(provider.isConfigured()).thenReturn(true);
        when(provider.search("concert")).thenReturn(List.of(event("P", "1", "http://a.com", T1)));

        DiscoveryService service = new DiscoveryService(List.of(provider));

        assertThat(service.search("concert")).hasSize(1);
        verify(provider).search("concert");
    }

    // --- dedup by URL ---

    @Test
    void search_deduplicatesEventsByUrl() {
        DiscoveredEvent e1 = event("P1", "1", "http://same.com", T1);
        DiscoveredEvent e2 = event("P2", "2", "http://same.com", T2);

        EventProvider p1 = mockConfiguredProvider(List.of(e1));
        EventProvider p2 = mockConfiguredProvider(List.of(e2));

        DiscoveryService service = new DiscoveryService(List.of(p1, p2));

        assertThat(service.search("anything")).hasSize(1);
    }

    @Test
    void search_deduplicatesEventsBySourceAndIdWhenUrlNull() {
        DiscoveredEvent e1 = new DiscoveredEvent("P", "42", "Show", null, T1, null, null, null);
        DiscoveredEvent e2 = new DiscoveredEvent("P", "42", "Show", null, T2, null, null, null);

        EventProvider p1 = mockConfiguredProvider(List.of(e1));
        EventProvider p2 = mockConfiguredProvider(List.of(e2));

        DiscoveryService service = new DiscoveryService(List.of(p1, p2));

        assertThat(service.search("show")).hasSize(1);
    }

    @Test
    void search_doesNotDeduplicateDifferentUrls() {
        DiscoveredEvent e1 = event("P", "1", "http://a.com", T1);
        DiscoveredEvent e2 = event("P", "2", "http://b.com", T2);

        EventProvider provider = mockConfiguredProvider(List.of(e1, e2));

        DiscoveryService service = new DiscoveryService(List.of(provider));

        assertThat(service.search("x")).hasSize(2);
    }

    // --- sort order ---

    @Test
    void search_resultsSortedByStartTimeAscending() {
        DiscoveredEvent e1 = event("P", "1", "http://a.com", T3);
        DiscoveredEvent e2 = event("P", "2", "http://b.com", T1);
        DiscoveredEvent e3 = event("P", "3", "http://c.com", T2);

        EventProvider provider = mockConfiguredProvider(List.of(e1, e2, e3));
        DiscoveryService service = new DiscoveryService(List.of(provider));

        List<DiscoveredEvent> results = service.search("x");

        assertThat(results).extracting(DiscoveredEvent::start)
                .containsExactly(T1, T2, T3);
    }

    // --- merging multiple providers ---

    @Test
    void search_mergesResultsFromMultipleProviders() {
        EventProvider p1 = mockConfiguredProvider(List.of(event("P1", "1", "http://a.com", T1)));
        EventProvider p2 = mockConfiguredProvider(List.of(event("P2", "2", "http://b.com", T2)));

        DiscoveryService service = new DiscoveryService(List.of(p1, p2));

        assertThat(service.search("x")).hasSize(2);
    }

    // --- helpers ---

    private EventProvider mockConfiguredProvider(List<DiscoveredEvent> events) {
        EventProvider p = mock(EventProvider.class);
        when(p.isConfigured()).thenReturn(true);
        when(p.search(anyString())).thenReturn(events);
        return p;
    }
}