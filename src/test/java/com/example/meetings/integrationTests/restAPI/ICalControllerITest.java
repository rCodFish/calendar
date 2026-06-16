package com.example.meetings.integrationTests.restAPI;

import com.example.meetings.model.User;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ICalControllerITest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;

    private User user;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
        userService.register("icalUser", "ical@example.com", "pass");
        user = userRepository.findByUsername("icalUser").orElseThrow();
    }

    @Test
    void getIcalFeed_validToken_returnsCalendarContent() throws Exception {
        mockMvc.perform(get("/ical/{token}.ics", user.getIcalToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("END:VCALENDAR")));
    }

    @Test
    void getIcalFeed_invalidToken_returns404() throws Exception {
        mockMvc.perform(get("/ical/{token}.ics", "not-a-real-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getIcalFeed_noAuthRequired_accessibleWithoutLogin() throws Exception {
        mockMvc.perform(get("/ical/{token}.ics", user.getIcalToken()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "icalUser")
    void getIcalFeed_withOneMeeting_feedContainsExactlyOneVEvent() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "Board Meeting")
                        .param("description", "Quarterly review")
                        .param("start", "2025-12-01T09:00")
                        .param("end", "2025-12-01T10:00")
                        .param("invitees", ""))
                .andExpect(status().is3xxRedirection());

        MvcResult result = mockMvc.perform(get("/ical/{token}.ics", user.getIcalToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Board Meeting")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int veventCount = countOccurrences(body, "BEGIN:VEVENT");
        assertThat(veventCount)
                .as("Expected exactly 1 VEVENT in the iCal feed")
                .isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "icalUser")
    void getIcalFeed_withNoMeetings_feedContainsNoVEvents() throws Exception {
        MvcResult result = mockMvc.perform(get("/ical/{token}.ics", user.getIcalToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(countOccurrences(body, "BEGIN:VEVENT"))
                .as("Expected no VEVENTs for a user with no meetings")
                .isEqualTo(0);
    }

    @Test
    void getIcalFeed_responseHasCorrectContentDisposition() throws Exception {
        mockMvc.perform(get("/ical/{token}.ics", user.getIcalToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("meetings.ics")));
    }

    // -------------------------------------------------------------------------

    /** Counts non-overlapping occurrences of {@code substring} in {@code text}. */
    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}