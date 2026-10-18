package com.example.walletbot.platform.web;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
public class FaviconController {

    private static final byte[] MINIMAL_SVG = (
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 32 32\"><rect width=\"32\" height=\"32\" rx=\"6\""
                    + " fill=\"#0A0A0B\"/></svg>")
            .getBytes(StandardCharsets.UTF_8);

    @GetMapping("/favicon.ico")
    public ResponseEntity<Resource> faviconGet() {
        Resource body = resolveBody();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .body(body);
    }

    @RequestMapping(value = "/favicon.ico", method = RequestMethod.HEAD)
    public ResponseEntity<Void> faviconHead() {
        Resource body = resolveBody();
        long len = contentLengthOf(body);
        var b = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.parseMediaType("image/svg+xml"));
        if (len >= 0) {
            b = b.contentLength(len);
        }
        return b.build();
    }

    private static Resource resolveBody() {
        var fromClasspath = new ClassPathResource("static/icon.svg");
        if (fromClasspath.exists()) {
            return fromClasspath;
        }
        return new ByteArrayResource(MINIMAL_SVG);
    }

    private static long contentLengthOf(Resource r) {
        try {
            return r.contentLength();
        } catch (java.io.IOException e) {
            return MINIMAL_SVG.length;
        }
    }
}