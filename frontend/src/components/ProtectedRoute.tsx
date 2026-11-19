import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../store/auth';
import type { Role } from '../types';

export default function ProtectedRoute({
  roles,
  children,
}: {
  roles: Role[];
  children: React.ReactNode;
}) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (!roles.includes(user.role)) {
    return (
      <div className="container-page py-16">
        <div className="card-pad mx-auto max-w-lg text-center">
          <h1 className="text-xl font-bold">Access restricted</h1>
          <p className="mt-2 text-sm text-slate-600">
            This area is only available to {roles.map((role) => role.toLowerCase()).join(' / ')} accounts.
          </p>
          <a href="/" className="btn-primary mt-5">
            Back to MediDrop
          </a>
        </div>
      </div>
    );
  }
  return <>{children}</>;
}
