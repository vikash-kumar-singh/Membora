package com.membora.platform.media.dto;

/** One-time, short-lived parameters safe to return to an authenticated Android client. */
public record ImageKitAuthResponse(
    String token,
    long expire,
    String signature,
    String publicKey,
    String urlEndpoint,
    String folder
) {}
