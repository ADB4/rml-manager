package com.adb4.rmlmanager.service;

import java.io.InputStream;
import java.time.Duration;

/**
 *   prefix conventions:
 *   geometry/{assetCode}/lod{level}/{fileName}
 *   geometry/{assetCode}/lod{level}/part/{meshPartCode}/{fileName}
 *   texture/{assetCode}/variant/{variantCode}/lod{level}/{mapType}/{fileName}
 *   texture/{assetCode}/variant/{variantCode}/lod{level}/part/{meshPartCode}/{mapType}/{fileName}
 */
public interface StorageService {

    /**
     * upload an object using a streaming body.
     *
     * @param key           object key (see prefix conventions above)
     * @param data          input stream; callers should <em>not</em> buffer the
     *                      entire file in memory
     * @param contentLength exact byte length of the stream
     * @param contentType   MIME type (e.g. {@code model/gltf-binary})
     */
    void put(String key, InputStream data, long contentLength, String contentType);

    /**
     given @param key object key, return a URL that grants temp read access
     */
    String presignedGetUrl(String key);

    /**
     * returns pre-signed URL with set expiration
     */
    String presignedGetUrl(String key, Duration expiration);

    /**
     * checks whether object at key exists
     */
    boolean exists(String key);

    /**
     * does not raise error if object does not exist
     */
    void delete(String key);
}