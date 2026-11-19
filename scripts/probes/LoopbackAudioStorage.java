package cxj.probe;

import com.chengxinjian.media.MediaModels;
import com.chengxinjian.media.MediaStorage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.*;

/** Test-only provider loaded from a temporary directory. Never packaged into the backend. */
@Configuration
@Profile("local")
public class LoopbackAudioStorage {
    @Bean @Primary MediaStorage fixtureStorage() throws IOException { return new Store(); }

    static final class Store implements MediaStorage, AutoCloseable {
        record Permit(String method, String key, Instant expiry, long size) {}
        private final Map<String, Permit> permits = new ConcurrentHashMap<>();
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        private final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        Store() throws IOException {
            http.createContext("/object/", this::serve);
            http.start();
        }
        private void serve(HttpExchange request) throws IOException {
            try (request) {
                var permit = permits.get(request.getRequestURI().getPath().substring("/object/".length()));
                if (permit == null || !permit.expiry().isAfter(Instant.now()) || !permit.method().equals(request.getRequestMethod())) {
                    request.sendResponseHeaders(403, -1);
                    return;
                }
                if ("PUT".equals(permit.method())) {
                    var bytes = request.getRequestBody().readNBytes(32769);
                    if (bytes.length != permit.size()) {
                        request.sendResponseHeaders(400, -1);
                        return;
                    }
                    files.put(permit.key(), bytes);
                    request.sendResponseHeaders(204, -1);
                } else {
                    var bytes = files.get(permit.key());
                    if (bytes == null) { request.sendResponseHeaders(404, -1); return; }
                    request.getResponseHeaders().set("Content-Type", "audio/mpeg");
                    request.sendResponseHeaders(200, bytes.length);
                    request.getResponseBody().write(bytes);
                }
            }
        }
        private String permit(String method, String key, Instant expiry, long size) {
            String token = UUID.randomUUID().toString();
            permits.put(token, new Permit(method, key, expiry, size));
            return "http://127.0.0.1:" + http.getAddress().getPort() + "/object/" + token;
        }
        public MediaModels.Ticket ticket(String id, String key, String mime, long size, Instant expiry) {
            if (size > 32768) throw new IllegalArgumentException("Oversized fixture");
            return new MediaModels.Ticket(id, permit("PUT", key, expiry, size), "PUT", Map.of(), "file", expiry, size);
        }
        public void download(String key, Path dest, long size, String mime) {
            try { Files.write(dest, Objects.requireNonNull(files.get(key))); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
        public void put(String key, Path src, String mime) {
            try { files.put(key, Files.readAllBytes(src)); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
        public String preview(String key, Instant expiry) { return permit("GET", key, expiry, 0); }
        public void delete(String key) { files.remove(key); }
        public void close() { http.stop(0); }
    }
}
