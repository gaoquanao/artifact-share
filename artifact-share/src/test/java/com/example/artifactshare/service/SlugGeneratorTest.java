package com.example.artifactshare.service;

import com.example.artifactshare.config.ShareProperties;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.repository.ArtifactRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlugGeneratorTest {

    private static final String HASH_PATTERN = "[23456789abcdefghjkmnpqrstuvwxyz]{8}";

    private final ArtifactRepository repository = mock(ArtifactRepository.class);
    private final SlugGenerator generator = new SlugGenerator(repository, new ShareProperties());

    @Test
    void defaultSlugContainsReadablePrefixAndHash() {
        when(repository.existsBySlug(anyString())).thenReturn(false);
        String slug = generator.generateDefault("weekly-report.html");
        assertTrue(slug.matches("^(?:[a-z0-9]+-)+" + HASH_PATTERN + "$"), slug);
        assertEquals("weekly-report", slug.substring(0, slug.length() - 9));
    }

    @Test
    void defaultSlugUsesParentDirForGenericEntry() {
        when(repository.existsBySlug(anyString())).thenReturn(false);
        assertTrue(generator.generateDefault("dist/weekly-report/index.html").startsWith("weekly-report-"));
        assertTrue(generator.generateDefault("site/readme.md").startsWith("site-"));
    }

    @Test
    void defaultSlugFallsBackToPureHashForChineseName() {
        when(repository.existsBySlug(anyString())).thenReturn(false);
        assertTrue(generator.generateDefault("周报.html").matches("^" + HASH_PATTERN + "$"));
    }

    @Test
    void sameSourceProducesDifferentSlugPerPublish() {
        when(repository.existsBySlug(anyString())).thenReturn(false);
        assertNotEquals(generator.generateDefault("a.html"), generator.generateDefault("a.html"));
    }

    @Test
    void collisionRetriesWithDifferentSlug() {
        when(repository.existsBySlug(anyString())).thenReturn(true, false);
        String slug = generator.generateDefault("weekly-report.html");
        assertTrue(slug.matches("^(?:[a-z0-9]+-)+[a-z0-9]*" + HASH_PATTERN + "$"), slug);
    }

    @Test
    void longFilenamePrefixIsTruncated() {
        when(repository.existsBySlug(anyString())).thenReturn(false);
        String slug = generator.generateDefault(
                "this-is-a-very-long-agent-artifact-filename-with-many-words.html");
        assertTrue(slug.length() <= 29, slug);
        assertTrue(slug.startsWith("this-is-a-very-long-"), slug);
    }

    @Test
    void customSlugIsNormalized() {
        assertEquals("my-report", generator.normalizeCustom("My-Report"));
        assertEquals("abc", generator.normalizeCustom(" abc "));
    }

    @Test
    void customSlugRejectsBadInput() {
        assertThrows(ApiException.class, () -> generator.normalizeCustom(null));
        assertThrows(ApiException.class, () -> generator.normalizeCustom("  "));
        assertThrows(ApiException.class, () -> generator.normalizeCustom("Admin"));
        assertThrows(ApiException.class, () -> generator.normalizeCustom("a"));
        assertThrows(ApiException.class, () -> generator.normalizeCustom("a_b"));
        assertThrows(ApiException.class, () -> generator.normalizeCustom("-abc"));
    }
}
