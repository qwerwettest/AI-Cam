package com.schedule.server.controller;

import com.schedule.server.dto.ScheduleFileDto;
import com.schedule.server.service.ScheduleService;
import kvt.model.Auditory;
import kvt.model.AuditoryJournal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@Slf4j
public class ScheduleController {

    private final ScheduleService scheduleService;

    // =============== UPLOAD ===============

    /**
     * POST /api/schedule/upload
     * Принимает JSON расписания, извлекает кабинеты + время → auditory / auditory_journal.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadSchedule(@RequestBody ScheduleFileDto dto) {
        log.info("Received schedule upload: {}", dto.getFileName());

        Map<String, Object> saveResult = scheduleService.saveSchedule(dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Расписание успешно обработано");
        response.putAll(saveResult);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =============== AUDITORIES ===============

    /**
     * GET /api/schedule/auditories
     * Список всех аудиторий.
     */
    @GetMapping("/auditories")
    public ResponseEntity<List<Map<String, Object>>> getAllAuditories() {
        List<Auditory> auditories = scheduleService.getAllAuditories();

        List<Map<String, Object>> result = auditories.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("name", a.name());
            m.put("number", a.number());
            m.put("corpus", a.corpus());
            m.put("category", a.category());
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }

    // =============== JOURNAL ===============

    /**
     * GET /api/schedule/journal
     * Весь журнал занятости аудиторий.
     */
    @GetMapping("/journal")
    public ResponseEntity<List<Map<String, Object>>> getAllJournal() {
        List<AuditoryJournal> journal = scheduleService.getAllJournal();
        List<Map<String, Object>> result = journal.stream().map(this::mapJournalToResponse).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/schedule/journal/{audId}
     * Журнал занятости конкретной аудитории.
     */
    @GetMapping("/journal/{audId}")
    public ResponseEntity<List<Map<String, Object>>> getJournalByAuditory(@PathVariable int audId) {
        List<AuditoryJournal> journal = scheduleService.getJournalByAuditoryId(audId);
        List<Map<String, Object>> result = journal.stream().map(this::mapJournalToResponse).toList();
        return ResponseEntity.ok(result);
    }

    // =============== Маппинг-утилиты ===============

    private Map<String, Object> mapJournalToResponse(AuditoryJournal j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id());
        m.put("audId", j.audId());
        m.put("dayOfWeek", j.dayOfWeek());
        m.put("startTime", j.startTime());
        m.put("endTime", j.endTime());
        m.put("duration", j.duration());
        m.put("timeStatus", j.timeStatus());
        return m;
    }
}
