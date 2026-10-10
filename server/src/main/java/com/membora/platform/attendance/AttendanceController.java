package com.membora.platform.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {
    private final VisitRepository repository;
    private final AttendanceService service;
    public AttendanceController(VisitRepository repository, AttendanceService service) { this.repository = repository; this.service = service; }

    @PostMapping("/check-in")
    @PreAuthorize("hasAuthority('ATTENDANCE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    Visit checkIn(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody CheckIn request) { return service.checkIn(organizationId, request.memberId(), request.method()); }

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    List<Visit> today(@RequestHeader("X-Organization-Id") UUID organizationId) {
        var since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return repository.findByOrganizationIdAndCheckedInAtAfterOrderByCheckedInAtDesc(organizationId, since);
    }

    record CheckIn(@NotNull UUID memberId, String method) {}
}
