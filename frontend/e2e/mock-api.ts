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

const address = {
  id: 1,
  label: 'Home',
  recipientName: 'Arghya Demo Customer',
  phone: '9800000002',
  line1: 'DN 51, Sector V',
  line2: 'Near City Centre',
  city: 'Kolkata',
  state: 'West Bengal',
  pincode: '700091',
  latitude: 22.5726,
  longitude: 88.3639,
  defaultAddress: true,
};

const cart = {
  cartId: 1,
  pharmacyId: 1,
  pharmacyName: 'Salt Lake Care Pharmacy',
  distanceKm: 1.2,
  lines: [{
    id: 1,
    inventoryId: 1,
    medicineId: 1,
    medicineName: medicine.name,
    brand: medicine.brand,
    strength: medicine.strength,
    packSize: medicine.packSize,
    prescriptionRequired: false,
    unitPrice: 32.5,
    quantity: 1,
    availableQuantity: 120,
    lineTotal: 32.5,
    stockSufficient: true,
  }],
  itemCount: 1,
  subtotal: 32.5,
  deliveryFee: 29,
  taxAmount: 1.63,
  totalAmount: 63.13,
  warnings: [],
  checkoutReady: true,
  prescriptionNotice: null,
};

const orderSummary = {
  id: 1,
  orderNumber: 'MD-2601-0001',
  status: 'PAYMENT_CONFIRMED',
  pharmacyName: 'Salt Lake Care Pharmacy',
  totalAmount: 63.13,
  itemCount: 1,
  placedAt: '2026-11-20T10:15:00Z',
  paymentMethod: 'COD',
  prescriptionRequired: false,
  city: 'Kolkata',
};

const deliveryAssignment = {
  id: 1,
  orderId: 1,
  orderNumber: 'MD-2601-0001',
  status: 'ACCEPTED',
  pharmacyName: 'Salt Lake Care Pharmacy',
  pharmacyAddress: 'DN 51, Sector V, Kolkata',
  customerArea: 'Salt Lake Sector II',
  destinationLatitude: 22.5801,
  destinationLongitude: 88.4172,
  distanceKm: 3.4,
  estimatedMinutes: 18,
  payout: 52,
  assignedAt: '2026-11-20T10:20:00Z',
  prescriptionRequired: false,
  otpVerified: false,
};

type Role = 'CUSTOMER' | 'PHARMACY' | 'DELIVERY' | 'ADMIN';

const users: Record<Role, {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  emailVerified: boolean;
  phoneVerified: boolean;
}> = {
  CUSTOMER: {
    id: 2,
    fullName: 'Arghya Demo Customer',
    email: 'customer@medidrop.local',
    phone: '9800000002',
    emailVerified: true,
    phoneVerified: true,
  },
  PHARMACY: {
    id: 3,
    fullName: 'Salt Lake Demo Pharmacist',
    email: 'pharmacy@medidrop.local',
    phone: '9800000003',
    emailVerified: true,
    phoneVerified: true,
  },
  DELIVERY: {
    id: 5,
    fullName: 'Rakesh Demo Delivery',
    email: 'delivery@medidrop.local',
    phone: '9800000005',
    emailVerified: true,
    phoneVerified: true,
  },
  ADMIN: {
    id: 1,
    fullName: 'MediDrop Administrator',
    email: 'admin@medidrop.local',
    phone: '9800000001',
    emailVerified: true,
    phoneVerified: true,
  },
};

function body(data: unknown) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, message: 'ok', data }),
  };
}

function pageOf<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
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

/**
 * Seeds the same browser storage the real login writes, so ProtectedRoute can
 * render a role console without a live authentication round trip.
 */
export async function signInAs(page: Page, role: Role) {
  const user = { ...users[role], role };
  await page.addInitScript((identity) => {
    localStorage.setItem('medidrop.access', 'browser-gate-access-token');
    localStorage.setItem('medidrop.refresh', 'browser-gate-refresh-token');
    localStorage.setItem('medidrop.user', JSON.stringify(identity));
  }, user);
}

/**
 * Role-console fixture for checkout and the three operator dashboards. Every
 * value is deterministic and mirrors the live DTOs; writes still go to the
 * mocked transport so the page can be inspected without mutating a database.
 */
export async function mockRoleApi(page: Page, role: Role) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, '');

    if (path === '/notifications/unread-count') return route.fulfill(body({ count: 0 }));
    if (path === '/notifications') return route.fulfill(body([]));

    if (role === 'CUSTOMER') {
      if (path === '/cart') return route.fulfill(body(cart));
      if (path === '/customer/addresses') return route.fulfill(body([address]));
      if (path === '/customer/prescriptions') return route.fulfill(body([]));
      if (path === '/orders') return route.fulfill(body(pageOf([orderSummary])));
    }

    if (role === 'PHARMACY') {
      if (path === '/pharmacy/dashboard') {
        return route.fulfill(body({
          totalOrders: 184,
          pendingOrders: 6,
          awaitingPrescription: 2,
          readyForPickup: 3,
          completedOrders: 169,
          cancelledOrders: 5,
          revenueLast30Days: 48260.5,
          lowStockItems: 2,
          activeListings: 18,
          pendingPrescriptionReviews: 2,
          pharmacyName: 'Salt Lake Care Pharmacy',
          status: 'APPROVED',
          acceptingOrders: true,
          rating: 4.8,
        }));
      }
      if (path === '/pharmacy/orders') return route.fulfill(body(pageOf([orderSummary])));
      if (path === '/pharmacy/inventory/low-stock') {
        return route.fulfill(body([{
          inventoryId: 1,
          medicineName: 'ORS Sachet',
          stockQuantity: 6,
          threshold: 10,
        }]));
      }
      if (path === '/pharmacy/documents') {
        return route.fulfill(body([{
          id: 1,
          documentType: 'DRUG_LICENSE',
          fileName: 'drug-licence.pdf',
          status: 'APPROVED',
          uploadedAt: '2026-10-01T09:00:00Z',
        }]));
      }
    }

    if (role === 'DELIVERY') {
      if (path === '/delivery/dashboard') {
        return route.fulfill(body({
          profile: {
            id: 1,
            userId: 5,
            fullName: 'Rakesh Demo Delivery',
            phone: '9800000005',
            email: 'delivery@medidrop.local',
            address: 'Salt Lake, Kolkata',
            vehicleType: 'Motorcycle',
            vehicleNumber: 'WB 06 AB 1234',
            status: 'APPROVED',
            online: true,
            latitude: 22.57,
            longitude: 88.36,
            lastLocationAt: '2026-11-20T10:15:00Z',
            completedDeliveries: 214,
            rating: 4.9,
          },
          online: true,
          pendingAssignments: 1,
          activeDeliveries: 1,
          completedDeliveries: 214,
          earningsToday: 320,
          earningsTotal: 18240,
          active: [deliveryAssignment],
        }));
      }
      if (path === '/delivery/assignments') {
        return route.fulfill(body([{ ...deliveryAssignment, status: 'ASSIGNED' }]));
      }
    }

    if (role === 'ADMIN') {
      if (path === '/admin/dashboard') {
        return route.fulfill(body({
          ordersToday: 42,
          activeOrders: 9,
          revenue: 128450.75,
          refunded: 1240,
          totalCustomers: 3840,
          totalPharmacies: 26,
          pendingPharmacies: 2,
          totalPartners: 48,
          pendingPartners: 1,
          pendingPrescriptions: 7,
          openSupportTickets: 3,
          completedOrders: 9714,
          cancelledOrders: 182,
          orderTrend: [{ date: '2026-11-20', orders: 42, revenue: 128450.75 }],
        }));
      }
      if (path === '/admin/reports/platform') {
        return route.fulfill(body({
          days: 30,
          orders: [{ date: '2026-11-20', orders: 42, revenue: 128450.75 }],
          grossRevenue: 1842950.5,
          averageOrderValue: 412.5,
          cancellationRate: 1.8,
          averageDeliveryMinutes: 34,
          deliveredOrders: 1132,
          topPharmacies: [{ label: 'Salt Lake Care Pharmacy', count: 128 }],
          topMedicines: [{ label: 'Paracetamol 500 mg', count: 214 }],
        }));
      }
    }

    return route.fulfill(body(null));
  });
}
