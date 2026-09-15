package com.schedule.server.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Фильтр, логирующий ВСЕ входящие HTTP-запросы и ответы.
 * Помогает диагностировать проблемы: видно, дошёл ли запрос от бота до Java.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();

        log.info(">>> HTTP {} {} from {} Content-Type={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getContentType());

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int status = wrappedResponse.getStatus();

            // Логируем тело запроса (POST/PUT)
            byte[] requestBody = wrappedRequest.getContentAsByteArray();
            if (requestBody.length > 0 && requestBody.length < 2000) {
                String body = new String(requestBody, StandardCharsets.UTF_8);
                log.info(">>> Request body: {}", body);
            }

            // Логируем тело ответа при ошибке
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            if (status >= 400 && responseBody.length > 0 && responseBody.length < 2000) {
                String body = new String(responseBody, StandardCharsets.UTF_8);
                log.warn("<<< HTTP {} {} → {} ({}ms) Response: {}",
                        request.getMethod(), request.getRequestURI(), status, elapsed, body);
            } else {
                log.info("<<< HTTP {} {} → {} ({}ms)",
                        request.getMethod(), request.getRequestURI(), status, elapsed);
            }

            wrappedResponse.copyBodyToResponse();
        }
    }
}
