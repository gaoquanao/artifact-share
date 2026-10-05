package com.example.artifactshare.util;

import com.example.artifactshare.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadPathValidatorTest {

    @Test
    void stripsLeadingSlashes() {
        assertEquals("assets/app.js", UploadPathValidator.normalize("//assets/app.js"));
    }

    @Test
    void normalizesDotSegments() {
        assertEquals("assets/app.js", UploadPathValidator.normalize("assets/./x/../app.js"));
    }

    @Test
    void acceptsPlainRelativePath() {
        assertEquals("index.html", UploadPathValidator.normalize("index.html"));
        assertEquals("a/b/c.png", UploadPathValidator.normalize("a\\b\\c.png"));
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(ApiException.class, () -> UploadPathValidator.normalize("../../etc/passwd"));
    }

    @Test
    void rejectsHiddenTraversalAfterStrip() {
        assertThrows(ApiException.class, () -> UploadPathValidator.normalize("/../etc/passwd"));
    }

    @Test
    void rejectsBlankPath() {
        assertThrows(ApiException.class, () -> UploadPathValidator.normalize("   "));
    }

    @Test
    void rejectsRootPath() {
        assertThrows(ApiException.class, () -> UploadPathValidator.normalize("/"));
    }
}
