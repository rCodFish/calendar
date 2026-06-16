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

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CalendarControllerITest {

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
        userService.register("calUser", "caluser@example.com", "pass");
    }

    @Test
    @WithMockUser(username = "calUser")
    void getCalendar_authenticated_returnsCalendarView() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attributeExists("user"))
                .andExpect(model().attributeExists("meetings"))
                .andExpect(model().attributeExists("pendingInvites"))
                .andExpect(model().attributeExists("icalHttpUrl"))
                .andExpect(model().attributeExists("icalWebcalUrl"));
    }

    @Test
    void getCalendar_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "calUser")
    void getCalendar_icalUrlsHaveCorrectFormat() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("icalHttpUrl", containsString("/ical/")))
                .andExpect(model().attribute("icalHttpUrl", endsWith(".ics")))
                .andExpect(model().attribute("icalWebcalUrl", startsWith("webcal://")));
    }

    @Test
    @WithMockUser(username = "calUser")
    void getCalendar_noMeetings_meetingsListIsEmpty() throws Exception {
        // Baseline: freshly registered user has no meetings.
        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("meetings", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "calUser")
    void getCalendar_withOneMeeting_meetingsListContainsThatMeeting() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "My Event")
                        .param("description", "Details")
                        .param("start", "2025-11-01T09:00")
                        .param("end", "2025-11-01T10:00")
                        .param("invitees", ""))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("meetings", hasSize(1)));
    }
}