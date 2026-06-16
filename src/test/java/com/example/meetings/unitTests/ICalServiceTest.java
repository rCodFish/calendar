package com.example.meetings.unitTests;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.service.ICalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ICalServiceTest {

    private ICalService icalService;
    private User owner;

    private final Instant start = Instant.parse("2025-09-01T10:00:00Z");
    private final Instant end   = Instant.parse("2025-09-01T11:00:00Z");

    @BeforeEach
    void setUp() {
        icalService = new ICalService();
        owner = new User("alice", "alice@example.com", "hash");
    }

    @Test
    void render_producesValidCalendarWrapper() {
        String output = icalService.render(owner, List.of());

        assertThat(output).contains("BEGIN:VCALENDAR");
        assertThat(output).contains("END:VCALENDAR");
        assertThat(output).contains("VERSION:2.0");
    }

    @Test
    void render_includesCalendarNameWithOwner() {
        String output = icalService.render(owner, List.of());

        assertThat(output).contains("X-WR-CALNAME:alice's meetings");
    }

    @Test
    void render_emptyMeetingListProducesNoVEvent() {
        String output = icalService.render(owner, List.of());

        assertThat(output).doesNotContain("BEGIN:VEVENT");
    }

    @Test
    void render_includesVEventForEachMeeting() {
        Meeting m1 = new Meeting("Standup", null, start, end, owner);
        Meeting m2 = new Meeting("Retro", null, start, end, owner);

        String output = icalService.render(owner, List.of(m1, m2));

        assertThat(output.split("BEGIN:VEVENT", -1)).hasSize(3); // 2 events + 1 leading chunk
    }

    @Test
    void render_includesSummaryAndTimes() {
        Meeting m = new Meeting("Team Sync", "Discuss Q3", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("SUMMARY:Team Sync");
        assertThat(output).contains("DTSTART:20250901T100000Z");
        assertThat(output).contains("DTEND:20250901T110000Z");
    }

    @Test
    void render_includesDescription() {
        Meeting m = new Meeting("T", "Some description here", start, end, owner);

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("DESCRIPTION:Some description here");
    }

    @Test
    void render_omitsDescriptionWhenBlank() {
        Meeting m = new Meeting("T", "  ", start, end, owner);

        String output = icalService.render(owner, List.of(m));

        assertThat(output).doesNotContain("DESCRIPTION:");
    }

    @Test
    void render_statusIsConfirmedWhenAllAccepted() {
        Meeting m = new Meeting("T", null, start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("STATUS:CONFIRMED");
    }

    @Test
    void render_statusIsTentativeWhenParticipantPending() {
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = new Meeting("T", null, start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("STATUS:TENTATIVE");
    }

    @Test
    void render_attendeePartStatReflectsStatus() {
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = new Meeting("T", null, start, end, owner);
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.DECLINED));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("PARTSTAT=DECLINED");
    }

    @Test
    void render_attendeePartStatPendingMapsToNeedsAction() {
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = new Meeting("T", null, start, end, owner);
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("PARTSTAT=NEEDS-ACTION");
    }

    @Test
    void render_escapesSemicolonInTitle() {
        Meeting m = new Meeting("Q3; Review", null, start, end, owner);

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("SUMMARY:Q3\\; Review");
    }

    @Test
    void render_escapesCommaInTitle() {
        Meeting m = new Meeting("Sync, Bob", null, start, end, owner);

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("SUMMARY:Sync\\, Bob");
    }

    @Test
    void render_usesCarriageReturnLineFeedLineEndings() {
        String output = icalService.render(owner, List.of());

        // RFC 5545 requires CRLF
        assertThat(output).contains("BEGIN:VCALENDAR\r\n");
    }
}