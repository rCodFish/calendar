package com.example.meetings.unitTests;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeetingServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock MeetingParticipantRepository participantRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    MeetingService meetingService;

    private User organizer;
    private final Instant start = Instant.parse("2025-09-01T10:00:00Z");
    private final Instant end   = Instant.parse("2025-09-01T11:00:00Z");

    @BeforeEach
    void setUp() {
        organizer = new User("alice", "alice@example.com", "hash");
    }

    // --- propose ---

    @Test
    void propose_savesAndReturnsNewMeeting() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.propose(organizer, "Standup", "Daily sync", start, end, List.of());

        assertThat(result.getTitle()).isEqualTo("Standup");
        assertThat(result.getOrganizer()).isEqualTo(organizer);
        verify(meetingRepository).save(result);
    }

    @Test
    void propose_organizerAutoAccepted() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.propose(organizer, "T", "D", start, end, List.of());

        assertThat(result.getParticipants())
                .anyMatch(p -> p.getUser().equals(organizer) && p.getStatus() == InviteStatus.ACCEPTED);
    }

    @Test
    void propose_inviteeAddedAsPending() {
        User bob = new User("bob", "bob@example.com", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.propose(organizer, "T", "D", start, end, List.of("bob"));

        assertThat(result.getParticipants())
                .anyMatch(p -> p.getUser().equals(bob) && p.getStatus() == InviteStatus.PENDING);
    }

    @Test
    void propose_throwsWhenEndNotAfterStart() {
        assertThatThrownBy(() ->
                meetingService.propose(organizer, "T", "D", end, start, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void propose_throwsWhenInviteeUnknown() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                meetingService.propose(organizer, "T", "D", start, end, List.of("ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown invitee: ghost");
    }

    @Test
    void propose_skipsDuplicateInvitees() {
        User bob = new User("bob", "bob@example.com", "hash");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.propose(organizer, "T", "D", start, end, List.of("bob", "bob"));

        long bobCount = result.getParticipants().stream()
                .filter(p -> p.getUser().equals(bob)).count();
        assertThat(bobCount).isEqualTo(1);
    }

    @Test
    void propose_organizerNotAddedTwiceWhenInInviteeList() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.propose(organizer, "T", "D", start, end, List.of("alice"));

        long aliceCount = result.getParticipants().stream()
                .filter(p -> p.getUser().equals(organizer)).count();
        assertThat(aliceCount).isEqualTo(1);
    }

    @Test
    void propose_ignoresBlankAndNullInvitees() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.propose(organizer, "T", "D", start, end, List.of("  ", ""));

        assertThat(result.getParticipants()).hasSize(1);
        verify(userRepository, never()).findByUsername(any());
    }

    // ── respond ─────────────────────────────────────────────────────────────

    @Test
    void respond_updatesParticipantStatus() {
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting meeting = new Meeting("T", "D", start, end, organizer);
        MeetingParticipant participant = new MeetingParticipant(meeting, bob, InviteStatus.PENDING);

        when(participantRepository.findByMeetingIdAndUserId(1L, bob.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, bob, InviteStatus.ACCEPTED);

        assertThat(participant.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    void respond_throwsForPendingStatus() {
        assertThatThrownBy(() ->
                meetingService.respond(1L, organizer, InviteStatus.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Response must be ACCEPTED or DECLINED");
    }

    @Test
    void respond_throwsWhenNoInviteFound() {
        when(participantRepository.findByMeetingIdAndUserId(anyLong(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                meetingService.respond(99L, organizer, InviteStatus.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No invite found");
    }

    // --- copyFromDiscovered ---

    @Test
    void copyFromDiscovered_usesEventEndWhenPresent() {
        Instant eventEnd = end;
        DiscoveredEvent event = new DiscoveredEvent("TM", "1", "Concert", null, start, eventEnd, null, "Arena");
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertThat(result.getEndTime()).isEqualTo(eventEnd);
    }

    @Test
    void copyFromDiscovered_defaultsTwoHoursWhenEndNull() {
        DiscoveredEvent event = new DiscoveredEvent("TM", "1", "Concert", null, start, null, null, null);
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertThat(result.getEndTime()).isEqualTo(start.plusSeconds(7200));
    }

    @Test
    void copyFromDiscovered_organizerIsAutoAccepted() {
        DiscoveredEvent event = new DiscoveredEvent("TM", "1", "Concert", null, start, end, null, null);
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertThat(result.getParticipants())
                .anyMatch(p -> p.getUser().equals(organizer) && p.getStatus() == InviteStatus.ACCEPTED);
    }

    @Test
    void copyFromDiscovered_descriptionIncludesSourceAndVenue() {
        DiscoveredEvent event = new DiscoveredEvent("Ticketmaster", "42", "Concert",
                "Awesome show", start, end, "http://example.com", "Madison Square Garden");
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertThat(result.getDescription())
                .contains("Ticketmaster")
                .contains("Madison Square Garden")
                .contains("Awesome show");
    }

    // --- calendarForIcalToken ---

    @Test
    void calendarForIcalToken_throwsOnInvalidToken() {
        when(userRepository.findByIcalToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.calendarForIcalToken("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid iCal token");
    }
}