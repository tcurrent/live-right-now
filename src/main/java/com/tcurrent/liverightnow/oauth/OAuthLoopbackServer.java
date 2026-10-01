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
    private boolean authorizationCodeCallback;
    private boolean implicitTokenCallback;

    @Inject
    public OAuthLoopbackServer(ScheduledExecutorService executorService)
    {
        this.executorService = executorService;
    }

    public synchronized void start(String provider, String state, BiConsumer<String, String> callback) throws IOException
    {
        startInternal(provider, state, callback, false, false);
    }

    public synchronized void startTwitch(String state, BiConsumer<String, String> callback) throws IOException
    {
        startInternal("twitch", state, callback, false, true);
    }

    public synchronized void startKick(String state, BiConsumer<String, String> callback) throws IOException
    {
        startInternal("kick", state, callback, true, false);
    }

    private void startInternal(String provider, String state, BiConsumer<String, String> callback, boolean authorizationCodeCallback, boolean implicitTokenCallback) throws IOException
    {
        stop();

        this.handoffCallback = callback;
        this.expectedProvider = provider;
        this.expectedState = state;
        this.callbackConsumed = false;
        this.authorizationCodeCallback = authorizationCodeCallback;
        this.implicitTokenCallback = implicitTokenCallback;

        server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
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
            authorizationCodeCallback = false;
            implicitTokenCallback = false;
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
            String requestPath = exchange.getRequestURI().getPath();
            boolean implicitTokenPost = implicitTokenCallback
                && "POST".equalsIgnoreCase(exchange.getRequestMethod())
                && (CALLBACK_PATH + "/token").equals(requestPath);

            if (implicitTokenCallback && "GET".equalsIgnoreCase(exchange.getRequestMethod())
                && CALLBACK_PATH.equals(requestPath))
            {
                sendHtml(exchange, implicitCallbackPage(), 200);
                return;
            }

            String requestData = implicitTokenPost
                ? new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                : exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(requestData);

            String provider = params.get("provider");
            String handoffCode = params.get("handoff_code");
            String authorizationCode = params.get("code");
            String accessToken = params.get("access_token");
            String state = params.get("state");
            String oauthError = params.get("error");

            if (implicitTokenPost)
            {
                provider = "twitch";
            }

            String responseHtml;
            int statusCode;
            boolean accepted = false;
            BiConsumer<String, String> callback = null;

            synchronized (OAuthLoopbackServer.this)
            {
                if (provider == null && authorizationCodeCallback)
                {
                    provider = expectedProvider;
                }

                boolean flowValid = expectedState != null && expectedState.equals(state)
                    && expectedProvider != null && expectedProvider.equalsIgnoreCase(provider)
                    && !callbackConsumed
                    && (!implicitTokenPost || "http://localhost:4646".equals(exchange.getRequestHeaders().getFirst("Origin")));

                if (!flowValid)
                {
                    responseHtml = errorPage("This authorization link is invalid or has expired. Please click Connect again in RuneLite.");
                    statusCode = 400;
                }
                else if (implicitTokenPost && oauthError != null && !oauthError.trim().isEmpty())
                {
                    responseHtml = errorPage("Twitch authorization was not completed. Please return to RuneLite and try again.");
                    statusCode = 400;
                    accepted = true;
                    callback = handoffCallback;
                }
                else if (implicitTokenPost && accessToken != null && !accessToken.trim().isEmpty())
                {
                    responseHtml = successPage("Twitch authorization received. You can safely close this window and return to RuneLite.");
                    statusCode = 200;
                    accepted = true;
                    callback = handoffCallback;
                }
                else if (authorizationCodeCallback && authorizationCode != null && !authorizationCode.trim().isEmpty())
                {
                    String platformDisplay = "kick".equalsIgnoreCase(provider) ? "Kick" : "Twitch";
                    responseHtml = successPage(platformDisplay + " authorization received. You can safely close this window and return to RuneLite.");
                    statusCode = 200;
                    accepted = true;
                    callback = handoffCallback;
                }
                else if (!authorizationCodeCallback && handoffCode != null && !handoffCode.trim().isEmpty())
                {
                    String platformDisplay = "kick".equalsIgnoreCase(provider) ? "Kick" : "Twitch";
                    responseHtml = successPage(platformDisplay + " connected successfully! You can safely close this window and return to RuneLite.");
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
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(bytes);
            }

            if (accepted && callback != null)
            {
                String callbackValue = implicitTokenCallback
                    ? (oauthError == null || oauthError.trim().isEmpty() ? accessToken : null)
                    : (authorizationCodeCallback ? authorizationCode : handoffCode);
                callback.accept(provider.toLowerCase(), callbackValue);
            }

            executorService.schedule(() -> stopIfState(state), 2, TimeUnit.SECONDS);
        }
    }

    private void sendHtml(HttpExchange exchange, String html, int statusCode) throws IOException
    {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; connect-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(bytes);
        }
    }

    private String implicitCallbackPage()
    {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Live Right Now - Twitch Authorization</title></head>"
            + "<body><p id='status'>Completing Twitch authorization...</p><script>"
            + "const fragment=new URLSearchParams(window.location.hash.slice(1));const query=new URLSearchParams(window.location.search);"
            + "const token=fragment.get('access_token')||'';const state=fragment.get('state')||query.get('state')||'';"
            + "const error=fragment.get('error')||query.get('error')||'';"
            + "history.replaceState(null,'',window.location.pathname);"
            + "if(!state||(!token&&!error)){document.getElementById('status').textContent='Authorization response was incomplete. Return to RuneLite and try again.';}"
            + "else{fetch('/callback/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
            + "body:new URLSearchParams({state:state,access_token:token,error:error}),cache:'no-store'})"
            + ".then(response=>{if(!response.ok)throw new Error();document.getElementById('status').textContent='Twitch authorization received. You can safely close this window and return to RuneLite.';})"
            + ".catch(()=>{document.getElementById('status').textContent='Authorization could not be completed. Return to RuneLite and try again.';});}"
            + "</script></body></html>";
    }

    private String successPage(String message)
    {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Live Right Now - Connected</title>"
            + "<style>body{font-family:Segoe UI,Helvetica,Arial,sans-serif;background:#121212;color:#eee;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}"
            + ".card{background:#1e1e1e;padding:30px 40px;border-radius:10px;border:1px solid #333;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,0.5);max-width:400px;}"
            + "h2{color:#00B4D8;margin-top:0;}p{color:#aaa;line-height:1.5;}</style></head>"
            + "<body><div class='card'><h2>Live Right Now</h2><p>" + message + "</p></div></body></html>";
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
