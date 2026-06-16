package com.example.meetings.integrationTests.thirdParty;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.SeatGeekProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SeatGeekProvider}.
 *
 * WireMock replaces the real SeatGeek API. The provider's {@code RestClient} is replaced via ReflectionTestUtils after construction.
 */
public class SeatGeekProviderITest {

    private static final String FAKE_CLIENT_ID = "test-sg-client-id";

    private WireMockServer wireMock;
    private SeatGeekProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        provider = new SeatGeekProvider(FAKE_CLIENT_ID);

        RestClient mockClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port() + "/2")
                .build();
        ReflectionTestUtils.setField(provider, "http", mockClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_returnsEvents_whenApiRespondsWithValidPayload() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .withQueryParam("q", equalTo("jazz"))
                .withQueryParam("client_id", equalTo(FAKE_CLIENT_ID))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 9001,
                              "title": "Jazz Evening",
                              "short_title": "Jazz Eve",
                              "datetime_utc": "2030-09-20T19:30:00",
                              "url": "https://seatgeek.com/jazz-evening",
                              "description": "A smooth jazz night.",
                              "venue": { "name": "Hot Club de Portugal" }
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("jazz");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.source()).isEqualTo("SeatGeek");
        assertThat(event.externalId()).isEqualTo("9001");
        assertThat(event.title()).isEqualTo("Jazz Evening");
        assertThat(event.description()).isEqualTo("A smooth jazz night.");
        assertThat(event.url()).isEqualTo("https://seatgeek.com/jazz-evening");
        assertThat(event.venue()).isEqualTo("Hot Club de Portugal");
        assertThat(event.start()).isNotNull();
    }

    @Test
    void search_usesShortTitle_whenTitleIsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 9002,
                              "title": null,
                              "short_title": "Short Only",
                              "datetime_utc": "2030-10-10T20:00:00",
                              "url": "https://seatgeek.com/short-only",
                              "venue": { "name": "Venue X" }
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("short");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Short Only");
    }

    @Test
    void search_parsesDatetimeUtc_asUtcInstant() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 9003,
                              "title": "UTC Check",
                              "datetime_utc": "2030-06-01T12:00:00",
                              "url": "https://seatgeek.com/utc-check",
                              "venue": { "name": "Online" }
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("utc");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).start().toString()).isEqualTo("2030-06-01T12:00:00Z");
    }

    @Test
    void search_returnsMultipleEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 1,
                              "title": "Event One",
                              "datetime_utc": "2030-01-01T20:00:00",
                              "url": "https://seatgeek.com/1",
                              "venue": { "name": "Venue A" }
                            },
                            {
                              "id": 2,
                              "title": "Event Two",
                              "datetime_utc": "2030-02-01T20:00:00",
                              "url": "https://seatgeek.com/2",
                              "venue": { "name": "Venue B" }
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("event");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(DiscoveredEvent::externalId).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void search_handlesNullVenue_gracefully() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 9004,
                              "title": "No Venue",
                              "datetime_utc": "2030-03-01T20:00:00",
                              "url": "https://seatgeek.com/no-venue"
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("novenue");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).venue()).isNull();
    }

    // -----------------------------------------------------------------------
    // Malformed / unparseable datetime_utc
    // -----------------------------------------------------------------------

    @Test
    void search_skipsEvents_whenDatetimeUtcIsMalformed() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 9010,
                              "title": "Bad Date Event",
                              "datetime_utc": "not-a-date",
                              "url": "https://seatgeek.com/bad-date",
                              "venue": { "name": "Somewhere" }
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("baddate");

        assertThat(results).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Edge / error cases
    // -----------------------------------------------------------------------

    @Test
    void search_skipsEvents_whenDatetimeUtcIsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("""
                        {
                          "events": [
                            {
                              "id": 9005,
                              "title": "No Date",
                              "url": "https://seatgeek.com/no-date",
                              "venue": { "name": "Somewhere" }
                            }
                          ]
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("nodate");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenEventsArrayIsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(okJson("{ \"events\": [] }")));

        List<DiscoveredEvent> results = provider.search("nothing");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenApiReturns401() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .willReturn(unauthorized()));

        List<DiscoveredEvent> results = provider.search("auth");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenApiReturns500() {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
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
    void search_returnsEmpty_whenProviderIsNotConfigured() {
        SeatGeekProvider unconfigured = new SeatGeekProvider("");

        List<DiscoveredEvent> results = unconfigured.search("jazz");

        assertThat(results).isEmpty();
    }

    @Test
    void isConfigured_falseWhenClientIdIsBlank() {
        SeatGeekProvider unconfigured = new SeatGeekProvider("   ");
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_trueWhenClientIdIsPresent() {
        assertThat(provider.isConfigured()).isTrue();
    }

    @Test
    void name_returnsExpectedLabel() {
        assertThat(provider.name()).isEqualTo("SeatGeek");
    }
}