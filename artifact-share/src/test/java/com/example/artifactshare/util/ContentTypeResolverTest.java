package com.example.artifactshare.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentTypeResolverTest {

    private final ContentTypeResolver resolver = new ContentTypeResolver();

    @Test
    void htmlRendersInBrowser() {
        assertTrue(resolver.resolve("index.html").startsWith("text/html"));
    }

    @Test
    void caseInsensitiveExtension() {
        assertEquals("image/png", resolver.resolve("assets/logo.PNG"));
    }

    @Test
    void unknownFallsBackToOctetStream() {
        assertEquals("application/octet-stream", resolver.resolve("data.bin"));
    }

    @Test
    void noExtensionFallsBackToOctetStream() {
        assertEquals("application/octet-stream", resolver.resolve("LICENSE"));
    }

    @Test
    void dotInDirectoryIsNotExtension() {
        assertEquals("application/octet-stream", resolver.resolve("v1.2/readme"));
    }
}
