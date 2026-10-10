package com.membora.platform.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentRepository repository;
    private final PaymentService service;
    public PaymentController(PaymentRepository repository, PaymentService service) { this.repository = repository; this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_READ') or hasAuthority('PAYMENT_WRITE')")
    List<Payment> list(@RequestHeader("X-Organization-Id") UUID organizationId) { return repository.findByOrganizationIdOrderByPaidAtDesc(organizationId); }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    Payment record(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody RecordPayment request) {
        return service.record(organizationId, request.memberId(), request.membershipId(), request.amount(), request.method(), request.reference(), request.notes(), request.staffId());
    }

    record RecordPayment(@NotNull UUID memberId, UUID membershipId, @NotNull @Positive BigDecimal amount, @NotBlank String method, String reference, String notes, UUID staffId) {}
}
