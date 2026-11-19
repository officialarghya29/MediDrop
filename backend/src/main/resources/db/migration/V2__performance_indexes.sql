-- Query-path indexes for dashboards, order history and live tracking.
CREATE INDEX IF NOT EXISTS idx_order_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_pharmacy_created ON orders (pharmacy_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_created ON orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_placed_at ON orders (placed_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_pharmacy_active ON pharmacy_inventory (pharmacy_id, active);
CREATE INDEX IF NOT EXISTS idx_prescription_customer_status ON prescriptions (customer_id, status);
CREATE INDEX IF NOT EXISTS idx_prescription_pharmacy_status ON prescriptions (pharmacy_id, status);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notifications (user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_assignment_partner_status ON delivery_assignments (partner_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_customer ON payments (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_delivery_partner_online ON delivery_partners (online, status);
