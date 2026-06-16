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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MeetingControllerITest {

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
        userService.register("organizer", "organizer@example.com", "pass");
        userService.register("invitee", "invitee@example.com", "pass");
    }

    @Test
    @WithMockUser(username = "organizer")
    void getMeetingsNew_authenticated_returnsProposeView() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"));
    }

    @Test
    void getMeetingsNew_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "organizer")
    void postMeetingsNew_validData_redirectsToCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "Team Sync")
                        .param("description", "Weekly sync")
                        .param("start", "2025-09-01T10:00")
                        .param("end", "2025-09-01T11:00")
                        .param("invitees", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    @WithMockUser(username = "organizer")
    void postMeetingsNew_withInvitee_redirectsToCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "Planning Session")
                        .param("description", "")
                        .param("start", "2025-09-02T14:00")
                        .param("end", "2025-09-02T15:00")
                        .param("invitees", "invitee"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    @WithMockUser(username = "organizer")
    void postMeetingsNew_endBeforeStart_returnsProposeViewWithError() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "Bad Meeting")
                        .param("description", "")
                        .param("start", "2025-09-01T11:00")
                        .param("end", "2025-09-01T10:00")
                        .param("invitees", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void postMeetingsNew_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .param("title", "Secret")
                        .param("start", "2025-09-01T10:00")
                        .param("end", "2025-09-01T11:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "invitee")
    void postMeetingRespond_accept_redirectsToCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .with(user("organizer"))
                        .param("title", "Review")
                        .param("description", "")
                        .param("start", "2025-10-01T09:00")
                        .param("end", "2025-10-01T10:00")
                        .param("invitees", "invitee"))
                .andExpect(status().is3xxRedirection());

        Long meetingId = meetingRepository.findAll().get(0).getId();

        mockMvc.perform(post("/meetings/{id}/respond", meetingId)
                        .with(csrf())
                        .param("action", "accept"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    @WithMockUser(username = "invitee")
    void postMeetingRespond_decline_redirectsToCalendar() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .with(csrf())
                        .with(user("organizer"))
                        .param("title", "Demo")
                        .param("description", "")
                        .param("start", "2025-10-05T10:00")
                        .param("end", "2025-10-05T11:00")
                        .param("invitees", "invitee"))
                .andExpect(status().is3xxRedirection());

        Long meetingId = meetingRepository.findAll().get(0).getId();

        mockMvc.perform(post("/meetings/{id}/respond", meetingId)
                        .with(csrf())
                        .param("action", "decline"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }
}