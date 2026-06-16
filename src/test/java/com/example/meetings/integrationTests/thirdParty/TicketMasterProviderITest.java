package com.example.meetings.integrationTests.thirdParty;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.TicketmasterProvider;
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
 * Integration tests for {@link TicketmasterProvider}.
 *
 * WireMock replaces the real Ticketmaster Discovery API.  The provider's
 * {@code RestClient} and {@code apiKey} fields are replaced via
 * ReflectionTestUtils so we can test both the configured and unconfigured paths.
 */
public class TicketMasterProviderITest {

    private static final String FAKE_API_KEY = "test-tm-key";

    private WireMockServer wireMock;
    private TicketmasterProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        provider = new TicketmasterProvider(FAKE_API_KEY, "PT");

        RestClient mockClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port() + "/discovery/v2")
                .build();
        ReflectionTestUtils.setField(provider, "http", mockClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_returnsEvents_whenApiRespondsWithValidPayload() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword", equalTo("rock"))
                .withQueryParam("apikey", equalTo(FAKE_API_KEY))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": [
                              {
                                "id": "tm-001",
                                "name": "Rock Night",
                                "url": "https://www.ticketmaster.com/event/tm-001",
                                "info": "An epic rock concert.",
                                "dates": {
                                  "start": { "dateTime": "2030-08-15T21:00:00Z" }
                                },
                                "_embedded": {
                                  "venues": [ { "name": "Altice Arena" } ]
                                }
                              }
                            ]
                          }
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("rock");

        assertThat(results).hasSize(1);
        DiscoveredEvent event = results.get(0);
        assertThat(event.source()).isEqualTo("Ticketmaster");
        assertThat(event.externalId()).isEqualTo("tm-001");
        assertThat(event.title()).isEqualTo("Rock Night");
        assertThat(event.description()).isEqualTo("An epic rock concert.");
        assertThat(event.url()).isEqualTo("https://www.ticketmaster.com/event/tm-001");
        assertThat(event.venue()).isEqualTo("Altice Arena");
        assertThat(event.start()).isNotNull();
    }

    @Test
    void search_returnsMultipleEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": [
                              {
                                "id": "A",
                                "name": "Event A",
                                "url": "https://ticketmaster.com/A",
                                "dates": { "start": { "dateTime": "2030-01-10T20:00:00Z" } },
                                "_embedded": { "venues": [] }
                              },
                              {
                                "id": "B",
                                "name": "Event B",
                                "url": "https://ticketmaster.com/B",
                                "dates": { "start": { "dateTime": "2030-02-20T18:00:00Z" } },
                                "_embedded": { "venues": [] }
                              }
                            ]
                          }
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("event");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(DiscoveredEvent::externalId).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void search_skipsEvents_withMissingDateTime() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": [
                              {
                                "id": "tba-1",
                                "name": "TBA Event",
                                "url": "https://ticketmaster.com/tba-1",
                                "dates": { "start": {} },
                                "_embedded": { "venues": [] }
                              }
                            ]
                          }
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("tba");

        assertThat(results).isEmpty();
    }

    @Test
    void search_handlesAbsentVenueGracefully() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": [
                              {
                                "id": "no-venue",
                                "name": "No Venue Event",
                                "url": "https://ticketmaster.com/no-venue",
                                "dates": { "start": { "dateTime": "2030-05-01T20:00:00Z" } }
                              }
                            ]
                          }
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("novenue");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).venue()).isNull();
    }

    // -----------------------------------------------------------------------
    // Malformed / unparseable dateTime
    // -----------------------------------------------------------------------

    @Test
    void search_skipsEvents_whenDateTimeIsMalformed() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .willReturn(okJson("""
                        {
                          "_embedded": {
                            "events": [
                              {
                                "id": "bad-date",
                                "name": "Malformed Date Event",
                                "url": "https://ticketmaster.com/bad-date",
                                "dates": { "start": { "dateTime": "not-a-date" } },
                                "_embedded": { "venues": [] }
                              }
                            ]
                          }
                        }
                        """)));

        List<DiscoveredEvent> results = provider.search("baddate");

        assertThat(results).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Edge / error cases
    // -----------------------------------------------------------------------

    @Test
    void search_returnsEmpty_whenEmbeddedSectionIsMissing() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .willReturn(okJson("{}")));

        List<DiscoveredEvent> results = provider.search("empty");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenApiReturns401() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .willReturn(unauthorized()));

        List<DiscoveredEvent> results = provider.search("auth");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenApiReturns500() {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
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
        TicketmasterProvider unconfigured = new TicketmasterProvider("", "PT");

        List<DiscoveredEvent> results = unconfigured.search("rock");

        assertThat(results).isEmpty();
    }

    @Test
    void isConfigured_falseWhenApiKeyIsBlank() {
        TicketmasterProvider unconfigured = new TicketmasterProvider("  ", "PT");
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_trueWhenApiKeyIsPresent() {
        assertThat(provider.isConfigured()).isTrue();
    }

    @Test
    void name_returnsExpectedLabel() {
        assertThat(provider.name()).isEqualTo("Ticketmaster");
    }
}