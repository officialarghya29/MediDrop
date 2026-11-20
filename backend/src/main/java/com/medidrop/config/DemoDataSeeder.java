package com.medidrop.config;

import com.medidrop.audit.SystemSetting;
import com.medidrop.audit.SystemSettingRepository;
import com.medidrop.common.ApprovalStatus;
import com.medidrop.delivery.DeliveryPartner;
import com.medidrop.delivery.DeliveryPartnerRepository;
import com.medidrop.inventory.InventoryTransaction;
import com.medidrop.inventory.InventoryTransactionRepository;
import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.medicine.Manufacturer;
import com.medidrop.medicine.ManufacturerRepository;
import com.medidrop.medicine.Medicine;
import com.medidrop.medicine.MedicineCategory;
import com.medidrop.medicine.MedicineCategoryRepository;
import com.medidrop.medicine.MedicineRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyOperatingHour;
import com.medidrop.pharmacy.PharmacyOperatingHourRepository;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.promo.Coupon;
import com.medidrop.promo.CouponRepository;
import com.medidrop.security.Role;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Idempotent demonstration data. Every record created here is flagged as test/demo data and is
 * never mixed with real inventory. All demo accounts use documented passwords that must be
 * changed or removed before production use.
 */
@Component
@ConditionalOnProperty(name = "medidrop.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo@12345";

    private static final Map<String, BigDecimal> DEMO_PRICE_OVERRIDES = Map.of(
            "Paracetamol 500 mg Value Pack", new BigDecimal("249.00"),
            "Cetirizine 10 mg Value Pack", new BigDecimal("16.00"),
            "Metformin 500 mg Generic", new BigDecimal("299.00"));

    private final MedidropProperties properties;
    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyOperatingHourRepository hoursRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final MedicineCategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final MedicineRepository medicineRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CouponRepository couponRepository;
    private final SystemSettingRepository settingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Demo seed skipped: database already contains accounts");
            return;
        }
        log.info("Seeding MediDrop demonstration data (TEST DATA ONLY)");
        String encoded = passwordEncoder.encode(DEMO_PASSWORD);

        User admin = user("MediDrop Administrator", properties.getSeed().getAdminEmail(),
                "9800000001", properties.getSeed().getAdminPassword() == null
                        || properties.getSeed().getAdminPassword().isBlank()
                        ? "Admin@12345" : properties.getSeed().getAdminPassword(), Role.ADMIN);
        User customer = user("Arghya Demo Customer", "customer@medidrop.local", "9800000002", DEMO_PASSWORD,
                Role.CUSTOMER);
        customer.setEmailVerified(true);
        customer.setPhoneVerified(true);
        userRepository.save(customer);

        User pharmacyOwner1 = user("Salt Lake Demo Pharmacist", "pharmacy@medidrop.local", "9800000003",
                DEMO_PASSWORD, Role.PHARMACY);
        User pharmacyOwner2 = user("Bhowanipore Demo Pharmacist", "pharmacy2@medidrop.local", "9800000004",
                DEMO_PASSWORD, Role.PHARMACY);
        User deliveryUser1 = user("Rakesh Demo Delivery", "delivery@medidrop.local", "9800000005",
                DEMO_PASSWORD, Role.DELIVERY);
        User deliveryUser2 = user("Sunil Demo Delivery", "delivery2@medidrop.local", "9800000006",
                DEMO_PASSWORD, Role.DELIVERY);

        Pharmacy p1 = pharmacy("MediDrop Demo Pharmacy — Salt Lake", pharmacyOwner1, "WB-DEMO-DL-0001",
                22.5726, 88.3639, "Salt Lake Sector V, Kolkata", "700091");
        Pharmacy p2 = pharmacy("Demo Wellness Chemist — Bhowanipore", pharmacyOwner2, "WB-DEMO-DL-0002",
                22.5350, 88.3450, "Bhowanipore, Kolkata", "700020");

        partner("Rakesh Demo Delivery", deliveryUser1, 22.5700, 88.3600,
                ApprovalStatus.APPROVED, true);
        partner("Sunil Demo Delivery", deliveryUser2, 22.5400, 88.3500,
                ApprovalStatus.APPROVED, true);

        Map<String, MedicineCategory> categories = seedCategories();
        Map<String, Manufacturer> manufacturers = seedManufacturers();
        List<Medicine> medicines = seedMedicines(categories, manufacturers);

        seedInventory(p1, medicines, 0);
        seedInventory(p2, medicines, 6);
        seedCoupons();
        seedSettings();

        log.info("Demo data ready. Admin={} Customer=customer@medidrop.local Pharmacy=pharmacy@medidrop.local " +
                "Delivery=delivery@medidrop.local Password={}", admin.getEmail(), DEMO_PASSWORD);
        log.info("Seeded {} test medicines across {} demo pharmacies", medicines.size(), 2);
    }

    private User user(String name, String email, String phone, String password, Role role) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        return userRepository.save(user);
    }

    private Pharmacy pharmacy(String name, User owner, String licence, double lat, double lng,
                              String address, String pincode) {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setOwnerUserId(owner.getId());
        pharmacy.setName(name);
        pharmacy.setLicenseNumber(licence);
        pharmacy.setGstin("19DEMO0000A1Z5");
        pharmacy.setRegisteredAddress(address);
        pharmacy.setCity("Kolkata");
        pharmacy.setState("West Bengal");
        pharmacy.setPincode(pincode);
        pharmacy.setLatitude(lat);
        pharmacy.setLongitude(lng);
        pharmacy.setPhone(owner.getPhone());
        pharmacy.setEmail(owner.getEmail());
        pharmacy.setStatus(ApprovalStatus.APPROVED);
        pharmacy.setActive(true);
        pharmacy.setAcceptingOrders(true);
        pharmacy.setDeliveryRadiusKm(8.0);
        pharmacy.setMinOrderValue(new BigDecimal("99.00"));
        pharmacy.setRating(new BigDecimal("4.60"));
        pharmacy.setPharmacistName(owner.getFullName());
        pharmacy.setPharmacistRegistrationNumber("WB-DEMO-PH-" + licence.substring(Math.max(0, licence.length() - 4)));
        pharmacy.setPharmacistCouncil("West Bengal State Pharmacy Council (demo)");
        pharmacy.setPharmacistVerified(true);
        pharmacy.setPharmacistVerifiedAt(java.time.Instant.now());
        pharmacyRepository.save(pharmacy);
        for (int day = 1; day <= 7; day++) {
            PharmacyOperatingHour hours = new PharmacyOperatingHour();
            hours.setPharmacyId(pharmacy.getId());
            hours.setDayOfWeek(day);
            hours.setClosed(day == 7);
            hoursRepository.save(hours);
        }
        return pharmacy;
    }

    private void partner(String name, User user, double lat, double lng, ApprovalStatus status, boolean online) {
        DeliveryPartner partner = new DeliveryPartner();
        partner.setUserId(user.getId());
        partner.setFullName(name);
        partner.setPhone(user.getPhone());
        partner.setEmail(user.getEmail());
        partner.setAddress("Kolkata, West Bengal");
        partner.setIdProofType("DEMO_ID");
        partner.setIdProofNumber("DEMO-0000");
        partner.setVehicleType("Motorcycle");
        partner.setVehicleNumber("WB-DEMO-0000");
        partner.setBankAccountName(name);
        partner.setBankAccountLast4("0000");
        partner.setPayoutIfsc("DEMO0000001");
        partner.setStatus(status);
        partner.setOnline(online);
        partner.setCurrentLatitude(lat);
        partner.setCurrentLongitude(lng);
        partner.setLastLocationAt(java.time.Instant.now());
        partnerRepository.save(partner);
    }

    private Map<String, MedicineCategory> seedCategories() {
        Map<String, MedicineCategory> map = new java.util.LinkedHashMap<>();
        map.put("RX", category("Prescription Medicines", "prescription-medicines",
                "Medicines dispensed only against a valid prescription", true, 1));
        map.put("OTC", category("Over the Counter", "over-the-counter",
                "Medicines available without a prescription", false, 2));
        map.put("PERSONAL", category("Personal Care", "personal-care",
                "Everyday personal care products", false, 3));
        map.put("ESSENTIALS", category("Healthcare Essentials", "healthcare-essentials",
                "Home healthcare essentials", false, 4));
        map.put("FIRST_AID", category("First Aid", "first-aid",
                "First aid supplies for home and travel", false, 5));
        map.put("DEVICES", category("Medical Devices", "medical-devices",
                "Devices such as thermometers and monitors", false, 6));
        map.put("WELLNESS", category("Wellness", "wellness",
                "Supplements and wellness products", false, 7));
        return map;
    }

    private MedicineCategory category(String name, String slug, String description, boolean rx, int order) {
        MedicineCategory category = new MedicineCategory();
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        category.setPrescriptionOnly(rx);
        category.setDisplayOrder(order);
        return categoryRepository.save(category);
    }

    private Map<String, Manufacturer> seedManufacturers() {
        Map<String, Manufacturer> map = new java.util.HashMap<>();
        for (String name : List.of("Demo Pharma Ltd", "Demo Health Sciences", "Demo Devices India")) {
            Manufacturer manufacturer = new Manufacturer();
            manufacturer.setName(name);
            map.put(name, manufacturerRepository.save(manufacturer));
        }
        return map;
    }

    private List<Medicine> seedMedicines(Map<String, MedicineCategory> categories,
                                         Map<String, Manufacturer> manufacturers) {
        List<Medicine> list = new ArrayList<>();
        list.add(medicine("Paracetamol 500 mg", "Demo Pharma Ltd", "Paracetamol", "500 mg", "Tablet",
                "Strip of 15", categories.get("OTC"), manufacturers.get("Demo Pharma Ltd"), false,
                new BigDecimal("30.00"), "Demonstration catalogue record for fever and pain relief. " +
                        "Always follow the advice of a qualified healthcare professional.", null));
        list.add(medicine("Dolo 650 mg", "Demo Pharma Ltd", "Paracetamol", "650 mg", "Tablet",
                "Strip of 15", categories.get("OTC"), manufacturers.get("Demo Pharma Ltd"), false,
                new BigDecimal("35.00"), "Demonstration catalogue record. Brand shown for demo purposes.",
                null));
        list.add(medicine("Cetirizine 10 mg", "Demo Health Sciences", "Cetirizine", "10 mg", "Tablet",
                "Strip of 10", categories.get("OTC"), manufacturers.get("Demo Health Sciences"), false,
                new BigDecimal("22.00"), "Demonstration catalogue record for allergy relief.", null));
        list.add(medicine("Azithromycin 500 mg", "Demo Health Sciences", "Azithromycin", "500 mg", "Tablet",
                "Strip of 3", categories.get("RX"), manufacturers.get("Demo Health Sciences"), true,
                new BigDecimal("95.00"), "Demonstration prescription-only catalogue record. " +
                        "Dispensed only after verification of a valid prescription.", "Prescription required."));
        list.add(medicine("Amoxicillin 500 mg", "Demo Health Sciences", "Amoxicillin", "500 mg", "Capsule",
                "Strip of 10", categories.get("RX"), manufacturers.get("Demo Health Sciences"), true,
                new BigDecimal("88.00"), "Demonstration prescription-only catalogue record.",
                "Prescription required. Do not use without medical advice."));
        list.add(medicine("Metformin 500 mg", "Demo Health Sciences", "Metformin", "500 mg", "Tablet",
                "Strip of 15", categories.get("RX"), manufacturers.get("Demo Health Sciences"), true,
                new BigDecimal("48.00"), "Demonstration prescription-only catalogue record.",
                "Prescription required."));
        list.add(medicine("Pantoprazole 40 mg", "Demo Pharma Ltd", "Pantoprazole", "40 mg", "Tablet",
                "Strip of 10", categories.get("RX"), manufacturers.get("Demo Pharma Ltd"), true,
                new BigDecimal("72.00"), "Demonstration prescription-only catalogue record.",
                "Prescription required."));
        list.add(medicine("Ibuprofen 400 mg", "Demo Pharma Ltd", "Ibuprofen", "400 mg", "Tablet",
                "Strip of 10", categories.get("OTC"), manufacturers.get("Demo Pharma Ltd"), false,
                new BigDecimal("28.00"), "Demonstration catalogue record.", null));
        list.add(medicine("ORS Sachet", "Demo Health Sciences", "Oral Rehydration Salts", "21.8 g", "Powder",
                "Pack of 5", categories.get("ESSENTIALS"), manufacturers.get("Demo Health Sciences"), false,
                new BigDecimal("45.00"), "Demonstration catalogue record for oral rehydration.", null));
        list.add(medicine("Vitamin D3 60K", "Demo Health Sciences", "Cholecalciferol", "60000 IU", "Sachet",
                "Pack of 4", categories.get("WELLNESS"), manufacturers.get("Demo Health Sciences"), false,
                new BigDecimal("120.00"), "Demonstration wellness catalogue record.", null));
        list.add(medicine("Digital Thermometer", "Demo Devices India", null, null, "Device",
                "Single unit", categories.get("DEVICES"), manufacturers.get("Demo Devices India"), false,
                new BigDecimal("199.00"), "Demonstration catalogue record for a digital thermometer.", null));
        list.add(medicine("Blood Pressure Monitor", "Demo Devices India", null, null, "Device",
                "Single unit", categories.get("DEVICES"), manufacturers.get("Demo Devices India"), false,
                new BigDecimal("1499.00"), "Demonstration catalogue record for an upper-arm BP monitor.", null));
        list.add(medicine("Antiseptic Liquid 100 ml", "Demo Health Sciences", "Chlorhexidine",
                "100 ml", "Liquid", "Bottle of 100 ml", categories.get("FIRST_AID"),
                manufacturers.get("Demo Health Sciences"), false, new BigDecimal("85.00"),
                "Demonstration first aid catalogue record.", null));
        list.add(medicine("Sterile Gauze Pad", "Demo Devices India", null, null, "Dressing",
                "Pack of 10", categories.get("FIRST_AID"), manufacturers.get("Demo Devices India"), false,
                new BigDecimal("55.00"), "Demonstration first aid catalogue record.", null));
        list.add(medicine("Hand Sanitiser 500 ml", "Demo Health Sciences", "Ethanol 70%",
                "70% v/v", "Gel", "Bottle of 500 ml", categories.get("PERSONAL"),
                manufacturers.get("Demo Health Sciences"), false, new BigDecimal("149.00"),
                "Demonstration personal care catalogue record.", null));
        list.add(medicine("Paracetamol 500 mg Value Pack", "Demo Generics", "Paracetamol", "500 mg",
                "Tablet", "Strip of 15", categories.get("OTC"), manufacturers.get("Demo Pharma Ltd"),
                false, new BigDecimal("249.00"), "Demonstration lower-cost equivalent. Same composition, strength, form and pack size.", null));
        list.add(medicine("Cetirizine 10 mg Value Pack", "Demo Generics", "Cetirizine", "10 mg",
                "Tablet", "Strip of 10", categories.get("OTC"), manufacturers.get("Demo Health Sciences"),
                false, new BigDecimal("16.00"), "Demonstration lower-cost equivalent. Same composition, strength, form and pack size.", null));
        list.add(medicine("Metformin 500 mg Generic", "Demo Generics", "Metformin", "500 mg",
                "Tablet", "Strip of 15", categories.get("RX"), manufacturers.get("Demo Health Sciences"),
                true, new BigDecimal("299.00"), "Demonstration lower-cost prescription equivalent. Pharmacist review remains mandatory.",
                "Prescription required. A pharmacist must review any substitution."));
        return list;
    }

    private Medicine medicine(String name, String brand, String generic, String strength, String form,
                              String pack, MedicineCategory category, Manufacturer manufacturer,
                              boolean rx, BigDecimal price, String description, String warnings) {
        Medicine medicine = new Medicine();
        medicine.setName(name);
        medicine.setBrand(brand);
        medicine.setGenericName(generic);
        medicine.setStrength(strength);
        medicine.setDosageForm(form);
        medicine.setPackSize(pack);
        medicine.setCategoryId(category.getId());
        medicine.setManufacturerId(manufacturer == null ? null : manufacturer.getId());
        medicine.setPrescriptionRequired(rx);
        medicine.setScheduleClass(rx ? "H" : "OTC");
        medicine.setDescription(description);
        medicine.setWarnings(warnings);
        medicine.setDemo(true);
        medicine.setActive(true);
        medicine.setImageUrl(null);
        return medicineRepository.save(medicine);
    }

    private void seedInventory(Pharmacy pharmacy, List<Medicine> medicines, int priceOffset) {
        for (int i = 0; i < medicines.size(); i++) {
            Medicine medicine = medicines.get(i);
            BigDecimal base = DEMO_PRICE_OVERRIDES.get(medicine.getName());
            if (base != null) {
                base = base.add(BigDecimal.valueOf(priceOffset));
            } else {
                base = new BigDecimal(String.valueOf(30 + i * 17 + priceOffset));
                String[] parts = medicine.getName().replaceAll("[^0-9]", " ").trim().split("\\s+");
                if (parts.length > 0 && !parts[0].isBlank()) {
                    try {
                        base = new BigDecimal(parts[0]).multiply(new BigDecimal("0.65"))
                                .add(new BigDecimal("18")).setScale(2, java.math.RoundingMode.HALF_UP);
                    } catch (NumberFormatException ignored) {
                        // keep the fallback price
                    }
                }
            }
            PharmacyInventory inventory = new PharmacyInventory();
            inventory.setPharmacyId(pharmacy.getId());
            inventory.setMedicineId(medicine.getId());
            inventory.setPrice(base);
            inventory.setMrp(base.multiply(new BigDecimal("1.20")).setScale(2, java.math.RoundingMode.HALF_UP));
            inventory.setStockQuantity(40 + (i % 4) * 15 - priceOffset);
            inventory.setLowStockThreshold(10);
            inventory.setActive(true);
            inventory = inventoryRepository.save(inventory);

            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryId(inventory.getId());
            tx.setType(InventoryTransaction.Type.RESTOCK);
            tx.setQuantityDelta(inventory.getStockQuantity());
            tx.setBalanceAfter(inventory.getStockQuantity());
            tx.setReason("Demo seed initial stock");
            transactionRepository.save(tx);
        }
    }

    private void seedCoupons() {
        Coupon flat = new Coupon();
        flat.setCode("WELCOME50");
        flat.setType(Coupon.Type.FLAT);
        flat.setValue(new BigDecimal("50.00"));
        flat.setMinOrderValue(new BigDecimal("299.00"));
        flat.setDescription("Demo coupon: ₹50 off orders above ₹299");
        flat.setActive(true);
        couponRepository.save(flat);

        Coupon percent = new Coupon();
        percent.setCode("MEDI10");
        percent.setType(Coupon.Type.PERCENT);
        percent.setValue(new BigDecimal("10.00"));
        percent.setMinOrderValue(new BigDecimal("199.00"));
        percent.setMaxDiscount(new BigDecimal("100.00"));
        percent.setDescription("Demo coupon: 10% off up to ₹100");
        percent.setActive(true);
        couponRepository.save(percent);
    }

    private void seedSettings() {
        setting("platform_name", "MediDrop", "Public platform name", true);
        setting("support_email", "support@medidrop.local", "Customer support email", true);
        setting("support_phone", "+91 33 0000 0000", "Customer support phone (demo)", true);
        setting("free_delivery_above", "499", "Free delivery threshold in INR", true);
        setting("min_order_value", "99", "Minimum order value in INR", true);
        setting("prescription_notice",
                "Prescription medicines are dispensed only after review by a licensed pharmacist.",
                "Shown at checkout for prescription orders", true);
    }

    private void setting(String key, String value, String description, boolean publicSetting) {
        SystemSetting setting = new SystemSetting();
        setting.setKey(key);
        setting.setValue(value);
        setting.setDescription(description);
        setting.setPublicSetting(publicSetting);
        settingRepository.save(setting);
    }
}
