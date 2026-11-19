package com.medidrop.support;

import com.medidrop.common.PageResponse;
import com.medidrop.common.exception.ApiException;
import com.medidrop.notification.NotificationService;
import com.medidrop.security.Role;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository repository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public SupportDtos.TicketRow create(Long customerId, SupportDtos.CreateRequest request) {
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerId(customerId);
        ticket.setOrderId(request.orderId());
        ticket.setSubject(request.subject());
        ticket.setMessage(request.message());
        ticket.setStatus("OPEN");
        repository.save(ticket);
        return row(ticket, userRepository.findById(customerId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.TicketRow> customerTickets(Long customerId) {
        User customer = userRepository.findById(customerId).orElse(null);
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(t -> row(t, customer)).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportDtos.TicketRow> adminQueue(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<SupportTicket> tickets = (status == null || status.isBlank())
                ? repository.findAll(pageable)
                : repository.findByStatus(status.toUpperCase(), pageable);
        return PageResponse.of(tickets, t -> row(t, userRepository.findById(t.getCustomerId()).orElse(null)));
    }

    @Transactional
    public SupportDtos.TicketRow respond(Long adminId, Long ticketId, SupportDtos.RespondRequest request) {
        SupportTicket ticket = repository.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Support ticket not found"));
        ticket.setResponse(request.response());
        ticket.setStatus(request.closeTicket() ? "RESOLVED" : "IN_PROGRESS");
        ticket.setResolvedBy(adminId);
        ticket.setResolvedAt(Instant.now());
        repository.save(ticket);
        notificationService.notifyUser(ticket.getCustomerId(), "SUPPORT", "Support update",
                "Our team replied to: " + ticket.getSubject(), "/support");
        return row(ticket, userRepository.findById(ticket.getCustomerId()).orElse(null));
    }

    private SupportDtos.TicketRow row(SupportTicket t, User customer) {
        return new SupportDtos.TicketRow(t.getId(), t.getCustomerId(),
                customer == null ? "-" : customer.getFullName(), t.getOrderId(), t.getSubject(), t.getMessage(),
                t.getStatus(), t.getResponse(), t.getCreatedAt(), t.getResolvedAt());
    }

    @SuppressWarnings("unused")
    private Role roleOf(User user) {
        return user == null ? null : user.getRole();
    }
}
