package com.medidrop.common;

import com.medidrop.common.exception.ApiException;

/** Content sniffing shared by every user-uploaded image/PDF path. */
public final class FileValidation {

    private FileValidation() {
    }

    public static void assertMagicBytes(byte[] content, String contentType) {
        boolean valid;
        if ("application/pdf".equals(contentType)) {
            valid = content.length > 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                    && content[3] == 'F';
        } else if ("image/jpeg".equals(contentType)) {
            valid = content.length > 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                    && (content[2] & 0xFF) == 0xFF;
        } else {
            valid = content.length > 8 && (content[0] & 0xFF) == 0x89 && content[1] == 'P'
                    && content[2] == 'N' && content[3] == 'G';
        }
        if (!valid) {
            throw ApiException.badRequest("The file content does not match its declared type");
        }
    }
}
