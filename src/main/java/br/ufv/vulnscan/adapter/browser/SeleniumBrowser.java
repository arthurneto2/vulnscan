package br.ufv.vulnscan.adapter.browser;

import br.ufv.vulnscan.domain.module.RenderedPage;
import br.ufv.vulnscan.domain.module.XssBrowser;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de navegador: implementa a interface {@link XssBrowser} dos módulos de XSS com
 * Selenium e Chrome headless. Um driver novo por {@link #open} (isolado; fechado no fim).
 * Do mapa de headers só {@code Cookie} é aplicado, como cookies na origem da URL (WebDriver
 * não envia header arbitrário). Espera a página carregar e mais {@value #SETTLE_MILLIS} ms
 * para SPAs, aceitando e anotando os diálogos ({@code alert/confirm/prompt}) que aparecerem.
 * Falha do WebDriver propaga. Binário e driver: os padrões do Selenium (Chrome no PATH;
 * Selenium Manager resolve o driver).
 */
public final class SeleniumBrowser implements XssBrowser {

    private static final long SETTLE_MILLIS = 1500;
    private static final long POLL_MILLIS = 100;

    @Override
    public RenderedPage open(URI url, Map<String, String> headers) {
        long start = System.nanoTime();
        WebDriver driver = new ChromeDriver(options());
        try {
            List<String> dialogs = new ArrayList<>();
            String cookie = cookieHeader(headers);
            if (cookie != null) {
                driver.get(url.resolve("/").toString());
                addCookies(driver, cookie);
            }
            try {
                driver.get(url.toString());
            } catch (UnhandledAlertException e) {
                // o diálogo segue aberto; é lido no assentamento
            }
            settle(driver, dialogs);
            String html = String.valueOf(((JavascriptExecutor) driver)
                    .executeScript("return document.documentElement.outerHTML"));
            return new RenderedPage(html, dialogs, Duration.ofNanos(System.nanoTime() - start).toMillis());
        } finally {
            driver.quit();
        }
    }

    private static ChromeOptions options() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        return options;
    }

    /** Espera {@value #SETTLE_MILLIS} ms aceitando e anotando os diálogos, na ordem. */
    private static void settle(WebDriver driver, List<String> dialogs) {
        long deadline = System.nanoTime() + Duration.ofMillis(SETTLE_MILLIS).toNanos();
        while (true) {
            try {
                Alert alert = driver.switchTo().alert();
                dialogs.add(alert.getText());
                alert.accept();
                continue;
            } catch (NoAlertPresentException e) {
                // nenhum diálogo agora
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
            sleep();
        }
    }

    private static String cookieHeader(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase("Cookie")) {
                return header.getValue();
            }
        }
        return null;
    }

    /** {@code "a=1; b=2"} → um cookie por par, na origem já aberta. */
    private static void addCookies(WebDriver driver, String header) {
        for (String pair : header.split(";")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                driver.manage().addCookie(new Cookie(pair.substring(0, equals).strip(),
                        pair.substring(equals + 1).strip()));
            }
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido esperando a página", e);
        }
    }
}
