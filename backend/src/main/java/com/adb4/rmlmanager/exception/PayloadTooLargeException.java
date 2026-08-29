package com.adb4.rmlmanager.exception;

/**
 * Raised when an uploaded file exceeds the configured maximum size. Mapped to
 * HTTP 413 (Payload Too Large) by {@code GlobalExceptionHandler}.
 */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String resourceName, long actualBytes, long maxBytes) {
        super(String.format("%s of %d bytes exceeds the maximum allowed size of %d bytes",
                resourceName, actualBytes, maxBytes));
    }
}
