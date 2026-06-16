package com.example.meetings.unitTests;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.AppUserDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppUserDetailsServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AppUserDetailsService service;

    // --- loadUserByUsername ---

    @Test
    void loadUserByUsername_returnsUserDetailsWithCorrectUsername() {
        User user = new User("alice", "alice@example.com", "hashed_pw");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
    }

    @Test
    void loadUserByUsername_returnsUserDetailsWithCorrectPasswordHash() {
        User user = new User("alice", "alice@example.com", "hashed_pw");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getPassword()).isEqualTo("hashed_pw");
    }

    @Test
    void loadUserByUsername_grantsRoleUser() {
        User user = new User("alice", "alice@example.com", "hashed_pw");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void loadUserByUsername_grantsExactlyOneAuthority() {
        User user = new User("alice", "alice@example.com", "hashed_pw");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities()).hasSize(1);
    }

    // --- loadUserByUsername --- unknown user ---

    @Test
    void loadUserByUsername_throwsUsernameNotFoundExceptionForUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_exceptionMessageContainsUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // --- repository interaction ---

    @Test
    void loadUserByUsername_delegatesToRepositoryWithExactUsername() {
        User user = new User("bob", "bob@example.com", "pw");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        service.loadUserByUsername("bob");

        verify(userRepository).findByUsername("bob");
    }
}