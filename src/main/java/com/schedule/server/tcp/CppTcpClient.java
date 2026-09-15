package com.schedule.server.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP-клиент для общения с C++ сервером.
 * Протокол: 4 байта big-endian (длина JSON) + JSON UTF-8.
 *
 * <p>Java-сервер выступает исключительно как мост (bridge):
 * принимает запрос от бота, передаёт параметры поиска на C++ сервер,
 * получает готовый результат и возвращает его боту.
 *
 * <p>Все запросы проходят через внутреннюю FIFO-очередь и обрабатываются
 * строго последовательно — новый запрос не отправляется, пока не получен
 * ответ (или ошибка) на предыдущий.
 */
@Component
@Slf4j
public class CppTcpClient {

    private final String host;
    private final int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Однопоточный executor гарантирует FIFO-порядок и
     * отправку не более одного запроса одновременно.
     */
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cpp-tcp-queue");
        t.setDaemon(true);
        return t;
    });

    /** Счётчик запросов, ожидающих в очереди (для мониторинга). */
    private final AtomicInteger pendingCount = new AtomicInteger(0);

    /** Максимальное время ожидания ответа от C++ (вместе с ожиданием в очереди). */
    private static final long TOTAL_TIMEOUT_SECONDS = 30;

    public CppTcpClient(
            @Value("${cpp.server.host}") String host,
            @Value("${cpp.server.port}") int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Отправляет произвольный JSON-запрос на C++ сервер через FIFO-очередь.
     * Блокирует вызывающий поток до получения ответа.
     *
     * @param requestPayload  карта ключ-значение, которая будет отправлена как JSON
     * @return распарсенный JSON-ответ от C++ в виде дерева JsonNode
     */
    public JsonNode send(Map<String, Object> requestPayload) {
        int position = pendingCount.incrementAndGet();
        log.info("Enqueued C++ request: payload={}, queuePosition={}", requestPayload, position);

        Future<JsonNode> future = queue.submit(() -> {
            try {
                return doSend(requestPayload);
            } finally {
                pendingCount.decrementAndGet();
            }
        });

        try {
            return future.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException(
                    "Таймаут ожидания ответа от C++ сервера (очередь + соединение > "
                            + TOTAL_TIMEOUT_SECONDS + " сек)", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Ошибка при запросе к C++ серверу: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Запрос к C++ серверу прерван", e);
        }
    }

    /**
     * Возвращает количество запросов, ожидающих обработки (включая текущий).
     */
    public int getPendingCount() {
        return pendingCount.get();
    }

    /**
     * Корректное завершение очереди при остановке приложения.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down C++ TCP queue, pending={}", pendingCount.get());
        queue.shutdown();
        try {
            if (!queue.awaitTermination(10, TimeUnit.SECONDS)) {
                queue.shutdownNow();
                log.warn("C++ TCP queue did not terminate in time, forced shutdown");
            }
        } catch (InterruptedException e) {
            queue.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========================= Внутренняя реализация =========================

    /**
     * Фактическая отправка TCP-запроса. Вызывается только из потока очереди,
     * поэтому к C++ одновременно идёт ровно один запрос.
     */
    @SuppressWarnings("unchecked")
    private JsonNode doSend(Map<String, Object> payload) {
        log.info("Sending to C++ server {}:{} — payload={}", host, port, payload);

        // --- формируем JSON запроса ---
        JSONObject request = new JSONObject();
        request.putAll(payload);

        byte[] jsonBytes = request.toJSONString().getBytes(StandardCharsets.UTF_8);

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10_000); // 10 секунд таймаут на чтение

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));

            // --- отправка: 4 байта длины + JSON ---
            out.writeInt(jsonBytes.length);
            out.write(jsonBytes);
            out.flush();
            log.debug("Sent {} bytes to C++ server: {}", jsonBytes.length, request.toJSONString());

            String responseJson = readResponse(socket);
            log.debug("Received from C++ server: {}", responseJson);

            return objectMapper.readTree(responseJson);

        } catch (IOException e) {
            throw new RuntimeException("Ошибка TCP-соединения с C++ сервером: " + e.getMessage(), e);
        }
    }

    private static String readResponse(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));

        // --- чтение ответа: 4 байта длины + JSON ---
        int responseLength = in.readInt();
        if (responseLength <= 0 || responseLength > 1_000_000) {
            throw new IOException("Некорректная длина ответа от C++ сервера: " + responseLength);
        }

        byte[] responseBytes = new byte[responseLength];
        in.readFully(responseBytes);
        return new String(responseBytes, StandardCharsets.UTF_8);
    }
}
