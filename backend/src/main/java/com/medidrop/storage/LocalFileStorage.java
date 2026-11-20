package com.medidrop.storage;

import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "medidrop.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(MedidropProperties properties) {
        this.root = Path.of(properties.getStorage().getLocalPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory " + root, e);
        }
    }

    @Override
    public StoredFile store(InputStream input, String originalName, String contentType, String folder) {
        String safeFolder = folder.replaceAll("[^a-zA-Z0-9_-]", "");
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
            if (!extension.matches("\\.[a-z0-9]{1,5}")) extension = "";
        }
        String key = safeFolder + "/" + UUID.randomUUID() + extension;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("Invalid storage path");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(key, Files.size(target), contentType, originalName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist file", e);
        }
    }

    @Override
    public Resource load(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw ApiException.notFound("File not found");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        Path target = root.resolve(key).normalize();
        if (target.startsWith(root)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete file", e);
            }
        }
    }
}
