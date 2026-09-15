package com.schedule.server.dto;

/**
 * Входящий JSON от Telegram-бота.
 * { "id": <int>, "corpus": "<string>" }
 */
public record BotIn(int id, String corpus) {
}
