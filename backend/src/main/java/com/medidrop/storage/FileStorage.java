package com.medidrop.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Private object storage. Implementations must never expose predictable public URLs;
 * retrieval always flows through an authorised backend endpoint.
 */
public interface FileStorage {

    StoredFile store(InputStream input, String originalName, String contentType, String folder);

    Resource load(String key);

    void delete(String key);

    record StoredFile(String key, long size, String contentType, String originalName) {
    }
}
