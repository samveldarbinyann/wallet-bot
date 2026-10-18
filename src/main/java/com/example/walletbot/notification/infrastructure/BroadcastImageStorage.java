package com.example.walletbot.notification.infrastructure;

import com.example.walletbot.shared.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
public class BroadcastImageStorage {

    private static final String URL_PREFIX = "/api/files/broadcasts/";
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final Path imageDir;

    public BroadcastImageStorage(@Value("${app.broadcast.image-dir:./uploads/broadcasts}") String dir) {
        this.imageDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    private static boolean magicMatches(byte[] b, String contentType) {
        if (b.length < 12) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/gif" -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case "image/webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(imageDir);
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("error.broadcast_image_empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("error.broadcast_image_too_large");
        }
        String contentType = file.getContentType();
        String ext = EXTENSIONS.get(contentType);
        if (ext == null) {
            throw new BadRequestException("error.broadcast_unsupported_image_type");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("error.broadcast_image_read_failed");
        }
        if (!magicMatches(bytes, contentType)) {
            throw new BadRequestException("error.broadcast_image_type_mismatch", contentType);
        }
        String filename = UUID.randomUUID() + ext;
        try {
            Files.write(imageDir.resolve(filename), bytes);
        } catch (IOException e) {
            log.error("Failed to store broadcast image: {}", e.getMessage());
            throw new BadRequestException("error.broadcast_image_store_failed");
        }
        return URL_PREFIX + filename;
    }

    public byte[] load(String imageUrl) {
        String filename = filenameOf(imageUrl);
        if (filename == null) return null;
        Path target = imageDir.resolve(filename).normalize();
        if (!target.startsWith(imageDir) || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.warn("Failed to read broadcast image {}: {}", filename, e.getMessage());
            return null;
        }
    }

    public String filenameOf(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(URL_PREFIX)) return null;
        String filename = imageUrl.substring(URL_PREFIX.length());
        if (!filename.matches("[A-Za-z0-9-]+\\.[A-Za-z0-9]+")) return null;
        return filename;
    }

    public String resourceLocation() {
        return "file:" + imageDir + "/";
    }

    public void deleteOne(String imageUrl) {
        String filename = filenameOf(imageUrl);
        if (filename == null) return;
        Path target = imageDir.resolve(filename).normalize();
        if (!target.startsWith(imageDir)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete broadcast image {}: {}", filename, e.getMessage());
        }
    }

    public int deleteUnreferenced(Set<String> referencedUrls, Duration minAge) {
        Instant cutoff = Instant.now().minus(minAge);
        int deleted = 0;
        try (Stream<Path> files = Files.list(imageDir)) {
            for (Path p : files.toList()) {
                String url = URL_PREFIX + p.getFileName();
                if (referencedUrls.contains(url)) {
                    continue;
                }
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff) && Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete orphan image {}: {}", p, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list broadcast image directory: {}", e.getMessage());
        }
        return deleted;
    }
}
