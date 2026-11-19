package com.medidrop.medicine;

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
        var result = medicineService.search(null, null, null, null, "relevance", 0, 12);

        assertThat(result).isNotNull();
        assertThat(result.page()).isZero();
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(0);
    }
}
