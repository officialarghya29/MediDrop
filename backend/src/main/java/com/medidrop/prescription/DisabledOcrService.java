package com.medidrop.prescription;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "medidrop.ocr.provider", havingValue = "none", matchIfMissing = true)
public class DisabledOcrService implements OcrService {

    @Override
    public Optional<OcrResult> extract(byte[] content, String contentType) {
        return Optional.empty();
    }

    @Override
    public String provider() {
        return "none";
    }
}
