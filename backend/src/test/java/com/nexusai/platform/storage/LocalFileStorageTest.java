package com.nexusai.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusai.platform.storage.FileStorage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void storesReadsAndDeletes() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        String namespace = UUID.randomUUID().toString();
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = storage.store(namespace, new ByteArrayInputStream(content));

        assertThat(stored.key()).startsWith(namespace + "/");
        assertThat(stored.sizeBytes()).isEqualTo(11);
        assertThat(stored.sha256()).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        assertThat(storage.exists(stored.key())).isTrue();
        try (InputStream in = storage.open(stored.key())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
        Path file = root.resolve(stored.key());
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
        // No temporary files left behind.
        try (var files = Files.list(file.getParent())) {
            assertThat(files).hasSize(1);
        }

        storage.delete(stored.key());
        assertThat(storage.exists(stored.key())).isFalse();
        storage.delete(stored.key()); // idempotent
    }

    @Test
    void createsMissingRootOwnerOnly() throws Exception {
        Path nested = root.resolve("a/b/storage");
        new LocalFileStorage(nested);
        assertThat(Files.isDirectory(nested)).isTrue();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(nested))).isEqualTo("rwx------");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc", "..", "", "not-a-uuid", "00000000-0000-0000-0000-000000000000/../x"})
    void rejectsUnsafeNamespaces(String namespace) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        assertThatThrownBy(() -> storage.store(namespace, new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "/etc/passwd",
            "00000000-0000-0000-0000-000000000000/../../../etc/passwd",
            "00000000-0000-0000-0000-000000000000",
    })
    void rejectsUnsafeKeys(String key) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
    }
}
