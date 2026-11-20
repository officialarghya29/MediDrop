package com.medidrop.medicine;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedicineSubstituteTest {

    @Autowired private MedicineService medicineService;
    @Autowired private MedicineRepository medicineRepository;
    @Autowired private MedicineCategoryRepository categoryRepository;
    @Autowired private PharmacyInventoryRepository inventoryRepository;
    @Autowired private PharmacyRepository pharmacyRepository;

    @Test
    void suggestsOnlyIdenticalCompositionLowerPricedInStockAtSelectedPharmacy() {
        MedicineCategory category = new MedicineCategory();
        category.setName("Substitute Test");
        category.setSlug("substitute-test-" + UUID.randomUUID());
        category.setPrescriptionOnly(false);
        category = categoryRepository.save(category);

        Medicine original = medicine(category, "Original Paracetamol", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", false, "OTC");
        Medicine lowerCost = medicine(category, "Value Paracetamol", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", false, "OTC");
        medicine(category, "Different Pack Paracetamol", "Paracetamol", "500 mg",
                "Tablet", "Strip of 10", false, "OTC");
        medicine(category, "Prescription Paracetamol", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", true, "H");
        Medicine blocked = medicine(category, "Blocked Paracetamol", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", false, "OTC");
        blocked.setSalesBlocked(true);
        blocked = medicineRepository.save(blocked);
        Medicine outOfStock = medicine(category, "Out of Stock Paracetamol", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", false, "OTC");

        Pharmacy pharmacy = pharmacy();
        stock(pharmacy.getId(), original.getId(), "50.00", 10);
        stock(pharmacy.getId(), lowerCost.getId(), "30.00", 7);
        stock(pharmacy.getId(), blocked.getId(), "10.00", 10);
        stock(pharmacy.getId(), outOfStock.getId(), "12.00", 0);

        var result = medicineService.substitutes(original.getId(), pharmacy.getId(), 5);

        assertThat(result.referencePrice()).isEqualByComparingTo("50.00");
        assertThat(result.pharmacyId()).isEqualTo(pharmacy.getId());
        assertThat(result.options()).hasSize(1);
        var option = result.options().get(0);
        assertThat(option.name()).isEqualTo("Value Paracetamol");
        assertThat(option.price()).isEqualByComparingTo("30.00");
        assertThat(option.savingsAmount()).isEqualByComparingTo("20.00");
        assertThat(option.savingsPercent()).isEqualTo(40);
        assertThat(option.availableQuantity()).isEqualTo(7);
        assertThat(result.requiresPharmacistReview()).isFalse();
    }

    @Test
    void prescriptionSubstitutesRemainFlaggedForPharmacistReview() {
        MedicineCategory category = new MedicineCategory();
        category.setName("Rx Substitute Test");
        category.setSlug("rx-substitute-test-" + UUID.randomUUID());
        category.setPrescriptionOnly(true);
        category = categoryRepository.save(category);

        Medicine original = medicine(category, "Original Metformin", "Metformin", "500 mg",
                "Tablet", "Strip of 15", true, "H");
        Medicine lowerCost = medicine(category, "Value Metformin", "Metformin", "500 mg",
                "Tablet", "Strip of 15", true, "H");
        Pharmacy pharmacy = pharmacy();
        stock(pharmacy.getId(), original.getId(), "60.00", 10);
        stock(pharmacy.getId(), lowerCost.getId(), "42.00", 10);

        var result = medicineService.substitutes(original.getId(), pharmacy.getId(), 3);

        assertThat(result.requiresPharmacistReview()).isTrue();
        assertThat(result.options()).hasSize(1);
        assertThat(result.options().get(0).prescriptionRequired()).isTrue();
        assertThat(result.notice()).contains("pharmacist");
    }

    @Test
    void skipsAnUnavailableCheapestPharmacyWhenNoPharmacyIsRequested() {
        MedicineCategory category = new MedicineCategory();
        category.setName("Fallback Substitute Test");
        category.setSlug("fallback-substitute-test-" + UUID.randomUUID());
        category.setPrescriptionOnly(false);
        category = categoryRepository.save(category);

        Medicine original = medicine(category, "Fallback Original", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", false, "OTC");
        Medicine lowerCost = medicine(category, "Fallback Value", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", false, "OTC");

        Pharmacy paused = pharmacy(false);
        Pharmacy usable = pharmacy(true);
        stock(paused.getId(), original.getId(), "20.00", 10);
        stock(paused.getId(), lowerCost.getId(), "10.00", 10);
        stock(usable.getId(), original.getId(), "50.00", 10);
        stock(usable.getId(), lowerCost.getId(), "30.00", 10);

        var result = medicineService.substitutes(original.getId(), null, 3);

        assertThat(result.pharmacyId()).isEqualTo(usable.getId());
        assertThat(result.referencePrice()).isEqualByComparingTo("50.00");
        assertThat(result.options()).extracting(MedicineDtos.SubstituteOption::name)
                .containsExactly("Fallback Value");
    }

    private Medicine medicine(MedicineCategory category, String name, String generic, String strength,
                              String form, String pack, boolean rx, String schedule) {
        Medicine medicine = new Medicine();
        medicine.setName(name);
        medicine.setGenericName(generic);
        medicine.setStrength(strength);
        medicine.setDosageForm(form);
        medicine.setPackSize(pack);
        medicine.setCategoryId(category.getId());
        medicine.setPrescriptionRequired(rx);
        medicine.setScheduleClass(schedule);
        medicine.setActive(true);
        return medicineRepository.save(medicine);
    }

    private Pharmacy pharmacy() {
        return pharmacy(true);
    }

    private Pharmacy pharmacy(boolean acceptingOrders) {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setOwnerUserId(740_000L + (System.nanoTime() % 10_000));
        pharmacy.setName("Substitute Test Pharmacy");
        pharmacy.setLicenseNumber("WB-SUB-" + UUID.randomUUID().toString().substring(0, 8));
        pharmacy.setRegisteredAddress("1 Test Road");
        pharmacy.setCity("Kolkata");
        pharmacy.setState("West Bengal");
        pharmacy.setPincode("700001");
        pharmacy.setLatitude(22.5726);
        pharmacy.setLongitude(88.3639);
        pharmacy.setPhone("9800000000");
        pharmacy.setEmail("substitute-" + UUID.randomUUID() + "@example.test");
        pharmacy.setStatus(ApprovalStatus.APPROVED);
        pharmacy.setActive(true);
        pharmacy.setAcceptingOrders(acceptingOrders);
        return pharmacyRepository.save(pharmacy);
    }

    private void stock(Long pharmacyId, Long medicineId, String price, int quantity) {
        PharmacyInventory inventory = new PharmacyInventory();
        inventory.setPharmacyId(pharmacyId);
        inventory.setMedicineId(medicineId);
        inventory.setPrice(new BigDecimal(price));
        inventory.setMrp(new BigDecimal(price).add(new BigDecimal("5.00")));
        inventory.setStockQuantity(quantity);
        inventory.setActive(true);
        inventoryRepository.save(inventory);
    }
}
