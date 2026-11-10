package com.chengxinjian.media;

import java.nio.file.Path;
import java.time.Instant;

public interface MediaStorage {
    MediaModels.Ticket ticket(String assetId, String key, String mime, long size, Instant expires);
    void download(String key, Path destination, long expectedSize, String mime);
    void put(String key, Path source, String mime);
    String preview(String key, Instant expires);
    void delete(String key);
}
