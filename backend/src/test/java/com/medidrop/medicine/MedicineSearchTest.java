package com.medidrop.medicine;

import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for catalogue paths that bind a null search term.
 * PostgreSQL infers untyped null parameters as bytea, which broke lower(:q);
 * the repository now coalesces the term so browse and filter-only search stay valid.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedicineSearchTest {

    @Autowired
    private MedicineService medicineService;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineCategoryRepository categoryRepository;

    @Autowired
    private PharmacyInventoryRepository inventoryRepository;

    @Test
    void browseReturnsActiveMedicinesWithoutASearchTerm() {
        MedicineCategory category = new MedicineCategory();
        category.setName("Test Essentials");
        category.setSlug("test-essentials-" + System.nanoTime());
        category.setPrescriptionOnly(false);
        category = categoryRepository.save(category);

        Medicine medicine = new Medicine();
        medicine.setName("Regression Paracetamol");
        medicine.setDosageForm("TABLET");
        medicine.setCategoryId(category.getId());
        medicine.setPrescriptionRequired(false);
        medicineRepository.save(medicine);

        var page = medicineService.browse(0, 12);

        assertThat(page.content()).isNotEmpty();
        assertThat(page.content())
                .extracting(MedicineDtos.MedicineSummary::name)
                .contains("Regression Paracetamol");
    }

    @Test
    void searchAcceptsANullTermAndStillAppliesFilters() {
        var result = medicineService.search(null, null, null, null, null, "relevance", 0, 12);

        assertThat(result).isNotNull();
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void pharmacyFilterOnlyReturnsMedicinesInThatPharmacyStock() {
        MedicineCategory category = new MedicineCategory();
        category.setName("Pharmacy Filter Test");
        category.setSlug("pharmacy-filter-" + System.nanoTime());
        category.setPrescriptionOnly(false);
        category = categoryRepository.save(category);

        Medicine medicine = new Medicine();
        medicine.setName("Filtered Vitamin C");
        medicine.setDosageForm("TABLET");
        medicine.setCategoryId(category.getId());
        medicine.setPrescriptionRequired(false);
        medicine = medicineRepository.save(medicine);

        Long pharmacyId = 777_000L + (System.nanoTime() % 1000);
        PharmacyInventory inventory = new PharmacyInventory();
        inventory.setPharmacyId(pharmacyId);
        inventory.setMedicineId(medicine.getId());
        inventory.setPrice(new java.math.BigDecimal("49.00"));
        inventory.setMrp(new java.math.BigDecimal("59.00"));
        inventory.setStockQuantity(4);
        inventory.setActive(true);
        inventoryRepository.save(inventory);

        var matching = medicineService.search(null, null, null, pharmacyId, null, "relevance", 0, 50);
        var missing = medicineService.search(null, null, null, pharmacyId + 1, null, "relevance", 0, 50);

        assertThat(matching.items()).extracting(MedicineDtos.MedicineSummary::name)
                .contains("Filtered Vitamin C");
        assertThat(missing.items()).extracting(MedicineDtos.MedicineSummary::name)
                .doesNotContain("Filtered Vitamin C");
    }
}
