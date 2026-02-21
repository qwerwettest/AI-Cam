package com.schedule.server.service;

import com.schedule.server.dto.*;
import kvt.db.AuditoryRepository;
import kvt.db.AuditoryJournalRepository;
import kvt.model.Auditory;
import kvt.model.AuditoryJournal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final AuditoryRepository auditoryRepository;
    private final AuditoryJournalRepository auditoryJournalRepository;

    /**
     * Принимает JSON расписания, извлекает кабинеты и время занятий,
     * сохраняет в существующие таблицы auditory + auditory_journal.
     */
    @Transactional
    public Map<String, Object> saveSchedule(ScheduleFileDto dto) {
        log.info("Saving schedule from file: {}", dto.getFileName());

        int auditoriesAdded = 0;
        int journalEntriesAdded = 0;

        if (dto.getSchedule() != null && dto.getSchedule().getGroupsSchedule() != null) {

            // Кэш существующих аудиторий по имени
            Map<String, Auditory> auditoryCache = new HashMap<>();
            for (Auditory a : auditoryRepository.findAll()) {
                auditoryCache.put(a.name(), a);
            }

            for (Map.Entry<String, List<DayScheduleDto>> groupEntry :
                    dto.getSchedule().getGroupsSchedule().entrySet()) {

                for (DayScheduleDto dayDto : groupEntry.getValue()) {
                    int dayOfWeek = parseDayOfWeek(dayDto.getDay());

                    if (dayDto.getLessons() == null) continue;

                    for (LessonDto lesson : dayDto.getLessons()) {
                        if (lesson.getRoom() == null || lesson.getRoom().isBlank()) continue;

                        String roomName = lesson.getRoom().trim();

                        // Найти или создать аудиторию
                        Auditory auditory = auditoryCache.get(roomName);
                        if (auditory == null) {
                            Integer roomNumber = extractNumber(roomName);
                            Auditory toInsert = new Auditory(0, roomName, roomNumber, null, null);
                            int generatedId = auditoryRepository.insert(toInsert);
                            auditory = new Auditory(generatedId, roomName, roomNumber, null, null);
                            auditoryCache.put(roomName, auditory);
                            auditoriesAdded++;
                            log.debug("Created auditory: id={}, name={}", generatedId, roomName);
                        }

                        // Парсим время урока и создаём запись в журнале
                        LocalTime[] times = parseTime(lesson.getTime());
                        if (times != null) {
                            int duration = (int) Duration.between(times[0], times[1]).toMinutes();
                            AuditoryJournal journal = new AuditoryJournal(
                                    0,
                                    auditory.id(),
                                    dayOfWeek,
                                    times[0],
                                    times[1],
                                    duration,
                                    1  // timeStatus = 1 (активно)
                            );
                            auditoryJournalRepository.insert(journal);
                            journalEntriesAdded++;
                        }
                    }
                }
            }
        }

        log.info("Schedule saved: {} auditories added, {} journal entries added",
                auditoriesAdded, journalEntriesAdded);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", dto.getFileName());
        result.put("auditoriesAdded", auditoriesAdded);
        result.put("journalEntriesAdded", journalEntriesAdded);
        return result;
    }

    /**
     * Список всех аудиторий.
     */
    public List<Auditory> getAllAuditories() {
        return auditoryRepository.findAll();
    }

    /**
     * Журнал занятости конкретной аудитории.
     */
    public List<AuditoryJournal> getJournalByAuditoryId(int audId) {
        return auditoryJournalRepository.findByAudId(audId);
    }

    /**
     * Весь журнал занятости.
     */
    public List<AuditoryJournal> getAllJournal() {
        return auditoryJournalRepository.findAll();
    }

    // =============== Утилиты ===============

    private static final Pattern TIME_PATTERN =
            Pattern.compile("(\\d{1,2}[.:;]\\d{2})\\s*[-–—]\\s*(\\d{1,2}[.:;]\\d{2})");

    /**
     * Парсит строку вида "8:30-10:00" или "8.30–10.00" в пару LocalTime.
     */
    private LocalTime[] parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        Matcher m = TIME_PATTERN.matcher(timeStr.trim());
        if (!m.find()) return null;
        try {
            String startStr = m.group(1).replaceAll("[.;]", ":");
            String endStr = m.group(2).replaceAll("[.;]", ":");
            // LocalTime.parse требует формат HH:mm, дополняем ведущий ноль
            if (startStr.length() == 4) startStr = "0" + startStr;
            if (endStr.length() == 4) endStr = "0" + endStr;
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            return new LocalTime[]{start, end};
        } catch (Exception e) {
            log.warn("Cannot parse time: {}", timeStr);
            return null;
        }
    }

    /**
     * Конвертирует название дня недели в число 1-7.
     */
    private int parseDayOfWeek(String day) {
        if (day == null) return 0;
        return switch (day.trim().toLowerCase()) {
            case "понедельник" -> 1;
            case "вторник" -> 2;
            case "среда" -> 3;
            case "четверг" -> 4;
            case "пятница" -> 5;
            case "суббота" -> 6;
            case "воскресенье" -> 7;
            default -> 0;
        };
    }

    /**
     * Извлекает числовую часть из названия кабинета, напр. "301а" -> 301.
     */
    private Integer extractNumber(String roomName) {
        if (roomName == null) return null;
        Matcher m = Pattern.compile("(\\d+)").matcher(roomName);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }
}
