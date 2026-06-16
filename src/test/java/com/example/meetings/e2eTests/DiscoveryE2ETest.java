package com.example.meetings.e2eTests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Flow 5: Discovery page.
 *
 * Tests cover navigation to the discover page, its basic structure, and the
 * copy-to-calendar flow (which bypasses the external API entirely via a
 * JS-injected form, making it independent of whether provider keys are set).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:disc_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"}
)
class DiscoveryE2ETest extends BaseE2ETest {

    private String user;
    private String password;

    @BeforeEach
    void setUp() {
        user     = uniqueUser("disc");
        password = "discpass";
        register(user, user + "@example.com", password);
        login(user, password);
    }

    @Test
    void discoverPageIsReachableFromNavbar() {
        navigateTo("/calendar");
        driver.findElement(By.cssSelector("nav a[href='/discover']")).click();
        wait.until(ExpectedConditions.urlContains("/discover"));
        assertThat(driver.getTitle()).contains("Discover");
    }

    @Test
    void discoverPageRendersProviderStatus() {
        navigateTo("/discover");
        // The page always lists providers and their status (configured/unconfigured).
        // We only assert the word appears, not which state, since that depends on the API keys set in the environment.
        assertThat(pageContains("configured")).isTrue();
    }

    @Test
    void discoverPageRendersSearchForm() {
        navigateTo("/discover");
        assertThat(driver.findElement(By.id("q")).isDisplayed()).isTrue();
    }

    @Test
    void copyDiscoveredEventAppearsOnCalendar() {
        navigateTo("/discover");

        String script = """
                var form = document.createElement('form');
                form.method = 'post';
                form.action = '/discover/copy';
                var fields = {
                    source:      'TestProvider',
                    externalId:  'ext-001',
                    title:       'Discovered Jazz Night',
                    description: 'Great jazz event',
                    start:       '2030-11-10T20:00:00Z',
                    end:         '2030-11-10T23:00:00Z',
                    url:         'https://example.com/event/1',
                    venue:       'Blue Note Club'
                };
                Object.entries(fields).forEach(([k, v]) => {
                    var inp = document.createElement('input');
                    inp.type = 'hidden'; inp.name = k; inp.value = v;
                    form.appendChild(inp);
                });
                var csrf = document.querySelector('input[name="_csrf"]');
                if (csrf) {
                    var c = document.createElement('input');
                    c.type = 'hidden'; c.name = '_csrf'; c.value = csrf.value;
                    form.appendChild(c);
                }
                document.body.appendChild(form);
                form.submit();
                """;

        ((JavascriptExecutor) driver).executeScript(script);

        wait.until(ExpectedConditions.urlContains("/calendar"));

        assertThat(pageContains("Discovered Jazz Night")).isTrue();
        // The user is the sole participant and auto-accepts → immediately confirmed
        assertThat(pageContains("confirmed")).isTrue();
    }
}