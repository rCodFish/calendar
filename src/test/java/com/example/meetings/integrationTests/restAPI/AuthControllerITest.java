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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerITest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;

    @BeforeEach
    void setUp() {
        // Must delete in FK order
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getLogin_returnsLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void getRegister_returnsRegisterPage() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void postRegister_validData_redirectsToLoginWithRegisteredParam() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "alice")
                        .param("email", "alice@example.com")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
    }

    @Test
    void postRegister_duplicateUsername_returnsRegisterViewWithError() throws Exception {
        userService.register("bob", "bob@example.com", "pass");

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "bob")
                        .param("email", "bob2@example.com")
                        .param("password", "other"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("username", "bob"))
                .andExpect(model().attribute("email", "bob2@example.com"));
    }

    @Test
    @WithMockUser(username = "rootUser")
    void getRoot_redirectsToCalendar_whenAuthenticated() throws Exception {
        userService.register("rootUser", "root@example.com", "pass");

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    void getRoot_redirectsToCalendar_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    void postLogin_validCredentials_redirectsToCalendar() throws Exception {
        userService.register("carol", "carol@example.com", "mypassword");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "carol")
                        .param("password", "mypassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    void postLogin_invalidCredentials_redirectsToLoginWithError() throws Exception {
        userService.register("dave", "dave@example.com", "rightpass");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "dave")
                        .param("password", "wrongpass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?error*"));
    }
}