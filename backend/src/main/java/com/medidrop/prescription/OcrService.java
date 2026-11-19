package com.medidrop.prescription;

import java.util.Optional;

/**
 * OCR assistance only. Output is surfaced to a pharmacist for manual verification and is
 * never used to authorise dispensing. Enable by providing an implementation and setting
 * medidrop.ocr.provider.
 */
public interface OcrService {

    Optional<OcrResult> extract(byte[] content, String contentType);

    String provider();

    record OcrResult(String text, double confidence) {
    }
}
