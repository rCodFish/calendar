package com.example.meetings.e2eTests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Flow 2: Create Individual Meeting → Verify Meeting appears on calendar.
 *
 * The organiser auto-accepts when no invitees are given, so the meeting
 * appears as "confirmed" immediately.
 */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:cim_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class CreateIndividualMeetingE2ETest extends BaseE2ETest {

    private String user;
    private String password;

    @BeforeEach
    void setUp() {
        user     = uniqueUser("solo");
        password = "pass123";
        register(user, user + "@example.com", password);
        login(user, password);
    }

    @Test
    void createMeetingAndVerifyOnCalendar() {
        String title       = "Solo Planning Session";
        String description = "Just me, thinking deeply.";

        navigateTo("/meetings/new");
        assertThat(driver.getTitle()).contains("Propose");

        driver.findElement(By.id("title")).sendKeys(title);
        driver.findElement(By.id("description")).sendKeys(description);
        setDateTimeLocal("start", "2030-06-20T10:00");
        setDateTimeLocal("end",   "2030-06-20T11:00");

        // Verify the values were actually set before submitting
        String startVal = driver.findElement(By.id("start")).getAttribute("value");
        String endVal   = driver.findElement(By.id("end")).getAttribute("value");
        assertThat(startVal).isEqualTo("2030-06-20T10:00");
        assertThat(endVal).isEqualTo("2030-06-20T11:00");

        // Submit via JS to bypass any HTML5 validation that headless Chrome might block silently
        submitFormById("start");

        wait.until(ExpectedConditions.urlContains("/calendar"));

        assertThat(pageContains(title)).isTrue();
        assertThat(pageContains("confirmed")).isTrue();
        assertThat(pageContains(description)).isTrue();
    }

    @Test
    void createMeetingWithEndBeforeStartShowsError() {
        navigateTo("/meetings/new");

        driver.findElement(By.id("title")).sendKeys("Bad Times");
        setDateTimeLocal("start", "2030-06-20T12:00");
        setDateTimeLocal("end",   "2030-06-20T10:00");   // end before start

        submitFormById("start");

        waitForElement(By.cssSelector(".error"));
        assertThat(driver.getCurrentUrl()).contains("/meetings/new");
    }

    // -------------------------------------------------------------------------

    /**
     * Submits the form that contains the given element ID via JavaScript.
     */
    private void submitFormById(String anyFieldId) {
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById(arguments[0]).closest('form').submit();",
                anyFieldId);
    }
}