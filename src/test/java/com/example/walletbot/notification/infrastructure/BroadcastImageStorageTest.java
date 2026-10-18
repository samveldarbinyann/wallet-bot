package com.example.walletbot.notification.infrastructure;

import com.example.walletbot.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BroadcastImageStorageTest {

    private static final byte[] PNG = pngBytes();

    @TempDir
    Path tempDir;
    private BroadcastImageStorage storage;

    private static byte[] pngBytes() {
        byte[] b = new byte[32];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, b, 0, signature.length);
        return b;
    }

    @BeforeEach
    void setUp() throws IOException {
        storage = new BroadcastImageStorage(tempDir.toString());
        storage.init();
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    @Test
    void save_validPng_storesAndReturnsUrl() {
        String url = storage.save(png("pic.png"));
        assertThat(url).matches("/api/files/broadcasts/[a-f0-9-]+\\.png");
    }

    @Test
    void save_rejectsUnsupportedContentType() {
        var file = new MockMultipartFile("file", "x.txt", "text/plain", new byte[32]);
        assertThatThrownBy(() -> storage.save(file)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void save_rejectsContentMismatchingMagicBytes() {
        var file = new MockMultipartFile("file", "fake.png", "image/png", new byte[32]);
        assertThatThrownBy(() -> storage.save(file)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void save_rejectsOversizeFile() {
        var file = new MockMultipartFile("file", "big.png", "image/png", new byte[6 * 1024 * 1024]);
        assertThatThrownBy(() -> storage.save(file)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void save_rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> storage.save(file)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_roundTripsStoredBytes() {
        String url = storage.save(png("pic.png"));
        assertThat(storage.load(url)).isEqualTo(PNG);
    }

    @Test
    void load_missingFile_returnsNull() {
        assertThat(storage.load("/api/files/broadcasts/00000000-0000-0000-0000-000000000000.png"))
                .isNull();
    }

    @Test
    void filenameOf_rejectsTraversalAndGarbage() {
        assertThat(storage.filenameOf("/api/files/broadcasts/../../etc/passwd")).isNull();
        assertThat(storage.filenameOf("/api/files/broadcasts/..")).isNull();
        assertThat(storage.filenameOf("/elsewhere/x.png")).isNull();
        assertThat(storage.filenameOf(null)).isNull();
    }

    @Test
    void deleteUnreferenced_removesAgedOrphansKeepsReferencedAndRecent() throws IOException {
        String referenced = storage.save(png("a.png"));
        String agedOrphan = storage.save(png("b.png"));
        String recentOrphan = storage.save(png("c.png"));

        Path agedPath = tempDir.resolve(agedOrphan.substring("/api/files/broadcasts/".length()));
        Files.setLastModifiedTime(agedPath, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        int removed = storage.deleteUnreferenced(Set.of(referenced), Duration.ofHours(1));

        assertThat(removed).isEqualTo(1);
        assertThat(storage.load(referenced)).isNotNull();
        assertThat(storage.load(recentOrphan)).isNotNull();
        assertThat(storage.load(agedOrphan)).isNull();
    }
}
