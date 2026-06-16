package com.example.meetings.integrationTests.db;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Database integration tests for {@link MeetingParticipantRepository}.
 */
@DataJpaTest
@ActiveProfiles("test")
public class MeetingParticipantRepositoryITest {

    @Autowired
    private MeetingParticipantRepository participantRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        alice   = userRepository.save(new User("alice", "alice@example.com", "pw"));
        bob     = userRepository.save(new User("bob",   "bob@example.com",   "pw"));

        Instant start = Instant.parse("2025-06-01T10:00:00Z");
        meeting = meetingRepository.save(new Meeting("Sprint review", null, start, start.plus(1, ChronoUnit.HOURS), alice));
    }

    // --- findByUserAndStatus ---

    @Test
    void findByUserAndStatus_returnsPendingInvitesForUser() {
        participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        List<MeetingParticipant> result = participantRepository.findByUserAndStatus(bob, InviteStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMeeting().getId()).isEqualTo(meeting.getId());
    }

    @Test
    void findByUserAndStatus_acceptedStatus_doesNotReturnPending() {
        participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        List<MeetingParticipant> result = participantRepository.findByUserAndStatus(bob, InviteStatus.ACCEPTED);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserAndStatus_noInvitesForUser_returnsEmpty() {
        participantRepository.save(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));

        List<MeetingParticipant> result = participantRepository.findByUserAndStatus(bob, InviteStatus.PENDING);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserAndStatus_multipleMatchingRows_allReturned() {
        Instant start2 = Instant.parse("2025-06-02T10:00:00Z");
        Meeting meeting2 = meetingRepository.save(new Meeting("Demo", null, start2, start2.plus(1, ChronoUnit.HOURS), alice));

        participantRepository.save(new MeetingParticipant(meeting,  bob, InviteStatus.PENDING));
        participantRepository.save(new MeetingParticipant(meeting2, bob, InviteStatus.PENDING));

        List<MeetingParticipant> result = participantRepository.findByUserAndStatus(bob, InviteStatus.PENDING);

        assertThat(result).hasSize(2);
    }

    // --- findByMeetingIdAndUserId ---

    @Test
    void findByMeetingIdAndUserId_existingParticipant_returnsIt() {
        MeetingParticipant saved = participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        Optional<MeetingParticipant> result = participantRepository.findByMeetingIdAndUserId(meeting.getId(), bob.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
        assertThat(result.get().getStatus()).isEqualTo(InviteStatus.PENDING);
    }

    @Test
    void findByMeetingIdAndUserId_wrongUser_returnsEmpty() {
        participantRepository.save(new MeetingParticipant(meeting, alice, InviteStatus.ACCEPTED));

        Optional<MeetingParticipant> result = participantRepository.findByMeetingIdAndUserId(meeting.getId(), bob.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByMeetingIdAndUserId_wrongMeeting_returnsEmpty() {
        participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        Optional<MeetingParticipant> result = participantRepository.findByMeetingIdAndUserId(9999L, bob.getId());

        assertThat(result).isEmpty();
    }

    // --- status update ---

    @Test
    void setStatus_persistsNewStatus() {
        MeetingParticipant participant = participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        participant.setStatus(InviteStatus.ACCEPTED);
        participantRepository.saveAndFlush(participant);

        MeetingParticipant reloaded =
                participantRepository.findById(participant.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    // --- uniqueness constraint ---

    @Test
    void save_duplicateParticipantOnSameMeeting_throwsException() {
        participantRepository.save(new MeetingParticipant(meeting, bob, InviteStatus.PENDING));

        assertThrows(Exception.class, () -> {
            participantRepository.saveAndFlush(new MeetingParticipant(meeting, bob, InviteStatus.ACCEPTED));
        });
    }
}
