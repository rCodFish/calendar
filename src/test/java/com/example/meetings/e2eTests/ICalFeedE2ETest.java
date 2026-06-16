package com.example.meetings.e2eTests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Flow 4: iCal feed.
 *
 * The iCal link on the calendar page is built from app.base-url which defaults
 * to http://localhost:8080. HttpURLConnection must therefore have its URL
 * rewritten to the actual random test port before connecting, otherwise it
 * gets a Connection refused on 8080.
 */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ical_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class ICalFeedE2ETest extends BaseE2ETest {

    private String user;
    private String password;

    @BeforeEach
    void setUp() {
        user     = uniqueUser("ical");
        password = "icalpass";
        register(user, user + "@example.com", password);
        login(user, password);
    }

    @Test
    void calendarPageShowsIcalLinks() {
        navigateTo("/calendar");

        assertThat(pageContains("webcal://")).isTrue();
        assertThat(pageContains(".ics")).isTrue();

        WebElement downloadLink = driver.findElement(By.cssSelector("a[href*='.ics']"));
        assertThat(downloadLink.isDisplayed()).isTrue();
    }

    @Test
    void icalFeedContainsMeetingAfterCreation() throws Exception {
        navigateTo("/meetings/new");
        driver.findElement(By.id("title")).sendKeys("ICal Test Meeting");
        setDateTimeLocal("start", "2030-09-01T09:00");
        setDateTimeLocal("end",   "2030-09-01T10:00");

        ((JavascriptExecutor) driver).executeScript("document.getElementById('title').closest('form').submit();");

        wait.until(ExpectedConditions.urlContains("/calendar"));

        WebElement icsLink = driver.findElement(By.cssSelector("a[href*='.ics']"));
        String icsUrl = toTestUrl(icsLink.getAttribute("href"));
        assertThat(icsUrl).isNotBlank();

        HttpURLConnection conn = openNoRedirect(icsUrl);
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getContentType()).contains("text/calendar");

        try (InputStream is = conn.getInputStream()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("BEGIN:VCALENDAR");
            assertThat(body).contains("BEGIN:VEVENT");
            assertThat(body).contains("ICal Test Meeting");
            assertThat(body).contains("END:VEVENT");
            assertThat(body).contains("END:VCALENDAR");
        }
    }

    @Test
    void icalFeedWithInvalidTokenRedirectsToLogin() throws Exception {
        HttpURLConnection conn = openNoRedirect(baseUrl() + "/ical/invalid-token-xyz.ics");
        int status = conn.getResponseCode();
        assertThat(status).isIn(302, 404);
        if (status == 302) {
            assertThat(conn.getHeaderField("Location")).contains("/login");
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Rewrites a URL that may contain a hardcoded base-url port to use the
     * actual random test server port instead.
     * e.g. "http://localhost:8080/ical/token.ics" → "http://localhost:57076/ical/token.ics"
     */
    private String toTestUrl(String url) {
        return url.replaceFirst("http://localhost:\\d+", baseUrl());
    }

    private HttpURLConnection openNoRedirect(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.connect();
        return conn;
    }
}