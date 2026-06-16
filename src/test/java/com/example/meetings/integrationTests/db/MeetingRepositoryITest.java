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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database integration tests for {@link MeetingRepository}.
 *
 * Focuses on the two custom JPQL queries: findCalendarMeetings and findOverlapping.
 */
@DataJpaTest
@ActiveProfiles("test")
public class MeetingRepositoryITest {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    private User alice;
    private User bob;

    private final Instant BASE = Instant.parse("2025-06-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice", "alice@example.com", "pw"));
        bob   = userRepository.save(new User("bob",   "bob@example.com",   "pw"));
    }

    // --- findCalendarMeetings ---

    @Test
    void findCalendarMeetings_organizerSeesOwnMeeting() {
        Meeting m = saveMeeting("Team standup", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Team standup");
    }

    @Test
    void findCalendarMeetings_acceptedInviteeSeesTheMeeting() {
        Meeting m = saveMeeting("Planning", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);
        addParticipant(m, bob,   InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);

        assertThat(result).hasSize(1);
    }

    @Test
    void findCalendarMeetings_pendingInviteeSeesTheMeeting() {
        Meeting m = saveMeeting("Review", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);
        addParticipant(m, bob,   InviteStatus.PENDING);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);

        assertThat(result).hasSize(1);
    }

    @Test
    void findCalendarMeetings_declinedInvitee_doesNotSeeMeeting() {
        Meeting m = saveMeeting("Review", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);
        addParticipant(m, bob,   InviteStatus.DECLINED);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);

        assertThat(result).isEmpty();
    }

    @Test
    void findCalendarMeetings_returnsOrderedByStartTime() {
        Instant t1 = BASE;
        Instant t2 = BASE.plus(2, ChronoUnit.HOURS);
        Instant t3 = BASE.plus(4, ChronoUnit.HOURS);

        Meeting m2 = saveMeeting("Second", alice, t2, t2.plus(1, ChronoUnit.HOURS));
        Meeting m1 = saveMeeting("First",  alice, t1, t1.plus(1, ChronoUnit.HOURS));
        Meeting m3 = saveMeeting("Third",  alice, t3, t3.plus(1, ChronoUnit.HOURS));
        addParticipant(m1, alice, InviteStatus.ACCEPTED);
        addParticipant(m2, alice, InviteStatus.ACCEPTED);
        addParticipant(m3, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);

        assertThat(result).extracting(Meeting::getTitle)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void findCalendarMeetings_unrelatedUserSeesNoMeetings() {
        Meeting m = saveMeeting("Private", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);

        assertThat(result).isEmpty();
    }

    // --- findOverlapping ---

    @Test
    void findOverlapping_meetingWithinWindow_isReturned() {
        // Meeting: 10:00–11:00. Window: 09:00–12:00. Should overlap.
        Meeting m = saveMeeting("Overlap", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findOverlapping(
                alice,
                BASE.minus(1, ChronoUnit.HOURS),
                BASE.plus(2, ChronoUnit.HOURS));

        assertThat(result).hasSize(1);
    }

    @Test
    void findOverlapping_meetingOutsideWindow_isNotReturned() {
        // Meeting: 10:00–11:00. Window: 12:00–13:00. No overlap.
        Meeting m = saveMeeting("NoOverlap", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findOverlapping(
                alice,
                BASE.plus(2, ChronoUnit.HOURS),
                BASE.plus(3, ChronoUnit.HOURS));

        assertThat(result).isEmpty();
    }

    @Test
    void findOverlapping_declinedMeeting_isNotReturned() {
        // Bob declined, so his slot should be free.
        Meeting m = saveMeeting("Declined", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);
        addParticipant(m, bob,   InviteStatus.DECLINED);

        List<Meeting> result = meetingRepository.findOverlapping(
                bob,
                BASE.minus(30, ChronoUnit.MINUTES),
                BASE.plus(90, ChronoUnit.MINUTES));

        assertThat(result).isEmpty();
    }

    @Test
    void findOverlapping_adjacentMeetingEndEqualsWindowStart_noOverlap() {
        // Meeting ends at 11:00; window starts at 11:00. Boundary must not count as overlap.
        Instant meetingEnd = BASE.plus(1, ChronoUnit.HOURS);
        Meeting m = saveMeeting("Adjacent", alice, BASE, meetingEnd);
        addParticipant(m, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findOverlapping(
                alice,
                meetingEnd,
                meetingEnd.plus(1, ChronoUnit.HOURS));

        assertThat(result).isEmpty();
    }

    /**
     * A participant with PENDING status must still block the slot in findOverlapping
     */
    @Test
    void findOverlapping_pendingParticipant_slotIsBlocked() {
        // Bob has a PENDING invite; the JPQL filter excludes only DECLINED, so PENDING must still count as a conflict.
        Meeting m = saveMeeting("PendingConflict", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);
        addParticipant(m, bob,   InviteStatus.PENDING);

        List<Meeting> result = meetingRepository.findOverlapping(
                bob,
                BASE.minus(30, ChronoUnit.MINUTES),
                BASE.plus(90, ChronoUnit.MINUTES));

        assertThat(result).hasSize(1);
    }

    /**
     * The query window completely contains an existing meeting (existing: 10:00–11:00, window: 09:00–12:00). The JPQL condition
     * {@code start < :end AND end > :start} handles both the "meeting inside window" and the "window inside meeting" cases.
     */
    @Test
    void findOverlapping_windowContainsMeeting_isReturned() {
        // Existing meeting: 10:00–11:00. Search window: 09:00–12:00.
        // The window is wider than the meeting; overlap must still be detected.
        Meeting m = saveMeeting("ContainedMeeting", alice, BASE, BASE.plus(1, ChronoUnit.HOURS));
        addParticipant(m, alice, InviteStatus.ACCEPTED);

        List<Meeting> result = meetingRepository.findOverlapping(
                alice,
                BASE.minus(1, ChronoUnit.HOURS),   // window start: 09:00
                BASE.plus(2, ChronoUnit.HOURS));    // window end:   12:00

        assertThat(result).hasSize(1);
    }

    // --- helpers ---

    private Meeting saveMeeting(String title, User organizer, Instant start, Instant end) {
        return meetingRepository.save(new Meeting(title, null, start, end, organizer));
    }

    private void addParticipant(Meeting meeting, User user, InviteStatus status) {
        participantRepository.save(new MeetingParticipant(meeting, user, status));
    }
}