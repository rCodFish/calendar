package com.example.meetings.e2eTests;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

/**
 * Base class for all E2E Selenium tests.
 *
 * Each concrete subclass MUST declare its own @TestPropertySource that sets
 * spring.datasource.url to a unique in-memory H2 database name, isolating
 * each test class from data created by other classes.
 *
 * Session management strategy:
 * - A fresh headless Chrome (non-incognito) is created per test method.
 * - Before login, the browser first GETs /logout to invalidate any
 *   server-side session that might be lingering, then proceeds to /login.
 *   This avoids the CSRF / session-fixation redirect-to-login?logout issue
 *   that occurs when a session cookie from register() or a prior test is
 *   partially reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    static void setupDriver() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage"
        );
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected void navigateTo(String path) {
        driver.get(baseUrl() + path);
    }

    // -------------------------------------------------------------------------
    // Auth helpers
    // -------------------------------------------------------------------------

    /**
     * Registers a new user and waits for the /login redirect.
     * Registration does not create a server session, the user must call
     * login() separately.
     */
    protected void register(String username, String email, String password) {
        navigateTo("/register");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    /**
     * Logs in and waits for the /calendar redirect.
     *
     * Performs a GET /logout first to clear any server-side session that may
     * have been created by a preceding register() call or left over from the
     * previous test in the same class. Spring Security processes a GET to
     * /logout as a no-op if there is no session, or returns a CSRF-protected
     * logout form, in either case, navigating away immediately is safe.
     * We then navigate to /login to get a clean page with a fresh CSRF token
     * and new session ID before submitting credentials.
     */
    protected void login(String username, String password) {
        // some stuff to avoid the csrf complaints
        navigateTo("/login");
        driver.manage().deleteAllCookies();
        navigateTo("/login");

        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
    }

    protected void logout() {
        driver.findElement(By.cssSelector("form[action*='/logout']"))
                .findElement(By.cssSelector("button[type='submit']"))
                .click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    protected static String uniqueUser(String base) {
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected boolean pageContains(String text) {
        return driver.getPageSource().contains(text);
    }

    protected WebElement waitForElement(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Sets a datetime-local input via JavaScript.
     */
    protected void setDateTimeLocal(String fieldId, String isoValue) {
        ((JavascriptExecutor) driver).executeScript("""
                var el = document.getElementById(arguments[0]);
                el.value = arguments[1];
                el.dispatchEvent(new Event('input',  {bubbles:true}));
                el.dispatchEvent(new Event('change', {bubbles:true}));
                """, fieldId, isoValue);
    }
}