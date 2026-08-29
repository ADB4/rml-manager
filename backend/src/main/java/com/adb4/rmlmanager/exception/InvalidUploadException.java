package com.adb4.rmlmanager.exception;

/**
 * Raised for client-side upload errors that are neither "not found" nor a size
 * violation: a missing filename, an extension that is not a known
 * {@code GeometryFileType}/{@code TextureFileType}, or an upload that disagrees
 * with its declared type. Mapped to HTTP 400 by {@code GlobalExceptionHandler}.
 *
 * <p>A dedicated typed exception keeps this in line with KAN-11, which removed
 * generic {@code IllegalArgumentException} usage from the service layer.
 */
public class InvalidUploadException extends RuntimeException {
    public InvalidUploadException(String message) {
        super(message);
    }
}
