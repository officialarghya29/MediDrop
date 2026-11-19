package com.medidrop.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryPartnerDocumentRepository extends JpaRepository<DeliveryPartnerDocument, Long> {

    List<DeliveryPartnerDocument> findByPartnerId(Long partnerId);
}
