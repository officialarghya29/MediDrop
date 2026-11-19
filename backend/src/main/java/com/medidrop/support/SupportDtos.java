package com.medidrop.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SupportDtos {

    private SupportDtos() {
    }

    public record CreateRequest(Long orderId,
                                @NotBlank @Size(max = 160) String subject,
                                @NotBlank @Size(min = 10, max = 2000) String message) {
    }

    public record RespondRequest(@NotBlank @Size(min = 2, max = 2000) String response,
                                 boolean closeTicket) {
    }

    public record TicketRow(Long id, Long customerId, String customerName, Long orderId, String subject,
                            String message, String status, String response, Instant createdAt,
                            Instant resolvedAt) {
    }
}
