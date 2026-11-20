import type { Page } from '@playwright/test';

const medicine = {
  id: 1,
  name: 'Paracetamol 500 mg Tablet',
  brand: 'Dolo',
  genericName: 'Paracetamol',
  strength: '500 mg',
  dosageForm: 'Tablet',
  packSize: '1 strip of 15',
  manufacturerName: 'Micro Labs',
  categoryName: 'Fever & pain',
  prescriptionRequired: false,
  lowestPrice: 32.5,
  mrp: 40,
  totalStock: 120,
  pharmacyCount: 1,
};

const categories = [
  { id: 1, name: 'Fever & pain', slug: 'fever-pain', prescriptionOnly: false },
  { id: 2, name: 'Prescription', slug: 'prescription', prescriptionOnly: true },
  { id: 3, name: 'Wellness', slug: 'wellness', prescriptionOnly: false },
];

const availability = {
  inventoryId: 1,
  pharmacyId: 1,
  pharmacyName: 'Salt Lake Care Pharmacy',
  city: 'Kolkata',
  distanceKm: 1.2,
  price: 32.5,
  mrp: 40,
  availableQuantity: 120,
  acceptingOrders: true,
};

function body(data: unknown) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, message: 'ok', data }),
  };
}

/**
 * Public-journey fixture. It keeps the browser gate independent of PostgreSQL
 * while using the same response shapes as the live API. Real API integration is
 * covered by the separate smoke and 250K benchmark gates.
 */
export async function mockPublicApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, '');

    if (path === '/medicines/categories') return route.fulfill(body(categories));
    if (path === '/pharmacies/nearby') {
      return route.fulfill(body([{
        id: 1,
        name: 'Salt Lake Care Pharmacy',
        address: 'DN 51, Sector V',
        city: 'Kolkata',
        pincode: '700091',
        latitude: 22.5726,
        longitude: 88.3639,
        distanceKm: 1.2,
        deliveryRadiusKm: 12,
        minOrderValue: 99,
        rating: 4.8,
        acceptingOrders: true,
        withinDeliveryArea: true,
      }]));
    }
    if (/^\/medicines\/\d+\/substitutes$/.test(path)) {
      return route.fulfill(body({
        medicineId: 1,
        medicineName: medicine.name,
        genericName: medicine.genericName,
        strength: medicine.strength,
        dosageForm: medicine.dosageForm,
        packSize: medicine.packSize,
        pharmacyId: 1,
        pharmacyName: availability.pharmacyName,
        referencePrice: availability.price,
        requiresPharmacistReview: false,
        notice: 'Only identical composition, strength, form and pack size are compared.',
        options: [],
      }));
    }
    if (/^\/medicines\/\d+$/.test(path)) {
      return route.fulfill(body({
        ...medicine,
        description: 'Synthetic public-journey fixture for browser and accessibility verification.',
        warnings: 'Follow the label and consult a pharmacist if symptoms persist.',
        scheduleClass: 'OTC',
        availability: [availability],
        cheapest: availability,
      }));
    }
    if (path === '/medicines' || path === '/medicines/search') {
      return route.fulfill(body({
        items: [medicine],
        page: 0,
        size: 12,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
        suggestion: null,
        suggestions: [],
      }));
    }
    return route.fulfill(body(null));
  });
}
