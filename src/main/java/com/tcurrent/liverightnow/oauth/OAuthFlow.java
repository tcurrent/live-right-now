package com.tcurrent.liverightnow.oauth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

final class OAuthFlow
{
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String state;
    private final String handoffSecret;
    private final String handoffProof;
    private final String codeVerifier;
    private final String codeChallenge;

    private OAuthFlow(String state, String handoffSecret, String handoffProof, String codeVerifier, String codeChallenge)
    {
        this.state = state;
        this.handoffSecret = handoffSecret;
        this.handoffProof = handoffProof;
        this.codeVerifier = codeVerifier;
        this.codeChallenge = codeChallenge;
    }

    static OAuthFlow create()
    {
        String state = randomValue();
        String handoffSecret = randomValue();
        String codeVerifier = randomValue();
        return new OAuthFlow(
            state,
            handoffSecret,
            sha256Base64Url(handoffSecret),
            codeVerifier,
            sha256Base64Url(codeVerifier)
        );
    }

    String getState()
    {
        return state;
    }

    String getHandoffSecret()
    {
        return handoffSecret;
    }

    String getHandoffProof()
    {
        return handoffProof;
    }

    String getCodeVerifier()
    {
        return codeVerifier;
    }

    String getCodeChallenge()
    {
        return codeChallenge;
    }

    private static String randomValue()
    {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64Url(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}