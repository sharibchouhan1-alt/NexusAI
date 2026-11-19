package com.nexusai.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local-filesystem storage. Layout: {@code <root>/<namespace>/<random-uuid>}.
 *
 * <p>Path safety: namespaces and keys must match a strict UUID-based pattern, and every
 * resolved path is checked to stay inside the root. Files are written to a temporary file
 * and atomically moved into place, so a partially written upload is never visible.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern NAMESPACE = Pattern.compile(UUID_REGEX);
    private static final Pattern KEY = Pattern.compile(UUID_REGEX + "/" + UUID_REGEX);
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final Path root;

    public LocalFileStorage(@Value("${nexusai.storage.local.root}") Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            Files.createDirectories(this.root);
            restrictToOwner(this.root, true); // only tighten a directory we created
        }
        if (!Files.isWritable(this.root)) {
            throw new IllegalStateException("Storage root is not writable: " + this.root);
        }
        log.info("Local file storage at {}", this.root);
    }

    @Override
    public StoredFile store(String namespace, InputStream content) throws IOException {
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid storage namespace");
        }
        String key = namespace + "/" + UUID.randomUUID();
        Path target = resolve(key);
        Path directory = target.getParent();
        Files.createDirectories(directory);
        restrictToOwner(directory, true);

        Path temp = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            MessageDigest sha256 = sha256();
            long size;
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), sha256)) {
                size = content.transferTo(out);
            }
            restrictToOwner(temp, false);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(key, size, HexFormat.of().formatHex(sha256.digest()));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root");
        }
        return path;
    }

    private static void restrictToOwner(Path path, boolean directory) throws IOException {
        if (POSIX) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
