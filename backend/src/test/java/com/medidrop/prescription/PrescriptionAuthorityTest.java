package com.medidrop.prescription;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.exception.ApiException;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrescriptionAuthorityTest {

    @Autowired private PrescriptionService prescriptionService;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private PharmacyRepository pharmacyRepository;

    @Test
    void unverifiedPharmacyOwnerCannotApprovePrescription() {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setOwnerUserId(730_001L);
        pharmacy.setName("Unverified pharmacy");
        pharmacy.setLicenseNumber("WB-AUTH-" + UUID.randomUUID().toString().substring(0, 8));
        pharmacy.setRegisteredAddress("1 Test Road");
        pharmacy.setCity("Kolkata");
        pharmacy.setState("West Bengal");
        pharmacy.setPincode("700001");
        pharmacy.setLatitude(22.5726);
        pharmacy.setLongitude(88.3639);
        pharmacy.setPhone("9800000000");
        pharmacy.setEmail("auth@example.test");
        pharmacy.setStatus(ApprovalStatus.APPROVED);
        pharmacy = pharmacyRepository.save(pharmacy);

        Prescription prescription = new Prescription();
        prescription.setCustomerId(730_002L);
        prescription.setFileName("rx.png");
        prescription.setStorageKey("test/" + UUID.randomUUID());
        prescription.setContentType("image/png");
        prescription.setSizeBytes(12L);
        prescription.setPharmacyId(pharmacy.getId());
        prescription.setStatus(PrescriptionStatus.UPLOADED);
        Prescription saved = prescriptionRepository.save(prescription);

        assertThatThrownBy(() -> prescriptionService.review(730_001L, saved.getId(),
                new PrescriptionDtos.ReviewDecisionRequest(true, "ok", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("verified pharmacist");
    }
}
