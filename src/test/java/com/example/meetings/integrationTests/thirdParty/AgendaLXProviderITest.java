package com.example.meetings.integrationTests.thirdParty;

import com.example.meetings.discover.AgendaLxProvider;
import com.example.meetings.discover.DiscoveredEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AgendaLxProvider}.
 *
 * WireMock stands in for the AgendaLx WordPress REST API so the tests are
 * fully offline.  We replace the provider's internal {@code RestClient} (via ReflectionTestUtils) with one pointed at the mock server after construction.
 */
public class AgendaLXProviderITest {

    private WireMockServer wireMock;
    private AgendaLxProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        provider = new AgendaLxProvider();

        // Redirect the internal RestClient to mock server
        RestClient mockClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port() + "/wp-json/agendalx/v1")
                .build();
        ReflectionTestUtils.setField(provider, "http", mockClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_returnsEvents_whenApiRespondsWithValidPayload() {
        String today    = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .withQueryParam("search", equalTo("concert"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 101,
                            "title": { "rendered": "Summer Concert" },
                            "description": ["<p>A great outdoor concert.</p>"],
                            "occurences": ["%s", "%s"],
                            "string_times": "sáb: 21h30",
                            "link": "https://www.agendalx.pt/events/101",
                            "venue": { "v1": { "name": "Parque Eduardo VII" } }
                          }
                        ]
                        """.formatted(today, tomorrow))));

        List<DiscoveredEvent> results = provider.search("concert");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.title()).isEqualTo("Summer Concert");
        assertThat(event.source()).isEqualTo("Agenda Cultural de Lisboa");
        assertThat(event.externalId()).isEqualTo("101");
        assertThat(event.url()).isEqualTo("https://www.agendalx.pt/events/101");
        assertThat(event.venue()).isEqualTo("Parque Eduardo VII");
        assertThat(event.start()).isNotNull();
    }

    @Test
    void search_stripsHtmlTags_fromDescription() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 202,
                            "title": { "rendered": "Art Exhibition" },
                            "description": ["<p>Bold <strong>art</strong> show.</p>"],
                            "occurences": ["%s"],
                            "string_times": "18h00",
                            "link": "https://www.agendalx.pt/events/202",
                            "venue": {}
                          }
                        ]
                        """.formatted(today))));

        List<DiscoveredEvent> results = provider.search("art");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).description()).doesNotContain("<p>", "<strong>", "</strong>", "</p>");
        assertThat(results.get(0).description()).contains("Bold");
        assertThat(results.get(0).description()).contains("art");
    }

    @Test
    void search_usesFallbackTime_whenStringTimesIsAbsent() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 303,
                            "title": { "rendered": "Mystery Event" },
                            "description": [],
                            "occurences": ["%s"],
                            "string_times": null,
                            "link": "https://www.agendalx.pt/events/303",
                            "venue": {}
                          }
                        ]
                        """.formatted(today))));

        List<DiscoveredEvent> results = provider.search("mystery");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).start()).isNotNull();
    }

    @Test
    void search_returnsMultipleEvents_bothIncluded() {
        String today    = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String nextWeek = LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 1,
                            "title": { "rendered": "Event Next Week" },
                            "description": [],
                            "occurences": ["%s"],
                            "string_times": "20h00",
                            "link": "https://www.agendalx.pt/events/1",
                            "venue": {}
                          },
                          {
                            "id": 2,
                            "title": { "rendered": "Event Today" },
                            "description": [],
                            "occurences": ["%s"],
                            "string_times": "20h00",
                            "link": "https://www.agendalx.pt/events/2",
                            "venue": {}
                          }
                        ]
                        """.formatted(nextWeek, today))));

        List<DiscoveredEvent> results = provider.search("event");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(DiscoveredEvent::externalId).containsExactlyInAnyOrder("1", "2");
    }

    // -----------------------------------------------------------------------
    // Description truncation
    // -----------------------------------------------------------------------

    @Test
    void search_truncatesDescriptionAt600Characters() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String longText = "A".repeat(700);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 601,
                            "title": { "rendered": "Long Description Event" },
                            "description": ["%s"],
                            "occurences": ["%s"],
                            "string_times": "20h00",
                            "link": "https://www.agendalx.pt/events/601",
                            "venue": {}
                          }
                        ]
                        """.formatted(longText, today))));

        List<DiscoveredEvent> results = provider.search("long");

        assertThat(results).hasSize(1);
        String description = results.get(0).description();
        assertThat(description).hasSizeLessThanOrEqualTo(601);
        assertThat(description).endsWith("…");
    }

    @Test
    void search_doesNotTruncateDescriptionUnder600Characters() {
        String today     = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String shortText = "B".repeat(100);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 602,
                            "title": { "rendered": "Short Description Event" },
                            "description": ["%s"],
                            "occurences": ["%s"],
                            "string_times": "20h00",
                            "link": "https://www.agendalx.pt/events/602",
                            "venue": {}
                          }
                        ]
                        """.formatted(shortText, today))));

        List<DiscoveredEvent> results = provider.search("short");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).description()).doesNotEndWith("…");
        assertThat(results.get(0).description()).contains(shortText);
    }

    // -----------------------------------------------------------------------
    // parseTime — invalid hour/minute guard
    // -----------------------------------------------------------------------

    @Test
    void search_usesFallbackTime_whenStringTimesHasOutOfRangeHour() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 701,
                            "title": { "rendered": "Bad Hour Event" },
                            "description": [],
                            "occurences": ["%s"],
                            "string_times": "25h00",
                            "link": "https://www.agendalx.pt/events/701",
                            "venue": {}
                          }
                        ]
                        """.formatted(today))));

        List<DiscoveredEvent> results = provider.search("badhour");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).start()).isNotNull();
    }

    @Test
    void search_usesFallbackTime_whenStringTimesHasOutOfRangeMinute() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 702,
                            "title": { "rendered": "Bad Minute Event" },
                            "description": [],
                            "occurences": ["%s"],
                            "string_times": "10h99",
                            "link": "https://www.agendalx.pt/events/702",
                            "venue": {}
                          }
                        ]
                        """.formatted(today))));

        List<DiscoveredEvent> results = provider.search("badminute");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).start()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Edge / error cases
    // -----------------------------------------------------------------------

    @Test
    void search_returnsEmpty_whenAllOccurrencesAreInThePast() {
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 404,
                            "title": { "rendered": "Old Event" },
                            "description": [],
                            "occurences": ["%s"],
                            "string_times": "20h00",
                            "link": "https://www.agendalx.pt/events/404",
                            "venue": {}
                          }
                        ]
                        """.formatted(yesterday))));

        List<DiscoveredEvent> results = provider.search("old");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenApiReturnsEmptyArray() {
        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(okJson("[]")));

        List<DiscoveredEvent> results = provider.search("nothing");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenApiReturns500() {
        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .willReturn(serverError()));

        List<DiscoveredEvent> results = provider.search("crash");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenConnectionFails() {
        wireMock.stop();

        List<DiscoveredEvent> results = provider.search("offline");

        assertThat(results).isEmpty();

        wireMock.start();
    }

    @Test
    void isConfigured_alwaysTrue() {
        assertThat(provider.isConfigured()).isTrue();
    }

    @Test
    void name_returnsExpectedLabel() {
        assertThat(provider.name()).isEqualTo("Agenda Cultural de Lisboa");
    }
}