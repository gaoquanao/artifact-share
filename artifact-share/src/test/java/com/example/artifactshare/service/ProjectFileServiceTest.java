package com.example.artifactshare.service;

import com.example.artifactshare.config.AgentProperties;
import com.example.artifactshare.exception.ApiException;
import com.example.artifactshare.web.dto.FileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFileServiceTest {

    @TempDir
    Path tempDir;

    private ProjectFileService service() {
        AgentProperties props = new AgentProperties();
        props.getWorkspace().setRoot(tempDir.toString());
        return new ProjectFileService(props);
    }

    @Test
    void writeAndReadRoundtrip() {
        ProjectFileService service = service();
        service.writeFile("proj123", "src/app.js", "console.log('hi');");
        assertEquals("console.log('hi');", service.readFile("proj123", "src/app.js"));
    }

    @Test
    void listsFilesSortedWithSize() {
        ProjectFileService service = service();
        service.writeFile("proj123", "b.txt", "bb");
        service.writeFile("proj123", "a/nested/c.txt", "c");
        List<FileEntry> entries = service.listFileEntries("proj123");
        assertEquals(2, entries.size());
        assertEquals("a/nested/c.txt", entries.get(0).path());
        assertEquals(2, entries.get(1).size());
    }

    @Test
    void rejectsTraversalPath() {
        ProjectFileService service = service();
        service.writeFile("proj123", "ok.txt", "x");
        assertThrows(ApiException.class, () -> service.writeFile("proj123", "../escape.txt", "evil"));
        assertThrows(ApiException.class, () -> service.readFile("proj123", "../../etc/passwd"));
    }

    @Test
    void readFromRootConfinedToRoot() {
        ProjectFileService service = service();
        service.writeFile("proj123", "index.html", "<p>hi</p>");
        Path root = service.workspace("proj123");
        assertEquals("<p>hi</p>", new String(service.readFromRoot(root, "index.html")));
        assertThrows(ApiException.class, () -> service.readFromRoot(root, "../outside.txt"));
    }

    @Test
    void snapshotContainsFileBodies() {
        ProjectFileService service = service();
        service.writeFile("proj123", "index.html", "<html></html>");
        assertTrue(service.renderSnapshot("proj123").contains("[file] index.html"));
        assertTrue(service.renderSnapshot("proj123").contains("<html></html>"));
    }
}
