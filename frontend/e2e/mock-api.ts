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

const orderDetail = {
  id: 1,
  orderNumber: 'MD-2601-0001',
  status: 'OUT_FOR_DELIVERY',
  pharmacyId: 1,
  pharmacyName: 'Salt Lake Care Pharmacy',
  pharmacyPhone: '9800000003',
  customerName: 'Arghya Demo Customer',
  customerPhone: '9800000002',
  addressLine: 'DN 51, Sector V',
  city: 'Kolkata',
  state: 'West Bengal',
  pincode: '700091',
  latitude: 22.5726,
  longitude: 88.3639,
  pharmacyLatitude: 22.5801,
  pharmacyLongitude: 88.4172,
  subtotal: 32.5,
  discount: 0,
  deliveryFee: 29,
  taxAmount: 1.63,
  totalAmount: 63.13,
  paymentMethod: 'COD',
  prescriptionRequired: false,
  placedAt: '2026-11-20T10:15:00Z',
  etaMinutes: 18,
  items: [{
    id: 1,
    medicineId: 1,
    medicineName: medicine.name,
    unitPrice: 32.5,
    quantity: 1,
    lineTotal: 32.5,
    prescriptionRequired: false,
  }],
  history: [
    { toStatus: 'PAYMENT_CONFIRMED', actorRole: 'CUSTOMER', at: '2026-11-20T10:15:00Z' },
    { toStatus: 'PHARMACY_ACCEPTED', actorRole: 'PHARMACY', at: '2026-11-20T10:17:00Z' },
    { toStatus: 'OUT_FOR_DELIVERY', actorRole: 'DELIVERY', at: '2026-11-20T10:31:00Z' },
  ],
  deliveryPartner: {
    id: 1,
    name: 'Rakesh Demo Delivery',
    phone: '9800000005',
    latitude: 22.576,
    longitude: 88.401,
    lastLocationAt: '2026-11-20T10:33:00Z',
    vehicleType: 'Motorcycle',
  },
  trackingKey: 'demo-tracking-key',
  deliveryOtp: '482913',
  deliveryOtpVerified: false,
};

const prescriptions = [
  {
    id: 1,
    fileName: 'prescription-november.pdf',
    contentType: 'application/pdf',
    sizeBytes: 245000,
    status: 'APPROVED',
    orderId: 1,
    notes: 'For the cardiology follow-up.',
    uploadedAt: '2026-11-18T09:30:00Z',
    reviewedAt: '2026-11-18T10:05:00Z',
    fileUrl: '/api/v1/customer/prescriptions/1/file',
    demo: true,
  },
  {
    id: 2,
    fileName: 'prescription-old.jpg',
    contentType: 'image/jpeg',
    sizeBytes: 512000,
    status: 'REJECTED',
    rejectionReason: 'The prescriber signature and date are not visible.',
    uploadedAt: '2026-11-12T16:20:00Z',
    reviewedAt: '2026-11-12T17:00:00Z',
    fileUrl: '/api/v1/customer/prescriptions/2/file',
    demo: true,
  },
];

const profile = {
  id: 2,
  fullName: 'Arghya Demo Customer',
  email: 'customer@medidrop.local',
  phone: '9800000002',
  role: 'CUSTOMER',
  emailVerified: true,
  phoneVerified: true,
  createdAt: '2026-01-12T08:00:00Z',
};

const supportTickets = [{
  id: 1,
  customerId: 2,
  customerName: 'Arghya Demo Customer',
  orderId: 1,
  subject: 'Delivery instructions',
  message: 'Please call on arrival instead of ringing the doorbell.',
  status: 'OPEN',
  createdAt: '2026-11-20T10:18:00Z',
}];

const notifications = [{
  id: 1,
  type: 'ORDER',
  title: 'Your order is out for delivery',
  body: 'Rakesh is on the way with order MD-2601-0001.',
  link: '/orders/1',
  read: false,
  createdAt: '2026-11-20T10:31:00Z',
}];

const tracking = {
  orderNumber: 'MD-2601-0001',
  status: 'OUT_FOR_DELIVERY',
  trackingKey: 'demo-tracking-key',
  updatedAt: '2026-11-20T10:33:00Z',
  partner: { name: 'Rakesh Demo Delivery', phone: '9800000005', vehicleType: 'Motorcycle' },
  partnerLatitude: 22.576,
  partnerLongitude: 88.401,
  partnerUpdatedAt: '2026-11-20T10:33:00Z',
  etaMinutes: 12,
  destinationLabel: 'Salt Lake Sector II, Kolkata',
  destinationLatitude: 22.5801,
  destinationLongitude: 88.4172,
  pharmacyLatitude: 22.5726,
  pharmacyLongitude: 88.3639,
};

const deliveredAssignment = {
  id: 2,
  orderId: 2,
  orderNumber: 'MD-2601-0002',
  status: 'DELIVERED',
  pharmacyName: 'Salt Lake Care Pharmacy',
  pharmacyAddress: 'DN 51, Sector V, Kolkata',
  customerArea: 'New Town, Kolkata',
  destinationLatitude: 22.5867,
  destinationLongitude: 88.4172,
  distanceKm: 5.1,
  estimatedMinutes: 24,
  payout: 68,
  assignedAt: '2026-11-19T14:10:00Z',
  prescriptionRequired: false,
  otpVerified: true,
};

const inventoryRow = {
  id: 1,
  medicineId: 1,
  medicineName: medicine.name,
  brand: medicine.brand,
  strength: medicine.strength,
  packSize: medicine.packSize,
  prescriptionRequired: false,
  price: 32.5,
  mrp: 40,
  stockQuantity: 6,
  availableQuantity: 6,
  lowStockThreshold: 10,
  active: true,
};

const pendingPrescriptions = [{
  id: 1,
  customerId: 2,
  customerName: 'Arghya Demo Customer',
  fileName: 'prescription-november.pdf',
  uploadedAt: '2026-11-20T09:30:00Z',
  status: 'UPLOADED',
  notes: 'Please review before dispensing.',
  orderId: 1,
  orderNumber: 'MD-2601-0001',
}];

const pharmacyAnalytics = {
  days: 30,
  series: [{ date: '2026-11-20', orders: 18, revenue: 6240.5 }],
  revenue: 48260.5,
  averageOrderValue: 312.4,
  deliveredOrders: 154,
  cancelledOrders: 5,
  lowStockItems: 2,
  topProducts: [{ label: 'Paracetamol 500 mg', count: 62 }],
  forecast: [{
    medicine: 'ORS Sachet',
    unitsLast30Days: 44,
    suggestedNext7Days: 12,
    method: 'moving-average',
  }],
};

const adminPharmacy = {
  id: 4,
  name: 'Behala Community Pharmacy',
  city: 'Kolkata',
  licenseNumber: 'WB-DL-2026-0042',
  phone: '9800000004',
  status: 'PENDING',
  registeredAt: '2026-11-18T08:00:00Z',
  rating: 0,
  documents: [{
    documentType: 'DRUG_LICENSE',
    fileName: 'drug-licence.pdf',
    status: 'UPLOADED',
  }],
};

const adminPartner = {
  id: 6,
  fullName: 'Suman Demo Rider',
  phone: '9800000006',
  vehicleType: 'Motorcycle',
  status: 'PENDING',
  online: false,
  completedDeliveries: 0,
  rating: 0,
  appliedAt: '2026-11-18T10:00:00Z',
  documents: [{
    documentType: 'DRIVING_LICENSE',
    fileName: 'driving-licence.pdf',
    status: 'UPLOADED',
  }],
};

const adminOrder = {
  id: 1,
  orderNumber: 'MD-2601-0001',
  customerName: 'Arghya Demo Customer',
  pharmacyName: 'Salt Lake Care Pharmacy',
  status: 'READY_FOR_PICKUP',
  totalAmount: 63.13,
  placedAt: '2026-11-20T10:15:00Z',
  customerRole: 'CUSTOMER',
};

const adminUser = {
  id: 2,
  fullName: 'Arghya Demo Customer',
  email: 'customer@medidrop.local',
  phone: '9800000002',
  role: 'CUSTOMER',
  enabled: true,
  locked: false,
  emailVerified: true,
  joinedAt: '2026-01-12T08:00:00Z',
};

const systemSettings = [{
  key: 'delivery.base-fee',
  value: '25.00',
  description: 'Base delivery fee in INR.',
  publicSetting: true,
}];

const coupons = [{
  id: 1,
  code: 'WELCOME10',
  type: 'PERCENT',
  value: 10,
  minOrderValue: 199,
  maxDiscount: 50,
  active: true,
  usageLimit: 500,
  usedCount: 42,
  validTo: '2026-12-31',
}];

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

    if (path === '/notifications/unread-count') return route.fulfill(body({ count: 1 }));
    if (path === '/notifications') return route.fulfill(body(notifications));
    if (path === '/tracking/demo-tracking-key') return route.fulfill(body(tracking));
    if (path === '/medicines/categories') return route.fulfill(body(categories));

    if (role === 'CUSTOMER') {
      if (path === '/cart') return route.fulfill(body(cart));
      if (path === '/customer/addresses') return route.fulfill(body([address]));
      if (path === '/customer/me') return route.fulfill(body(profile));
      if (path === '/customer/prescriptions') return route.fulfill(body(prescriptions));
      if (path === '/support') return route.fulfill(body(supportTickets));
      if (path === '/orders/counts') {
        return route.fulfill(body({ total: 1, active: 1, delivered: 0, cancelled: 0 }));
      }
      if (path === '/orders/1') return route.fulfill(body(orderDetail));
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
      if (path === '/pharmacy/inventory') return route.fulfill(body(pageOf([inventoryRow])));
      if (path === '/pharmacy/prescriptions') return route.fulfill(body(pendingPrescriptions));
      if (path === '/pharmacy/analytics') return route.fulfill(body(pharmacyAnalytics));
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
        const status = url.searchParams.get('status');
        return route.fulfill(body(status === 'ASSIGNED'
          ? [{ ...deliveryAssignment, status: 'ASSIGNED' }]
          : [{ ...deliveryAssignment, status: 'ASSIGNED' }, deliveredAssignment]));
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
      if (path === '/admin/pharmacies') return route.fulfill(body(pageOf([adminPharmacy])));
      if (path === '/admin/delivery-partners') return route.fulfill(body(pageOf([adminPartner])));
      if (path === '/admin/orders') return route.fulfill(body(pageOf([adminOrder])));
      if (path === '/admin/users') return route.fulfill(body(pageOf([adminUser])));
      if (path === '/admin/settings') return route.fulfill(body(systemSettings));
      if (path === '/admin/coupons') return route.fulfill(body(coupons));
      if (path === '/admin/categories') return route.fulfill(body(categories));
    }

    return route.fulfill(body(null));
  });
}
