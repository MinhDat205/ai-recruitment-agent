package com.recruitment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// app.storage.local-path co the la duong dan tuong doi trong application.yml (vd "./uploads") -
// resolve ve tuyet doi ngay o day de moi noi dung (store/delete/static resource mapping) deu
// dung chung mot gia tri, khong phu thuoc working directory luc chay.
@Service
public class LocalStorageService implements StorageService {

    private static final String PUBLIC_PREFIX = "/uploads/";

    private final Path basePath;

    public LocalStorageService(@Value("${app.storage.local-path}") String localPath) {
        this.basePath = Path.of(localPath).toAbsolutePath().normalize();
    }

    public Path getBasePath() {
        return basePath;
    }

    @Override
    public String store(String subdirectory, String filename, InputStream content) {
        try {
            Path dir = basePath.resolve(subdirectory).normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return PUBLIC_PREFIX + subdirectory + "/" + filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Khong the luu file: " + filename, e);
        }
    }

    @Override
    public void delete(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        String relative = publicUrl.substring(PUBLIC_PREFIX.length());
        Path target = basePath.resolve(relative).normalize();
        // Chan path traversal: chi xoa file thuc su nam duoi basePath.
        if (!target.startsWith(basePath)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong the xoa file: " + publicUrl, e);
        }
    }
}
