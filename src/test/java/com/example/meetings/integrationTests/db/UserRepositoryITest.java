package com.example.meetings.integrationTests.db;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Database integration tests for {@link UserRepository}.
 *
 * Uses @DataJpaTest which spins up an in-memory H2 database and only loads
 * JPA-related context (repositories, entities). No web layer or services involved.
 */
@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryITest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        savedUser = userRepository.save(new User("alice", "alice@example.com", "hashedpw"));
    }

    // --- findByUsername ---

    @Test
    void findByUsername_existingUser_returnsUser() {
        Optional<User> result = userRepository.findByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByUsername_unknownUsername_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("nobody");

        assertThat(result).isEmpty();
    }

    // --- findByIcalToken ---

    @Test
    void findByIcalToken_validToken_returnsUser() {
        String token = savedUser.getIcalToken();

        Optional<User> result = userRepository.findByIcalToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(savedUser.getId());
    }

    @Test
    void findByIcalToken_invalidToken_returnsEmpty() {
        Optional<User> result = userRepository.findByIcalToken("not-a-real-token");

        assertThat(result).isEmpty();
    }

    // --- existsByUsername ---

    @Test
    void existsByUsername_existingUser_returnsTrue() {
        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    @Test
    void existsByUsername_unknownUser_returnsFalse() {
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    /**
     * verify whether existsByUsername is case-sensitive.
     */
    @Test
    void existsByUsername_differentCasing_returnsFalse() {
        assertThat(userRepository.existsByUsername("Alice")).isFalse();
        assertThat(userRepository.existsByUsername("ALICE")).isFalse();
    }

    // --- uniqueness constraints ---

    @Test
    void save_duplicateUsername_throwsException() {
        User duplicate = new User("alice", "other@example.com", "pw2");

        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(duplicate);
        });
    }

    @Test
    void save_uniqueIcalTokenPerUser() {
        User bob = userRepository.save(new User("bob", "bob@example.com", "pw"));

        assertThat(savedUser.getIcalToken()).isNotEqualTo(bob.getIcalToken());
    }

    // --- basic CRUD ---

    @Test
    void save_persistsAllFields() {
        User found = userRepository.findById(savedUser.getId()).orElseThrow();

        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
        assertThat(found.getPasswordHash()).isEqualTo("hashedpw");
        assertThat(found.getIcalToken()).isNotBlank();
    }

    @Test
    void update_emailChange_isPersisted() {
        savedUser.setEmail("new@example.com");
        userRepository.saveAndFlush(savedUser);

        // Clear the first-level cache to force a SQL SELECT on the next read.
        testEntityManager.clear();

        User reloaded = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("new@example.com");
    }
}