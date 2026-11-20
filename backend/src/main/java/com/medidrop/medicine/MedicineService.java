package com.medidrop.medicine;

import com.medidrop.common.Haversine;
import com.medidrop.common.PageResponse;
import com.medidrop.common.exception.ApiException;
import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final MedicineCategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final PharmacyRepository pharmacyRepository;

    @Transactional(readOnly = true)
    public MedicineDtos.SearchResponse search(String query, Long categoryId, Long manufacturerId,
                                              Long pharmacyId, Boolean prescriptionRequired,
                                              String sortBy, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        Sort sort = switch (sortBy == null ? "relevance" : sortBy) {
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.ASC, "name");
        };
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        String q = (query == null || query.isBlank()) ? null : query.trim();
        Page<Medicine> result = medicineRepository.search(q, categoryId, manufacturerId, pharmacyId,
                prescriptionRequired, pageable);

        List<MedicineDtos.MedicineSummary> items = enrich(result.getContent());
        if ("price".equals(sortBy)) {
            items = items.stream()
                    .sorted(Comparator.comparing((MedicineDtos.MedicineSummary s) ->
                            s.lowestPrice() == null ? new BigDecimal("999999") : s.lowestPrice()))
                    .toList();
        }
        String suggestion = null;
        List<String> suggestions = List.of();
        if (q != null && result.getTotalElements() == 0) {
            suggestion = nearestName(q);
            suggestions = medicineRepository.suggest(q.length() > 2 ? q.substring(0, 2) : q, PageRequest.of(0, 6));
        }
        return new MedicineDtos.SearchResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), suggestion, suggestions);
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicineDtos.MedicineSummary> browse(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("name"));
        Page<Medicine> pageResult = medicineRepository.search(null, null, null, null, null, pageable);
        List<MedicineDtos.MedicineSummary> all = enrich(pageResult.getContent());
        return new PageResponse<>(all, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages(), pageResult.isFirst(), pageResult.isLast());
    }

    @Transactional(readOnly = true)
    public MedicineDtos.MedicineDetail detail(Long id, Double lat, Double lng) {
        Medicine medicine = medicineRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ApiException.notFound("Medicine not found"));
        List<PharmacyInventory> inventory = inventoryRepository.findAvailableByMedicine(id);
        Map<Long, Pharmacy> pharmacies = pharmacyRepository.findAllById(
                        inventory.stream().map(PharmacyInventory::getPharmacyId).toList())
                .stream().collect(Collectors.toMap(Pharmacy::getId, Function.identity()));

        List<MedicineDtos.Availability> availability = inventory.stream()
                .filter(i -> pharmacies.containsKey(i.getPharmacyId()))
                .map(i -> {
                    Pharmacy p = pharmacies.get(i.getPharmacyId());
                    Double distance = (lat != null && lng != null)
                            ? round(Haversine.distanceKm(lat, lng, p.getLatitude(), p.getLongitude())) : null;
                    return new MedicineDtos.Availability(i.getId(), p.getId(), p.getName(), p.getCity(),
                            distance, i.getPrice(), i.getMrp(), i.availableQuantity(), p.isAcceptingOrders());
                })
                .sorted(Comparator.comparing(a -> a.distanceKm() == null ? 9999.0 : a.distanceKm()))
                .toList();

        String categoryName = categoryRepository.findById(medicine.getCategoryId())
                .map(MedicineCategory::getName).orElse(null);
        String manufacturerName = medicine.getManufacturerId() == null ? null
                : manufacturerRepository.findById(medicine.getManufacturerId())
                .map(Manufacturer::getName).orElse(null);
        int totalStock = availability.stream().mapToInt(MedicineDtos.Availability::availableQuantity).sum();
        BigDecimal lowest = availability.stream().map(MedicineDtos.Availability::price)
                .min(BigDecimal::compareTo).orElse(null);
        BigDecimal mrp = availability.stream().map(MedicineDtos.Availability::mrp)
                .max(BigDecimal::compareTo).orElse(null);

        return new MedicineDtos.MedicineDetail(medicine.getId(), medicine.getName(), medicine.getBrand(),
                medicine.getGenericName(), medicine.getStrength(), medicine.getDosageForm(), medicine.getPackSize(),
                manufacturerName, categoryName, medicine.isPrescriptionRequired(), medicine.getImageUrl(),
                medicine.getDescription(), medicine.getWarnings(), medicine.getScheduleClass(),
                lowest, mrp, totalStock, availability.isEmpty() ? null : availability.get(0), availability);
    }

    @Transactional(readOnly = true)
    public MedicineDtos.SubstituteResponse substitutes(Long medicineId, Long pharmacyId, int limit) {
        Medicine original = medicineRepository.findByIdAndActiveTrue(medicineId)
                .orElseThrow(() -> ApiException.notFound("Medicine not found"));

        String generic = normalised(original.getGenericName());
        String strength = normalised(original.getStrength());
        String form = normalised(original.getDosageForm());
        String pack = normalised(original.getPackSize());
        if (generic == null || strength == null || form == null || pack == null) {
            return emptySubstitutes(original, pharmacyId, null, null);
        }

        Long comparePharmacyId = pharmacyId;
        Pharmacy pharmacy = null;
        BigDecimal referencePrice = null;
        if (comparePharmacyId != null) {
            pharmacy = pharmacyRepository.findById(comparePharmacyId).orElse(null);
            if (!usablePharmacy(pharmacy)) return emptySubstitutes(original, comparePharmacyId, null, null);
            PharmacyInventory reference = inventoryRepository
                    .findByPharmacyIdAndMedicineId(comparePharmacyId, medicineId)
                    .filter(i -> i.isActive() && i.availableQuantity() > 0)
                    .orElse(null);
            if (reference != null) referencePrice = reference.getPrice();
        } else {
            // The repository is price-ordered. Walk it until the first pharmacy that can
            // actually accept an order; a paused cheapest pharmacy must not hide a valid match.
            for (PharmacyInventory candidateStock : inventoryRepository.findAvailableByMedicine(medicineId)) {
                Pharmacy candidatePharmacy = pharmacyRepository.findById(candidateStock.getPharmacyId()).orElse(null);
                if (usablePharmacy(candidatePharmacy)) {
                    comparePharmacyId = candidateStock.getPharmacyId();
                    referencePrice = candidateStock.getPrice();
                    pharmacy = candidatePharmacy;
                    break;
                }
            }
        }
        if (comparePharmacyId == null || pharmacy == null || referencePrice == null) {
            return emptySubstitutes(original, pharmacyId, null, null);
        }

        final Long selectedPharmacyId = comparePharmacyId;
        final Pharmacy selectedPharmacy = pharmacy;
        final BigDecimal selectedReferencePrice = referencePrice;
        List<Medicine> candidates = medicineRepository.findSubstituteCandidates(original.getId(),
                original.getGenericName(), original.getStrength(), original.getDosageForm(),
                original.getPackSize(), original.isPrescriptionRequired(), original.getScheduleClass());
        if (candidates.isEmpty()) {
            return emptySubstitutes(original, selectedPharmacyId, selectedPharmacy.getName(), selectedReferencePrice);
        }
        Map<Long, Medicine> byId = candidates.stream()
                .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<Long, PharmacyInventory> stock = inventoryRepository
                .findByPharmacyIdAndMedicineIdInAndActiveTrue(selectedPharmacyId, new ArrayList<>(byId.keySet()))
                .stream()
                .filter(i -> i.availableQuantity() > 0)
                .filter(i -> i.getPrice().compareTo(selectedReferencePrice) < 0)
                .collect(Collectors.toMap(PharmacyInventory::getMedicineId, Function.identity()));

        int safeLimit = Math.min(Math.max(limit, 1), 5);
        List<MedicineDtos.SubstituteOption> options = stock.values().stream()
                .map(inventory -> {
                    Medicine candidate = byId.get(inventory.getMedicineId());
                    BigDecimal savings = selectedReferencePrice.subtract(inventory.getPrice());
                    int percent = savings.multiply(BigDecimal.valueOf(100))
                            .divide(selectedReferencePrice, 0, java.math.RoundingMode.HALF_UP).intValue();
                    return new MedicineDtos.SubstituteOption(candidate.getId(), inventory.getId(),
                            candidate.getName(), candidate.getBrand(), candidate.getGenericName(),
                            candidate.getStrength(), candidate.getDosageForm(), candidate.getPackSize(),
                            candidate.isPrescriptionRequired(), candidate.getScheduleClass(),
                            selectedPharmacy.getId(), selectedPharmacy.getName(), inventory.getPrice(), inventory.getMrp(),
                            savings, percent, inventory.availableQuantity());
                })
                .sorted(Comparator.comparing(MedicineDtos.SubstituteOption::price)
                        .thenComparing(MedicineDtos.SubstituteOption::name))
                .limit(safeLimit)
                .toList();

        String notice = original.isPrescriptionRequired()
                ? "Only identical composition, strength, form and pack size are compared. A pharmacist must review any prescription substitution; nothing is changed automatically."
                : "Only identical composition, strength, form and pack size are compared. Confirm suitability with a pharmacist; nothing is changed automatically.";
        return new MedicineDtos.SubstituteResponse(original.getId(), original.getName(),
                original.getGenericName(), original.getStrength(), original.getDosageForm(),
                original.getPackSize(), selectedPharmacy.getId(), selectedPharmacy.getName(), selectedReferencePrice,
                original.isPrescriptionRequired(), notice, options);
    }

    private MedicineDtos.SubstituteResponse emptySubstitutes(Medicine medicine, Long pharmacyId,
                                                             String pharmacyName, BigDecimal referencePrice) {
        return new MedicineDtos.SubstituteResponse(medicine.getId(), medicine.getName(),
                medicine.getGenericName(), medicine.getStrength(), medicine.getDosageForm(),
                medicine.getPackSize(), pharmacyId, pharmacyName, referencePrice,
                medicine.isPrescriptionRequired(),
                "Only identical composition, strength, form and pack size are compared. Nothing is changed automatically.",
                List.of());
    }

    private boolean usablePharmacy(Pharmacy pharmacy) {
        return pharmacy != null && pharmacy.getStatus() == com.medidrop.common.ApprovalStatus.APPROVED
                && pharmacy.isActive() && pharmacy.isAcceptingOrders();
    }

    private String normalised(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    @Transactional(readOnly = true)
    public List<MedicineDtos.CategoryResponse> categories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(c -> new MedicineDtos.CategoryResponse(c.getId(), c.getName(), c.getSlug(),
                        c.getDescription(), c.isPrescriptionOnly()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MedicineDtos.ManufacturerResponse> manufacturers() {
        return manufacturerRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(m -> new MedicineDtos.ManufacturerResponse(m.getId(), m.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> suggestions(String q, int limit) {
        if (q == null || q.trim().length() < 2) return List.of();
        return medicineRepository.suggest(q.trim(), PageRequest.of(0, Math.min(limit, 10)));
    }

    private List<MedicineDtos.MedicineSummary> enrich(List<Medicine> medicines) {
        if (medicines.isEmpty()) return List.of();
        List<Long> ids = medicines.stream().map(Medicine::getId).toList();
        Map<Long, Object[]> aggregates = new HashMap<>();
        inventoryRepository.aggregateForMedicines(ids)
                .forEach(row -> aggregates.put((Long) row[0], row));
        Map<Long, String> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(MedicineCategory::getId, MedicineCategory::getName));
        Map<Long, String> manufacturers = manufacturerRepository.findAll().stream()
                .collect(Collectors.toMap(Manufacturer::getId, Manufacturer::getName));

        return medicines.stream().map(m -> {
            Object[] agg = aggregates.get(m.getId());
            BigDecimal lowest = agg == null ? null : (BigDecimal) agg[1];
            BigDecimal mrp = agg == null ? null : (BigDecimal) agg[2];
            int stock = agg == null ? 0 : ((Number) agg[3]).intValue();
            long pharmacies = agg == null ? 0 : ((Number) agg[4]).longValue();
            return new MedicineDtos.MedicineSummary(m.getId(), m.getName(), m.getBrand(), m.getGenericName(),
                    m.getStrength(), m.getDosageForm(), m.getPackSize(),
                    m.getManufacturerId() == null ? null : manufacturers.get(m.getManufacturerId()),
                    categories.get(m.getCategoryId()), m.isPrescriptionRequired(), m.getImageUrl(),
                    lowest, mrp, stock, pharmacies);
        }).toList();
    }

    private String nearestName(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return medicineRepository.findAll(PageRequest.of(0, 300)).getContent().stream()
                .map(Medicine::getName)
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(name -> levenshtein(needle, name.toLowerCase(Locale.ROOT))))
                .filter(name -> levenshtein(needle, name.toLowerCase(Locale.ROOT)) <= Math.max(2, needle.length() / 3))
                .orElse(null);
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
