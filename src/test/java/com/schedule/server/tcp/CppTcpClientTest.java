package com.schedule.server.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты TCP-соединения с C++ сервером.
 *
 * <p>Используется встроенный mock TCP-сервер, который эмулирует протокол
 * C++ сервера (4 байта big-endian длина + JSON UTF-8).</p>
 */
class CppTcpClientTest {

    private ServerSocket mockServer;
    private ExecutorService executor;
    private int serverPort;

    /** Payload в том же формате, что собирает BotBridgeService. */
    private static Map<String, Object> payload(int id, String corpus, String startTime, int duration) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("start_time", startTime);
        p.put("duration", duration);
        p.put("corpus", corpus);
        return p;
    }

    @BeforeEach
    void setUp() throws IOException {
        // Запускаем mock TCP-сервер на случайном свободном порту
        mockServer = new ServerSocket(0);
        serverPort = mockServer.getLocalPort();
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws IOException {
        executor.shutdownNow();
        if (mockServer != null && !mockServer.isClosed()) {
            mockServer.close();
        }
    }

    /**
     * Тест успешного соединения и обмена данными с C++ сервером.
     * Mock-сервер читает запрос и возвращает корректный JSON-ответ.
     */
    @Test
    @DisplayName("Успешное TCP соединение и обмен данными")
    void testSuccessfulConnection() throws Exception {
        // Настраиваем mock-сервер: принимаем соединение, читаем запрос, отправляем ответ
        Future<String> serverTask = executor.submit(() -> {
            try (Socket client = mockServer.accept()) {
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(client.getInputStream()));
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(client.getOutputStream()));

                // Читаем запрос
                int requestLength = in.readInt();
                byte[] requestBytes = new byte[requestLength];
                in.readFully(requestBytes);
                String requestJson = new String(requestBytes, StandardCharsets.UTF_8);

                // Формируем ответ
                @SuppressWarnings("unchecked")
                JSONObject response = new JSONObject();
                response.put("id", 42);
                response.put("cabinet_number", "А-301");
                byte[] responseBytes = response.toJSONString().getBytes(StandardCharsets.UTF_8);

                out.writeInt(responseBytes.length);
                out.write(responseBytes);
                out.flush();

                return requestJson;
            }
        });

        // Создаём клиент, указывающий на mock-сервер
        CppTcpClient client = new CppTcpClient("localhost", serverPort);

        // Отправляем запрос
        JsonNode result = client.send(payload(42, "main", "10:00", 90));

        // Проверяем ответ
        assertNotNull(result, "Ответ от C++ сервера не должен быть null");
        assertEquals(42, result.get("id").asInt(), "ID в ответе должен совпадать");
        assertEquals("А-301", result.get("cabinet_number").asText(), "Номер кабинета должен совпадать");

        // Проверяем, что сервер получил корректный запрос
        String receivedRequest = serverTask.get(5, TimeUnit.SECONDS);
        assertNotNull(receivedRequest, "Сервер должен получить запрос");
        assertTrue(receivedRequest.contains("\"id\":42"), "Запрос должен содержать id");
        assertTrue(receivedRequest.contains("\"corpus\":\"main\""), "Запрос должен содержать corpus");
    }

    /**
     * Тест: сервер недоступен — ожидаем RuntimeException.
     */
    @Test
    @DisplayName("Ошибка соединения при недоступном сервере")
    void testConnectionRefused() throws IOException {
        // Закрываем mock-сервер, чтобы порт был недоступен
        mockServer.close();

        CppTcpClient client = new CppTcpClient("localhost", serverPort);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> client.send(payload(1, "test", "08:00", 45)));

        assertTrue(exception.getMessage().contains("TCP"),
                "Сообщение исключения должно указывать на ошибку TCP");
    }

    /**
     * Тест: сервер отвечает некорректным JSON — ожидаем RuntimeException.
     */
    @Test
    @DisplayName("Ошибка парсинга при невалидном JSON от сервера")
    void testInvalidJsonResponse() {
        executor.submit(() -> {
            try (Socket client = mockServer.accept()) {
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(client.getInputStream()));
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(client.getOutputStream()));

                // Читаем запрос
                int requestLength = in.readInt();
                byte[] requestBytes = new byte[requestLength];
                in.readFully(requestBytes);

                // Отправляем невалидный JSON
                byte[] badJson = "NOT_A_JSON{{{".getBytes(StandardCharsets.UTF_8);
                out.writeInt(badJson.length);
                out.write(badJson);
                out.flush();
            }
            return null;
        });

        CppTcpClient client = new CppTcpClient("localhost", serverPort);

        // Jackson бросает JsonProcessingException (наследник IOException),
        // клиент оборачивает её в RuntimeException с текстом об ошибке соединения.
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> client.send(payload(1, "test", "08:00", 45)));

        assertNotNull(exception.getMessage(), "Исключение должно нести сообщение об ошибке");
    }

    /**
     * Тест: сервер отвечает некорректной длиной — ожидаем RuntimeException.
     */
    @Test
    @DisplayName("Ошибка при некорректной длине ответа от сервера")
    void testInvalidResponseLength() {
        executor.submit(() -> {
            try (Socket client = mockServer.accept()) {
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(client.getInputStream()));
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(client.getOutputStream()));

                // Читаем запрос
                int requestLength = in.readInt();
                byte[] requestBytes = new byte[requestLength];
                in.readFully(requestBytes);

                // Отправляем отрицательную длину
                out.writeInt(-1);
                out.flush();
            }
            return null;
        });

        CppTcpClient client = new CppTcpClient("localhost", serverPort);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> client.send(payload(1, "test", "08:00", 45)));

        assertTrue(exception.getMessage().contains("длина") || exception.getMessage().contains("TCP"),
                "Сообщение исключения должно указывать на ошибку длины ответа");
    }

    /**
     * Тест: сервер закрывает соединение после получения запроса — ожидаем RuntimeException.
     */
    @Test
    @DisplayName("Ошибка при преждевременном разрыве соединения")
    void testServerDisconnect() {
        executor.submit(() -> {
            try (Socket client = mockServer.accept()) {
                DataInputStream in = new DataInputStream(
                        new BufferedInputStream(client.getInputStream()));

                // Читаем запрос и сразу закрываем соединение
                int requestLength = in.readInt();
                byte[] requestBytes = new byte[requestLength];
                in.readFully(requestBytes);
                // Закрытие client (try-with-resources) разорвет соединение
            }
            return null;
        });

        CppTcpClient client = new CppTcpClient("localhost", serverPort);

        assertThrows(RuntimeException.class,
                () -> client.send(payload(1, "test", "08:00", 45)),
                "Должно выброситься исключение при разрыве соединения");
    }

    /**
     * Тест с различными входными данными.
     */
    @Test
    @DisplayName("Корректная передача различных параметров")
    void testDifferentParameters() throws Exception {
        Object[][] testCases = {
                {1, "09:00", 30},
                {100, "23:00", 120},
                {7, "12:00", 60},
        };

        for (Object[] tc : testCases) {
            int id = (int) tc[0];
            String startTime = (String) tc[1];
            int duration = (int) tc[2];

            // Каждый раз переоткрываем mock-сервер
            if (mockServer.isClosed()) {
                mockServer = new ServerSocket(0);
                serverPort = mockServer.getLocalPort();
            }

            executor.submit(() -> {
                try (Socket client = mockServer.accept()) {
                    DataInputStream in = new DataInputStream(
                            new BufferedInputStream(client.getInputStream()));
                    DataOutputStream out = new DataOutputStream(
                            new BufferedOutputStream(client.getOutputStream()));

                    int reqLen = in.readInt();
                    byte[] reqBytes = new byte[reqLen];
                    in.readFully(reqBytes);

                    @SuppressWarnings("unchecked")
                    JSONObject response = new JSONObject();
                    response.put("id", id);
                    response.put("cabinet_number", "Б-" + id);
                    byte[] respBytes = response.toJSONString().getBytes(StandardCharsets.UTF_8);

                    out.writeInt(respBytes.length);
                    out.write(respBytes);
                    out.flush();
                }
                return null;
            });

            CppTcpClient client = new CppTcpClient("localhost", serverPort);
            JsonNode result = client.send(payload(id, "corpus", startTime, duration));

            assertEquals(id, result.get("id").asInt(),
                    String.format("ID должен совпадать для testCase id=%d", id));
            assertEquals("Б-" + id, result.get("cabinet_number").asText(),
                    String.format("cabinet_number должен совпадать для testCase id=%d", id));
        }
    }
}
