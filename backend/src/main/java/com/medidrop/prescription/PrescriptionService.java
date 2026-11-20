package com.medidrop.prescription;

import com.medidrop.audit.AuditService;
import com.medidrop.common.FileValidation;
import com.medidrop.common.PageResponse;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import com.medidrop.notification.NotificationService;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderService;
import com.medidrop.order.OrderStatus;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.security.Role;
import com.medidrop.storage.FileStorage;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionReviewRepository reviewRepository;
    private final PharmacyRepository pharmacyRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final FileStorage fileStorage;
    private final OcrService ocrService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MedidropProperties properties;

    @Transactional
    public PrescriptionDtos.PrescriptionResponse upload(Long customerId, MultipartFile file, String notes,
                                                        Long pharmacyId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Select a prescription file to upload");
        }
        long max = properties.getStorage().getMaxFileSizeBytes();
        if (file.getSize() > max) {
            throw ApiException.badRequest("Prescription must be smaller than " + (max / (1024 * 1024)) + " MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!List.of("application/pdf", "image/jpeg", "image/png").contains(contentType)) {
            throw ApiException.badRequest("Only PDF, JPEG or PNG prescriptions are accepted");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
        FileValidation.assertMagicBytes(content, contentType);
        if (pharmacyId != null) {
            pharmacyRepository.findById(pharmacyId)
                    .filter(p -> p.getStatus() == com.medidrop.common.ApprovalStatus.APPROVED && p.isActive())
                    .orElseThrow(() -> ApiException.badRequest("Selected pharmacy is not available"));
        }

        FileStorage.StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorage.store(in, file.getOriginalFilename(), contentType, "prescriptions");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store prescription", e);
        }

        Prescription prescription = new Prescription();
        prescription.setCustomerId(customerId);
        prescription.setFileName(safeName(file.getOriginalFilename()));
        prescription.setStorageKey(stored.key());
        prescription.setContentType(contentType);
        prescription.setSizeBytes(stored.size());
        prescription.setNotes(notes);
        prescription.setPharmacyId(pharmacyId);
        prescription.setStatus(PrescriptionStatus.UPLOADED);
        ocrService.extract(content, contentType).ifPresent(result -> {
            prescription.setOcrExtractedText(result.text());
            prescription.setOcrConfidence(java.math.BigDecimal.valueOf(result.confidence()));
        });
        prescriptionRepository.save(prescription);
        auditService.recordForUser(customerId, Role.CUSTOMER.name(), "PRESCRIPTION_UPLOADED", "Prescription",
                prescription.getId(), null);
        return toDto(prescription, false);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionDtos.PrescriptionResponse> customerList(Long customerId) {
        return prescriptionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(p -> toDto(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionDtos.CustomerPrescriptionDetail customerDetail(Long customerId, Long id) {
        Prescription prescription = prescriptionRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        List<PrescriptionDtos.ReviewHistory> reviews = reviewRepository
                .findByPrescriptionIdOrderByReviewedAtDesc(id).stream()
                .map(r -> new PrescriptionDtos.ReviewHistory(r.getId(), r.getDecision(), r.getComments(),
                        r.getReviewedAt()))
                .toList();
        return new PrescriptionDtos.CustomerPrescriptionDetail(toDto(prescription, false), reviews);
    }

    @Transactional
    public void delete(Long customerId, Long id) {
        Prescription prescription = prescriptionRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        if (prescription.getOrderId() != null) {
            throw ApiException.conflict("This prescription is linked to an order and cannot be deleted");
        }
        fileStorage.delete(prescription.getStorageKey());
        prescriptionRepository.delete(prescription);
        auditService.recordForUser(customerId, Role.CUSTOMER.name(), "PRESCRIPTION_DELETED", "Prescription", id,
                null);
    }

    @Transactional(readOnly = true)
    public Resource loadFile(Long actorId, Role role, Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        boolean permitted = switch (role) {
            case CUSTOMER -> prescription.getCustomerId().equals(actorId);
            case PHARMACY -> {
                Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(actorId).orElse(null);
                // Suspension must remove PHI access immediately, not just block new reviews.
                yield pharmacy != null
                        && pharmacy.getStatus() == com.medidrop.common.ApprovalStatus.APPROVED
                        && pharmacy.isActive()
                        && pharmacy.getId().equals(prescription.getPharmacyId());
            }
            case ADMIN -> true;
            default -> false;
        };
        if (!permitted) {
            throw ApiException.forbidden("You cannot access this prescription");
        }
        return fileStorage.load(prescription.getStorageKey());
    }

    @Transactional(readOnly = true)
    public String fileContentType(Long prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> ApiException.notFound("Prescription not found")).getContentType();
    }

    @Transactional(readOnly = true)
    public PageResponse<PrescriptionDtos.PendingReview> pharmacyQueue(Long userId, String status, int page, int size) {
        Pharmacy pharmacy = activePharmacy(userId);
        PrescriptionStatus filter = parseStatus(status);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Prescription> prescriptions = prescriptionRepository
                .findByPharmacyIdAndStatus(pharmacy.getId(), filter, pageable);
        List<PrescriptionDtos.PendingReview> rows = prescriptions.getContent().stream().map(p -> {
            User customer = userRepository.findById(p.getCustomerId()).orElse(null);
            String orderNumber = p.getOrderId() == null ? null
                    : orderRepository.findById(p.getOrderId()).map(Order::getOrderNumber).orElse(null);
            return new PrescriptionDtos.PendingReview(p.getId(), p.getCustomerId(),
                    customer == null ? "-" : customer.getFullName(), p.getFileName(), p.getCreatedAt(),
                    p.getStatus().name(), p.getNotes(), p.getOrderId(), orderNumber);
        }).toList();
        return new PageResponse<>(rows, prescriptions.getNumber(), prescriptions.getSize(),
                prescriptions.getTotalElements(), prescriptions.getTotalPages(), prescriptions.isFirst(),
                prescriptions.isLast());
    }

    @Transactional
    public PrescriptionDtos.PrescriptionResponse review(Long pharmacistUserId, Long prescriptionId,
                                                        PrescriptionDtos.ReviewDecisionRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(pharmacistUserId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
        if (pharmacy.getStatus() != com.medidrop.common.ApprovalStatus.APPROVED || !pharmacy.isActive()) {
            throw ApiException.forbidden("Pharmacy must be active before reviewing prescriptions");
        }
        if (!pharmacy.isPharmacistVerified()
                || pharmacy.getPharmacistRegistrationNumber() == null
                || pharmacy.getPharmacistRegistrationNumber().isBlank()) {
            throw ApiException.forbidden("A verified pharmacist must be linked to this pharmacy before review");
        }
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        if (!pharmacy.getId().equals(prescription.getPharmacyId())) {
            throw ApiException.forbidden("This prescription is assigned to another pharmacy");
        }
        if (prescription.getStatus() == PrescriptionStatus.APPROVED
                || prescription.getStatus() == PrescriptionStatus.REJECTED) {
            throw ApiException.conflict("This prescription has already been reviewed");
        }
        PrescriptionReview review = new PrescriptionReview();
        review.setPrescriptionId(prescription.getId());
        review.setPharmacistUserId(pharmacistUserId);
        review.setPharmacistRegistrationNumber(pharmacy.getPharmacistRegistrationNumber());
        review.setDecision(Boolean.TRUE.equals(request.approve()) ? "APPROVED" : "REJECTED");
        review.setComments(request.comments());
        review.setVerifiedItems(request.verifiedItems());
        review.setReviewedAt(Instant.now());
        reviewRepository.save(review);

        prescription.setStatus(Boolean.TRUE.equals(request.approve())
                ? PrescriptionStatus.APPROVED : PrescriptionStatus.REJECTED);
        prescription.setReviewedBy(pharmacistUserId);
        prescription.setReviewedByRegistration(pharmacy.getPharmacistRegistrationNumber());
        prescription.setReviewedAt(Instant.now());
        if (!Boolean.TRUE.equals(request.approve())) {
            prescription.setRejectionReason(request.comments());
        }
        prescriptionRepository.save(prescription);

        if (prescription.getOrderId() != null) {
            Order order = orderRepository.findById(prescription.getOrderId()).orElse(null);
            if (order != null && order.getStatus() == OrderStatus.PRESCRIPTION_REVIEW_PENDING) {
                orderService.systemTransition(order, Boolean.TRUE.equals(request.approve())
                        ? OrderStatus.PRESCRIPTION_APPROVED : OrderStatus.PRESCRIPTION_REJECTED,
                        Boolean.TRUE.equals(request.approve())
                                ? "Prescription approved by pharmacist"
                                : "Prescription rejected: " + request.comments());
            }
        }
        notificationService.notifyUser(prescription.getCustomerId(), "PRESCRIPTION",
                Boolean.TRUE.equals(request.approve()) ? "Prescription approved" : "Prescription needs attention",
                Boolean.TRUE.equals(request.approve())
                        ? "Your prescription was verified by a licensed pharmacist."
                        : "Your prescription was rejected: " + request.comments(),
                "/prescriptions");
        auditService.recordForUser(pharmacistUserId, Role.PHARMACY.name(),
                Boolean.TRUE.equals(request.approve()) ? "PRESCRIPTION_APPROVED" : "PRESCRIPTION_REJECTED",
                "Prescription", prescription.getId(), request.comments());
        return toDto(prescription, true);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionDtos.ReviewHistory> reviews(Long userId, Long prescriptionId) {
        Pharmacy pharmacy = activePharmacy(userId);
        prescriptionRepository.findByIdAndPharmacyId(prescriptionId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Prescription not found"));
        return reviewRepository.findByPrescriptionIdOrderByReviewedAtDesc(prescriptionId).stream()
                .map(r -> new PrescriptionDtos.ReviewHistory(r.getId(), r.getDecision(), r.getComments(),
                        r.getReviewedAt()))
                .toList();
    }

    private Pharmacy activePharmacy(Long userId) {
        Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
        if (pharmacy.getStatus() != com.medidrop.common.ApprovalStatus.APPROVED || !pharmacy.isActive()) {
            throw ApiException.forbidden("This pharmacy is not active. Contact support.");
        }
        return pharmacy;
    }

    private PrescriptionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return PrescriptionStatus.UPLOADED;
        try {
            return PrescriptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown prescription status: " + status);
        }
    }

    private PrescriptionDtos.PrescriptionResponse toDto(Prescription p, boolean staff) {
        return new PrescriptionDtos.PrescriptionResponse(p.getId(), p.getFileName(), p.getContentType(),
                p.getSizeBytes(), p.getStatus().name(), p.getPharmacyId(), p.getOrderId(), p.getNotes(),
                p.getRejectionReason(), p.getCreatedAt(), p.getReviewedAt(),
                "/api/v1/customer/prescriptions/" + p.getId() + "/file",
                staff ? p.getOcrExtractedText() : null, p.isDemo());
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "prescription";
        String cleaned = name.replaceAll("[\\\\/\\r\\n]", "_").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
