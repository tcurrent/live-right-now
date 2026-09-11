package com.tcurrent.liverightnow.oauth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

public class OAuthLoopbackServer
{
    private static final Logger log = LoggerFactory.getLogger(OAuthLoopbackServer.class);
    private static final int TIMEOUT_SECONDS = 120;

    private HttpServer server;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public CompletableFuture<Map<String, String>> startServer(Consumer<Integer> onPortBound)
    {
        CompletableFuture<Map<String, String>> callbackFuture = new CompletableFuture<>();

        try
        {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();

            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryParams(query);

                if (params.containsKey("code") || params.containsKey("access_token") || params.containsKey("error"))
                {
                    byte[] responseBytes = getSuccessHtml().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody())
                    {
                        os.write(responseBytes);
                    }
                    callbackFuture.complete(params);
                    stop();
                }
                else
                {
                    // Handle potential fragment / hash redirect
                    byte[] scriptBytes = getFragmentCaptureHtml().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, scriptBytes.length);
                    try (OutputStream os = exchange.getResponseBody())
                    {
                        os.write(scriptBytes);
                    }
                }
            });

            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();

            if (onPortBound != null)
            {
                onPortBound.accept(port);
            }

            executor.schedule(() -> {
                if (!callbackFuture.isDone())
                {
                    callbackFuture.cancel(true);
                    stop();
                }
            }, TIMEOUT_SECONDS, TimeUnit.SECONDS);

        }
        catch (IOException e)
        {
            log.error("Failed to start OAuth loopback server", e);
            callbackFuture.completeExceptionally(e);
        }

        return callbackFuture;
    }

    public synchronized void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
        }
        executor.shutdownNow();
    }

    private Map<String, String> parseQueryParams(String query)
    {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.trim().isEmpty())
        {
            return result;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs)
        {
            int idx = pair.indexOf("=");
            if (idx > 0)
            {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                result.put(key, value);
            }
        }
        return result;
    }

    private String getSuccessHtml()
    {
        return "<!DOCTYPE html>"
            + "<html>"
            + "<head><meta charset='utf-8'><title>Live Right Now - Connected</title>"
            + "<style>"
            + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #121212; color: #f0f0f0; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }"
            + ".card { background: #1e1e1e; border: 1px solid #333; border-radius: 12px; padding: 32px 48px; text-align: center; box-shadow: 0 4px 20px rgba(0,0,0,0.5); max-width: 400px; }"
            + "h1 { color: #00b4d8; margin-bottom: 12px; font-size: 24px; }"
            + "p { color: #aaa; font-size: 14px; line-height: 1.5; }"
            + ".badge { display: inline-block; background: #2e7d32; color: #fff; padding: 6px 16px; border-radius: 20px; font-weight: bold; margin-bottom: 16px; font-size: 13px; }"
            + "</style></head>"
            + "<body>"
            + "<div class='card'>"
            + "<div class='badge'>Connected</div>"
            + "<h1>Authorization Complete</h1>"
            + "<p>Your account has been linked to <strong>Live Right Now</strong> in RuneLite.<br>You can safely close this window.</p>"
            + "</div>"
            + "</body></html>";
    }

    private String getFragmentCaptureHtml()
    {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Authenticating...</title></head>"
            + "<body style='background:#121212;color:#eee;text-align:center;padding:50px;font-family:sans-serif;'>"
            + "Connecting to Live Right Now..."
            + "<script>"
            + "if (window.location.hash) {"
            + "  var query = window.location.hash.substring(1);"
            + "  window.location.href = '/callback?' + query;"
            + "}"
            + "</script>"
            + "</body></html>";
    }
}
