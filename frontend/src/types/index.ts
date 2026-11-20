export type Role = 'CUSTOMER' | 'PHARMACY' | 'DELIVERY' | 'ADMIN';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AuthUser {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  role: Role;
  emailVerified: boolean;
  phoneVerified: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: AuthUser;
  devOtp?: string | null;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
  prescriptionOnly: boolean;
}

export interface MedicineSummary {
  id: number;
  name: string;
  brand?: string;
  genericName?: string;
  strength?: string;
  dosageForm?: string;
  packSize?: string;
  manufacturerName?: string;
  categoryName?: string;
  prescriptionRequired: boolean;
  imageUrl?: string;
  lowestPrice?: number;
  mrp?: number;
  totalStock: number;
  pharmacyCount: number;
}

export interface Availability {
  inventoryId: number;
  pharmacyId: number;
  pharmacyName: string;
  city: string;
  distanceKm?: number;
  price: number;
  mrp: number;
  availableQuantity: number;
  acceptingOrders: boolean;
}

export interface MedicineDetail extends MedicineSummary {
  description?: string;
  warnings?: string;
  scheduleClass?: string;
  availability: Availability[];
  cheapest?: Availability;
}

export interface SubstituteOption {
  medicineId: number;
  inventoryId: number;
  name: string;
  brand?: string;
  genericName?: string;
  strength?: string;
  dosageForm?: string;
  packSize?: string;
  prescriptionRequired: boolean;
  scheduleClass?: string;
  pharmacyId: number;
  pharmacyName: string;
  price: number;
  mrp: number;
  savingsAmount: number;
  savingsPercent: number;
  availableQuantity: number;
}

export interface SubstituteResponse {
  medicineId: number;
  medicineName: string;
  genericName?: string;
  strength?: string;
  dosageForm?: string;
  packSize?: string;
  pharmacyId?: number;
  pharmacyName?: string;
  referencePrice?: number;
  requiresPharmacistReview: boolean;
  notice: string;
  options: SubstituteOption[];
}

export interface SearchResponse {
  items: MedicineSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  suggestion?: string | null;
  suggestions: string[];
}

export interface NearbyPharmacy {
  id: number;
  name: string;
  address: string;
  city: string;
  pincode: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  deliveryRadiusKm: number;
  minOrderValue: number;
  rating: number;
  acceptingOrders: boolean;
  withinDeliveryArea: boolean;
}

export interface CartLine {
  id: number;
  inventoryId: number;
  medicineId: number;
  medicineName: string;
  brand?: string;
  strength?: string;
  packSize?: string;
  prescriptionRequired: boolean;
  unitPrice: number;
  quantity: number;
  availableQuantity: number;
  lineTotal: number;
  stockSufficient: boolean;
}

export interface CartView {
  cartId: number;
  pharmacyId?: number | null;
  pharmacyName?: string | null;
  distanceKm: number;
  lines: CartLine[];
  itemCount: number;
  subtotal: number;
  deliveryFee: number;
  taxAmount: number;
  totalAmount: number;
  warnings: string[];
  checkoutReady: boolean;
  prescriptionNotice?: string | null;
}

export interface Address {
  id: number;
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  latitude: number;
  longitude: number;
  defaultAddress: boolean;
}

export interface Prescription {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: 'UPLOADED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';
  pharmacyId?: number;
  orderId?: number;
  notes?: string;
  rejectionReason?: string;
  uploadedAt: string;
  reviewedAt?: string;
  fileUrl: string;
  demo: boolean;
}

export type OrderStatus =
  | 'CREATED' | 'PAYMENT_PENDING' | 'PAYMENT_CONFIRMED' | 'PRESCRIPTION_REVIEW_PENDING'
  | 'PRESCRIPTION_APPROVED' | 'PRESCRIPTION_REJECTED' | 'PHARMACY_ACCEPTED' | 'PREPARING'
  | 'READY_FOR_PICKUP' | 'DELIVERY_ASSIGNED' | 'PICKED_UP' | 'OUT_FOR_DELIVERY'
  | 'DELIVERED' | 'CANCELLATION_REQUESTED' | 'CANCELLED' | 'REFUND_PENDING' | 'REFUNDED' | 'FAILED';

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  pharmacyName?: string;
  totalAmount: number;
  itemCount: number;
  placedAt: string;
  paymentMethod: string;
  prescriptionRequired: boolean;
  city: string;
}

export interface OrderItem {
  id: number;
  medicineId: number;
  medicineName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  prescriptionRequired: boolean;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  pharmacyId: number;
  pharmacyName?: string;
  pharmacyPhone?: string;
  customerName?: string;
  customerPhone?: string;
  addressLine: string;
  city: string;
  state: string;
  pincode: string;
  latitude: number;
  longitude: number;
  pharmacyLatitude?: number;
  pharmacyLongitude?: number;
  subtotal: number;
  discount: number;
  deliveryFee: number;
  taxAmount: number;
  totalAmount: number;
  paymentMethod: string;
  prescriptionRequired: boolean;
  prescriptionId?: number;
  prescriptionStatus?: string;
  couponCode?: string;
  customerNote?: string;
  cancellationReason?: string;
  placedAt: string;
  deliveredAt?: string;
  distanceKm?: number;
  etaMinutes?: number;
  items: OrderItem[];
  history: { fromStatus?: string; toStatus: string; actorRole?: string; reason?: string; at: string }[];
  deliveryPartner?: {
    id: number; name: string; phone: string; latitude?: number; longitude?: number;
    lastLocationAt?: string; vehicleType?: string;
  } | null;
  trackingKey?: string;
  deliveryOtp?: string;
  deliveryOtpVerified: boolean;
}

export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  body: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export interface PharmacyProfile {
  id: number;
  name: string;
  address: string;
  city: string;
  state: string;
  pincode: string;
  latitude: number;
  longitude: number;
  rating: number;
  minOrderValue: number;
  deliveryRadiusKm: number;
  phone: string;
}

export interface PharmacyDashboard {
  totalOrders: number;
  pendingOrders: number;
  awaitingPrescription: number;
  readyForPickup: number;
  completedOrders: number;
  cancelledOrders: number;
  revenueLast30Days: number;
  lowStockItems: number;
  activeListings: number;
  pendingPrescriptionReviews: number;
  pharmacyName: string;
  status: string;
  acceptingOrders: boolean;
  rating: number;
}

export interface InventoryRow {
  id: number;
  medicineId: number;
  medicineName: string;
  brand?: string;
  strength?: string;
  packSize?: string;
  prescriptionRequired: boolean;
  price: number;
  mrp: number;
  stockQuantity: number;
  availableQuantity: number;
  lowStockThreshold: number;
  active: boolean;
  nearestExpiry?: string;
  version: number;
}

export interface DeliveryAssignment {
  id: number;
  orderId: number;
  orderNumber: string;
  status: string;
  pharmacyName?: string;
  pharmacyAddress?: string;
  pharmacyLatitude?: number;
  pharmacyLongitude?: number;
  customerArea?: string;
  destinationLatitude?: number;
  destinationLongitude?: number;
  distanceKm?: number;
  estimatedMinutes?: number;
  payout: number;
  assignedAt: string;
  prescriptionRequired: boolean;
  otpVerified: boolean;
  trackingKey?: string;
}

export interface CheckoutSession {
  paymentId: number;
  provider: string;
  providerOrderId: string;
  publicKey?: string;
  amountPaise: number;
  currency: string;
  testMode: boolean;
  status: string;
}

export interface Invoice {
  orderNumber: string;
  issuedAt: string;
  sellerName: string;
  sellerAddress: string;
  sellerGstin?: string;
  sellerLicense?: string;
  buyerName: string;
  buyerAddress: string;
  items: OrderItem[];
  subtotal: number;
  discount: number;
  taxAmount: number;
  deliveryFee: number;
  totalAmount: number;
  paymentMethod: string;
  paymentReference?: string;
  note?: string;
}

export interface DeliveryPartnerProfile {
  id: number;
  userId: number;
  fullName: string;
  phone: string;
  email: string;
  address: string;
  vehicleType?: string;
  vehicleNumber?: string;
  status: string;
  online: boolean;
  latitude?: number;
  longitude?: number;
  lastLocationAt?: string;
  completedDeliveries: number;
  rating: number;
  reviewNote?: string;
}

export interface DeliveryDashboard {
  profile: DeliveryPartnerProfile;
  online: boolean;
  pendingAssignments: number;
  activeDeliveries: number;
  completedDeliveries: number;
  earningsToday: number;
  earningsTotal: number;
  active: DeliveryAssignment[];
}

export interface PharmacyAnalytics {
  days: number;
  series: { date: string; orders: number; revenue: number }[];
  revenue: number;
  averageOrderValue: number;
  deliveredOrders: number;
  cancelledOrders: number;
  lowStockItems: number;
  topProducts: { label: string; count: number }[];
  forecast: { medicine: string; unitsLast30Days: number; suggestedNext7Days: number; method: string }[];
}

export interface AdminUserRow {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  role: Role;
  enabled: boolean;
  locked: boolean;
  emailVerified: boolean;
  joinedAt: string;
}

export interface AdminDocument {
  documentType: string;
  fileName: string;
  status: string;
  remarks?: string;
}

export interface AdminPharmacyRow {
  id: number;
  name: string;
  city: string;
  licenseNumber: string;
  phone: string;
  status: string;
  registeredAt: string;
  rating: number;
  documents: AdminDocument[];
}

export interface AdminPartnerRow {
  id: number;
  fullName: string;
  phone: string;
  vehicleType?: string;
  status: string;
  online: boolean;
  completedDeliveries: number;
  rating: number;
  appliedAt: string;
  documents: AdminDocument[];
}

export interface AdminOrderRow {
  id: number;
  orderNumber: string;
  customerName: string;
  pharmacyName: string;
  status: OrderStatus;
  totalAmount: number;
  placedAt: string;
  customerRole: Role;
}

export interface PlatformReport {
  days: number;
  orders: { date: string; orders: number; revenue: number }[];
  grossRevenue: number;
  averageOrderValue: number;
  cancellationRate: number;
  averageDeliveryMinutes: number;
  deliveredOrders: number;
  topPharmacies: { label: string; count: number }[];
  topMedicines: { label: string; count: number }[];
}

export interface CouponRow {
  id: number;
  code: string;
  type: string;
  value: number;
  minOrderValue: number;
  maxDiscount?: number;
  active: boolean;
  usageLimit?: number;
  usedCount: number;
  validTo?: string;
}

export interface SystemSetting {
  key: string;
  value: string;
  description?: string;
  publicSetting: boolean;
}

export interface SupportTicket {
  id: number;
  customerId: number;
  customerName: string;
  orderId?: number;
  subject: string;
  message: string;
  status: string;
  response?: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface AdminDashboard {
  totalCustomers: number;
  totalPharmacies: number;
  pendingPharmacies: number;
  totalPartners: number;
  pendingPartners: number;
  totalOrders: number;
  activeOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  revenue: number;
  refunded: number;
  ordersToday: number;
  pendingPrescriptions: number;
  openSupportTickets: number;
  orderTrend: { date: string; orders: number; revenue: number }[];
}
