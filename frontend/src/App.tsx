import { useEffect } from 'react';
import { Link, Route, Routes, useLocation } from 'react-router-dom';
import Shell from './components/Shell';
import ProtectedRoute from './components/ProtectedRoute';
import HomePage from './features/customer/HomePage';
import SearchPage from './features/customer/SearchPage';
import MedicineDetailPage from './features/customer/MedicineDetailPage';
import CartPage from './features/customer/CartPage';
import CheckoutPage from './features/customer/CheckoutPage';
import OrdersPage from './features/customer/OrdersPage';
import OrderDetailPage from './features/customer/OrderDetailPage';
import PrescriptionsPage from './features/customer/PrescriptionsPage';
import ProfilePage from './features/customer/ProfilePage';
import NotificationsPage from './features/customer/NotificationsPage';
import TrackPage from './features/customer/TrackPage';
import { LoginPage, RegisterPage, VerifyPage, ForgotPasswordPage, ResetPasswordPage } from './features/auth/AuthPages';
import PharmacyRegisterPage from './features/pharmacy/PharmacyRegisterPage';
import PharmacyDashboardPage from './features/pharmacy/PharmacyDashboardPage';
import PharmacyOrdersPage from './features/pharmacy/PharmacyOrdersPage';
import InventoryPage from './features/pharmacy/InventoryPage';
import PharmacyPrescriptionsPage from './features/pharmacy/PharmacyPrescriptionsPage';
import PharmacyAnalyticsPage from './features/pharmacy/PharmacyAnalyticsPage';
import DeliveryRegisterPage from './features/delivery/DeliveryRegisterPage';
import DeliveryDashboardPage from './features/delivery/DeliveryDashboardPage';
import DeliveryHistoryPage from './features/delivery/DeliveryHistoryPage';
import AdminDashboardPage from './features/admin/AdminDashboardPage';
import AdminApprovalsPage from './features/admin/AdminApprovalsPage';
import AdminOrdersPage from './features/admin/AdminOrdersPage';
import AdminUsersPage from './features/admin/AdminUsersPage';
import AdminConfigPage from './features/admin/AdminConfigPage';

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
  }, [pathname]);
  return null;
}

function NotFound() {
  return (
    <div className="container-page py-20 text-center">
      <p className="text-sm font-semibold uppercase tracking-wide text-brand-700">404</p>
      <h1 className="mt-2 text-2xl font-bold">Page not found</h1>
      <p className="muted mt-2">The page you are looking for does not exist or has moved.</p>
      <Link to="/" className="btn-primary mt-5">Back to MediDrop</Link>
    </div>
  );
}

export default function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<HomePage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="medicines/:id" element={<MedicineDetailPage />} />
          <Route path="track/:key" element={<TrackPage />} />

          <Route path="cart" element={<ProtectedRoute roles={['CUSTOMER']}><CartPage /></ProtectedRoute>} />
          <Route path="checkout" element={<ProtectedRoute roles={['CUSTOMER']}><CheckoutPage /></ProtectedRoute>} />
          <Route path="orders" element={<ProtectedRoute roles={['CUSTOMER']}><OrdersPage /></ProtectedRoute>} />
          <Route path="orders/:id" element={<ProtectedRoute roles={['CUSTOMER']}><OrderDetailPage /></ProtectedRoute>} />
          <Route path="prescriptions" element={<ProtectedRoute roles={['CUSTOMER']}><PrescriptionsPage /></ProtectedRoute>} />
          <Route path="profile" element={<ProtectedRoute roles={['CUSTOMER']}><ProfilePage /></ProtectedRoute>} />
          <Route
            path="notifications"
            element={<ProtectedRoute roles={['CUSTOMER', 'PHARMACY', 'DELIVERY', 'ADMIN']}><NotificationsPage /></ProtectedRoute>}
          />

          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="verify" element={<VerifyPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />

          <Route path="pharmacy/register" element={<PharmacyRegisterPage />} />
          <Route path="delivery/register" element={<DeliveryRegisterPage />} />

          <Route path="pharmacy" element={<ProtectedRoute roles={['PHARMACY']}><PharmacyDashboardPage /></ProtectedRoute>} />
          <Route path="pharmacy/orders" element={<ProtectedRoute roles={['PHARMACY']}><PharmacyOrdersPage /></ProtectedRoute>} />
          <Route path="pharmacy/inventory" element={<ProtectedRoute roles={['PHARMACY']}><InventoryPage /></ProtectedRoute>} />
          <Route path="pharmacy/prescriptions" element={<ProtectedRoute roles={['PHARMACY']}><PharmacyPrescriptionsPage /></ProtectedRoute>} />
          <Route path="pharmacy/analytics" element={<ProtectedRoute roles={['PHARMACY']}><PharmacyAnalyticsPage /></ProtectedRoute>} />

          <Route path="delivery" element={<ProtectedRoute roles={['DELIVERY']}><DeliveryDashboardPage /></ProtectedRoute>} />
          <Route path="delivery/history" element={<ProtectedRoute roles={['DELIVERY']}><DeliveryHistoryPage /></ProtectedRoute>} />

          <Route path="admin" element={<ProtectedRoute roles={['ADMIN']}><AdminDashboardPage /></ProtectedRoute>} />
          <Route path="admin/approvals" element={<ProtectedRoute roles={['ADMIN']}><AdminApprovalsPage /></ProtectedRoute>} />
          <Route path="admin/orders" element={<ProtectedRoute roles={['ADMIN']}><AdminOrdersPage /></ProtectedRoute>} />
          <Route path="admin/users" element={<ProtectedRoute roles={['ADMIN']}><AdminUsersPage /></ProtectedRoute>} />
          <Route path="admin/config" element={<ProtectedRoute roles={['ADMIN']}><AdminConfigPage /></ProtectedRoute>} />

          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </>
  );
}
