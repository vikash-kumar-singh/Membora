package com.membora.platform.media.controller;

import com.membora.platform.media.FirebaseIdentityVerifier;
import com.membora.platform.media.dto.ImageKitAuthResponse;
import com.membora.platform.media.dto.ImageKitSignedUrlResponse;
import com.membora.platform.media.service.ImageKitService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
public class ImageKitController {
    private final FirebaseIdentityVerifier identities;
    private final ImageKitService imageKit;

    public ImageKitController(FirebaseIdentityVerifier identities, ImageKitService imageKit) {
        this.identities = identities;
        this.imageKit = imageKit;
    }

    @GetMapping("/imagekit-auth")
    ResponseEntity<ImageKitAuthResponse> authentication(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken
    ) {
        var response = imageKit.createUploadAuthentication(identities.requireUser(firebaseIdToken));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/signed-url")
    ResponseEntity<ImageKitSignedUrlResponse> signedUrl(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken,
        @RequestParam("url") String url
    ) {
        var response = imageKit.createSignedUrl(identities.requireUser(firebaseIdToken), url);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
