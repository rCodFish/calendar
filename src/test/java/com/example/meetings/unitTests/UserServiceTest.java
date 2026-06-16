package com.example.meetings.unitTests;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    void register_savesUserWithEncodedPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.register("alice", "alice@example.com", "secret");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed");
        verify(userRepository).save(result);
    }

    @Test
    void register_generatesIcalToken() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.register("alice", "alice@example.com", "secret");

        assertThat(result.getIcalToken()).isNotBlank();
    }

    @Test
    void register_throwsWhenUsernameAlreadyTaken() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("alice", "a@b.com", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void requireByUsername_returnsExistingUser() {
        User alice = new User("alice", "alice@example.com", "hash");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        User result = userService.requireByUsername("alice");

        assertThat(result).isSameAs(alice);
    }

    @Test
    void requireByUsername_throwsForUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requireByUsername("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown user: ghost");
    }
}