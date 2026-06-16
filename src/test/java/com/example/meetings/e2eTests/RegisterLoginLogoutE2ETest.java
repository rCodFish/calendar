package com.example.meetings.e2eTests;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Flow 1: Register → Login → Logout
 *
 * Uses its own isolated H2 database (rll_db) so data from other test classes
 * cannot bleed in or cause username conflicts.
 */
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:rll_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class RegisterLoginLogoutE2ETest extends BaseE2ETest {

    @Test
    void registerThenLoginThenLogout() {
        String username = uniqueUser("rll");
        String password = "secret123";

        // ---- Register ----
        navigateTo("/register");
        assertThat(driver.getTitle()).contains("Register");

        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(username + "@example.com");
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        waitForElement(By.cssSelector(".success"));
        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(pageContains("Account created")).isTrue();

        // ---- Login ----
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        waitForElement(By.cssSelector("nav"));
        assertThat(driver.getCurrentUrl()).contains("/calendar");
        assertThat(pageContains(username)).isTrue();

        // ---- Logout ----
        logout();

        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(pageContains("signed out")).isTrue();
    }

    @Test
    void registerWithDuplicateUsernameShowsError() {
        String username = uniqueUser("dup");
        String password = "pass";

        register(username, username + "@example.com", password);

        navigateTo("/register");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys("other_" + username + "@example.com");
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        waitForElement(By.cssSelector(".error"));
        assertThat(driver.getCurrentUrl()).contains("/register");
    }

    @Test
    void loginWithWrongPasswordShowsError() {
        String username = uniqueUser("auth");
        register(username, username + "@example.com", "correctpass");

        navigateTo("/login");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys("wrongpass");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        waitForElement(By.cssSelector(".error"));
        assertThat(driver.getCurrentUrl()).contains("error");
        assertThat(pageContains("Invalid username or password")).isTrue();
    }
}