package com.medidrop.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<SupportTicket> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);
}
