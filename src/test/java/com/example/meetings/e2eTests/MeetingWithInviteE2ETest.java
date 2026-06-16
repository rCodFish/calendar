package com.example.meetings.e2eTests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Flow 3: Create Meeting with invites → Login as invitee → Accept or Decline →
 * Verify meeting state for both parties.
 */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:mwi_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class MeetingWithInviteE2ETest extends BaseE2ETest {

    private String organiser;
    private String organiserPw;
    private String invitee;
    private String inviteePw;
    private String meetingTitle;

    @BeforeEach
    void createUsers() {
        organiser    = uniqueUser("org");
        organiserPw  = "orgpass";
        invitee      = uniqueUser("inv");
        inviteePw    = "invpass";
        meetingTitle = "Team Sync " + organiser;

        register(organiser, organiser + "@example.com", organiserPw);
        register(invitee,   invitee   + "@example.com", inviteePw);
    }

    @Test
    void organiserInvitesUserWhoAcceptsAndMeetingBecomesConfirmed() {

        // ---- Organiser creates meeting with an invite ----
        login(organiser, organiserPw);
        navigateTo("/meetings/new");

        driver.findElement(By.id("title")).sendKeys(meetingTitle);
        setDateTimeLocal("start", "2030-07-10T14:00");
        setDateTimeLocal("end",   "2030-07-10T15:00");
        driver.findElement(By.id("invitees")).sendKeys(invitee);

        submitFormContaining("title");

        wait.until(ExpectedConditions.urlContains("/calendar"));

        assertThat(pageContains(meetingTitle)).isTrue();
        assertThat(pageContains("tentative")).isTrue();

        logout();

        // ---- Invitee sees pending invite ----
        login(invitee, inviteePw);

        waitForElement(By.cssSelector(".invite"));
        assertThat(pageContains(meetingTitle)).isTrue();
        assertThat(pageContains("pending")).isTrue();

        // ---- Invitee accepts ----
        List<WebElement> invites = driver.findElements(By.cssSelector(".invite"));
        WebElement targetInvite = invites.stream()
                .filter(el -> el.getText().contains(meetingTitle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invite not found: " + meetingTitle));

        targetInvite.findElement(
                        By.xpath(".//input[@value='accept']/parent::form//button[@type='submit']"))
                .click();

        wait.until(ExpectedConditions.urlContains("/calendar"));

        assertThat(pageContains(meetingTitle)).isTrue();
        assertThat(pageContains("confirmed")).isTrue();

        logout();

        // ---- Organiser also sees confirmed ----
        login(organiser, organiserPw);
        assertThat(pageContains(meetingTitle)).isTrue();
        assertThat(pageContains("confirmed")).isTrue();
    }

    @Test
    void inviteeWhoDeclinesMeetingNoLongerSeesItOnTheirCalendar() {

        // ---- Organiser creates meeting with an invite ----
        login(organiser, organiserPw);
        navigateTo("/meetings/new");

        driver.findElement(By.id("title")).sendKeys(meetingTitle);
        setDateTimeLocal("start", "2030-08-05T10:00");
        setDateTimeLocal("end",   "2030-08-05T11:00");
        driver.findElement(By.id("invitees")).sendKeys(invitee);

        submitFormContaining("title");

        wait.until(ExpectedConditions.urlContains("/calendar"));
        logout();

        // ---- Invitee declines ----
        login(invitee, inviteePw);

        waitForElement(By.cssSelector(".invite"));

        List<WebElement> invites = driver.findElements(By.cssSelector(".invite"));
        WebElement targetInvite = invites.stream()
                .filter(el -> el.getText().contains(meetingTitle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invite not found: " + meetingTitle));

        targetInvite.findElement(
                        By.xpath(".//input[@value='decline']/parent::form//button[@type='submit']"))
                .click();

        wait.until(ExpectedConditions.urlContains("/calendar"));

        // ---- Meeting must not appear anywhere on invitee's calendar ----
        assertThat(pageContains(meetingTitle)).isFalse();
    }

    @Test
    void afterInviteeDeclinesMeetingRemainsOnOrganiserCalendar() {

        // ---- Organiser creates meeting with an invite ----
        login(organiser, organiserPw);
        navigateTo("/meetings/new");

        driver.findElement(By.id("title")).sendKeys(meetingTitle);
        setDateTimeLocal("start", "2030-08-12T15:00");
        setDateTimeLocal("end",   "2030-08-12T16:00");
        driver.findElement(By.id("invitees")).sendKeys(invitee);

        submitFormContaining("title");

        wait.until(ExpectedConditions.urlContains("/calendar"));
        logout();

        // ---- Invitee declines ----
        login(invitee, inviteePw);

        waitForElement(By.cssSelector(".invite"));

        List<WebElement> invites = driver.findElements(By.cssSelector(".invite"));
        WebElement targetInvite = invites.stream()
                .filter(el -> el.getText().contains(meetingTitle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invite not found: " + meetingTitle));

        targetInvite.findElement(
                        By.xpath(".//input[@value='decline']/parent::form//button[@type='submit']"))
                .click();

        wait.until(ExpectedConditions.urlContains("/calendar"));
        logout();

        // ---- Organiser still sees the meeting ----
        login(organiser, organiserPw);
        assertThat(pageContains(meetingTitle)).isTrue();
        assertThat(pageContains("tentative")).isTrue();
        assertThat(pageContains("confirmed")).isFalse();
    }

    @Test
    void invitingNonExistentUserShowsError() {
        login(organiser, organiserPw);
        navigateTo("/meetings/new");

        driver.findElement(By.id("title")).sendKeys("Ghost Meeting");
        setDateTimeLocal("start", "2030-07-10T14:00");
        setDateTimeLocal("end",   "2030-07-10T15:00");
        driver.findElement(By.id("invitees")).sendKeys("ghost_user_xyz_404");

        submitFormContaining("title");

        waitForElement(By.cssSelector(".error"));
        assertThat(pageContains("Unknown invitee")).isTrue();
    }

    // -------------------------------------------------------------------------

    /**
     * Submits the form containing the given field via JavaScript
     */
    private void submitFormContaining(String anyFieldId) {
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById(arguments[0]).closest('form').submit();",
                anyFieldId);
    }
}