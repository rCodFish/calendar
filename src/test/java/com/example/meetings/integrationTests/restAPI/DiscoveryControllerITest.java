package com.example.meetings.integrationTests.restAPI;

import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DiscoveryControllerITest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
        userService.register("discoverUser", "discover@example.com", "pass");
    }

    @Test
    @WithMockUser(username = "discoverUser")
    void getDiscover_noQuery_returnsDiscoverViewWithEmptyResults() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attributeExists("providers"))
                .andExpect(model().attributeExists("anyConfigured"))
                .andExpect(model().attribute("q", ""))
                .andExpect(model().attribute("results", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "discoverUser")
    void getDiscover_withQuery_returnsDiscoverViewWithQueryInModel() throws Exception {
        mockMvc.perform(get("/discover").param("q", "concert"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("q", "concert"))
                .andExpect(model().attributeExists("results"));
    }

    @Test
    @WithMockUser(username = "discoverUser")
    void getDiscover_blankQuery_returnsEmptyResults() throws Exception {
        mockMvc.perform(get("/discover").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("results", hasSize(0)));
    }

    @Test
    void getDiscover_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/discover"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "discoverUser")
    void postDiscoverCopy_validEvent_redirectsToCalendar() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/discover/copy")
                        .with(csrf())
                        .param("source", "ticketmaster")
                        .param("externalId", "ext-001")
                        .param("title", "Rock Concert")
                        .param("description", "An awesome concert")
                        .param("start", start.toString())
                        .param("end", end.toString())
                        .param("url", "https://example.com/event")
                        .param("venue", "Lisbon Arena"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    @WithMockUser(username = "discoverUser")
    void postDiscoverCopy_noEndOrOptionals_redirectsToCalendar() throws Exception {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        mockMvc.perform(post("/discover/copy")
                        .with(csrf())
                        .param("source", "agendacultural")
                        .param("externalId", "ext-002")
                        .param("title", "Art Exhibition")
                        .param("start", start.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    @WithMockUser(username = "discoverUser")
    void postDiscoverCopy_missingRequiredStartParam_returns400() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .with(csrf())
                        .param("source", "ticketmaster")
                        .param("externalId", "ext-missing-start")
                        .param("title", "No Start Event"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postDiscoverCopy_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .with(csrf())
                        .param("source", "ticketmaster")
                        .param("externalId", "ext-003")
                        .param("title", "Event")
                        .param("start", Instant.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}