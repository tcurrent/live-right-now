package com.tcurrent.liverightnow.oauth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@Singleton
public class OAuthLoopbackServer
{
    private static final Logger log = LoggerFactory.getLogger(OAuthLoopbackServer.class);
    public static final int PORT = 4646;
    private static final String CALLBACK_PATH = "/callback";
    private static final long FLOW_TIMEOUT_MINUTES = 5;

    private final ScheduledExecutorService executorService;
    private HttpServer server;
    private BiConsumer<String, String> handoffCallback;
    private String expectedProvider;
    private String expectedState;
        private boolean callbackConsumed;

    @Inject
    public OAuthLoopbackServer(ScheduledExecutorService executorService)
    {
        this.executorService = executorService;
    }

    public synchronized void start(String provider, String state, BiConsumer<String, String> callback) throws IOException
    {
        stop();

        this.handoffCallback = callback;
        this.expectedProvider = provider;
        this.expectedState = state;
            this.callbackConsumed = false;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext(CALLBACK_PATH, new CallbackHandler());
        server.setExecutor(executorService);
        server.start();
        log.debug("OAuth loopback server started on port {}", PORT);
        executorService.schedule(() -> stopIfState(state), FLOW_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    public synchronized void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
            handoffCallback = null;
            expectedProvider = null;
            expectedState = null;
                callbackConsumed = false;
            log.debug("OAuth loopback server stopped");
        }
    }

    private synchronized void stopIfState(String state)
    {
        if (state.equals(expectedState))
        {
            stop();
        }
    }

    private class CallbackHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);

            String provider = params.get("provider");
            String handoffCode = params.get("handoff_code");
            String state = params.get("state");

            String responseHtml;
            int statusCode;
            boolean accepted = false;
            BiConsumer<String, String> callback = null;

            synchronized (OAuthLoopbackServer.this)
            {
                boolean flowValid = expectedState != null && expectedState.equals(state)
                    && expectedProvider != null && expectedProvider.equalsIgnoreCase(provider)
                    && !callbackConsumed;

                if (!flowValid)
                {
                    responseHtml = errorPage("This authorization link is invalid or has expired. Please click Connect again in RuneLite.");
                    statusCode = 400;
                }
                else if (handoffCode != null && !handoffCode.trim().isEmpty())
                {
                    String platformDisplay = "kick".equalsIgnoreCase(provider) ? "Kick" : "Twitch";
                    responseHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Live Right Now - Connected</title>"
                        + "<style>body{font-family:Segoe UI,Helvetica,Arial,sans-serif;background:#121212;color:#eee;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}"
                        + ".card{background:#1e1e1e;padding:30px 40px;border-radius:10px;border:1px solid #333;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,0.5);max-width:400px;}"
                        + "h2{color:#00B4D8;margin-top:0;}p{color:#aaa;line-height:1.5;}</style></head>"
                        + "<body><div class='card'><h2>Live Right Now</h2>"
                        + "<p><strong>" + platformDisplay + "</strong> connected successfully!<br>You can safely close this window and return to RuneScape.</p></div></body></html>";
                    statusCode = 200;
                    accepted = true;
                    callback = handoffCallback;
                }
                else
                {
                    responseHtml = errorPage("Authorization failed or the secure handoff code is missing.");
                    statusCode = 400;
                }

                if (accepted)
                {
                    callbackConsumed = true;
                }
            }

            byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(bytes);
            }

            if (accepted && callback != null)
            {
                callback.accept(provider.toLowerCase(), handoffCode);
            }

            executorService.schedule(() -> stopIfState(state), 2, TimeUnit.SECONDS);
        }
    }

    private String errorPage(String message)
    {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Live Right Now - Error</title>"
            + "<style>body{font-family:Segoe UI,Helvetica,Arial,sans-serif;background:#121212;color:#eee;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}"
            + ".card{background:#1e1e1e;padding:30px 40px;border-radius:10px;border:1px solid #ff4444;text-align:center;max-width:400px;}"
            + "h2{color:#ff4444;margin-top:0;}p{color:#aaa;}</style></head>"
            + "<body><div class='card'><h2>Authentication Error</h2>"
            + "<p>" + message + "</p></div></body></html>";
    }

    private Map<String, String> parseQuery(String query)
    {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty())
        {
            return map;
        }

        for (String param : query.split("&"))
        {
            String[] pair = param.split("=", 2);
            if (pair.length == 2)
            {
                try
                {
                    String key = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                    String value = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                    map.put(key, value);
                }
                catch (java.io.UnsupportedEncodingException | IllegalArgumentException ignored)
                {
                }
            }
        }
        return map;
    }
}
