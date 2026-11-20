import { useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowLeft, CheckCircle2, KeyRound, MailCheck, ShieldCheck, Sparkles } from 'lucide-react';
import Logo, { LogoMark } from '../../components/Logo';
import { Alert, FieldError, Spinner, useToast } from '../../components/ui';
import { apiPost, errorMessage } from '../../lib/api';
import { dashboardPathFor, useAuth } from '../../store/auth';
import type { AuthResponse } from '../../types';

const passwordRule = z
  .string()
  .min(8, 'Use at least 8 characters')
  .max(72, 'Password is too long')
  .regex(/^(?=.*[A-Za-z])(?=.*\d).+$/, 'Include at least one letter and one number');

function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="container-page py-8 lg:py-12">
      <div className="mx-auto grid max-w-5xl overflow-hidden rounded-[2rem] border border-white/80 bg-white/80 shadow-soft backdrop-blur-xl lg:grid-cols-[1.02fr_0.98fr]">
        <aside className="mesh-hero relative hidden flex-col justify-between overflow-hidden p-10 text-white lg:flex">
          <div className="grid-lines pointer-events-none absolute inset-0" />
          <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-grape-500/35 blur-3xl" />
          <div className="relative">
            <div>
              <LogoMark size={104} variant="emblem" />
              <p className="mt-3 text-[9px] font-bold uppercase tracking-[0.18em] text-brand-200">
                Kolkata&apos;s local care network
              </p>
            </div>
            <h2 className="mt-10 font-display text-4xl font-bold leading-[1.02] tracking-tight">
              Health, but make it <span className="gradient-text">make sense</span>.
            </h2>
            <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-300">
              One account for live pharmacy stock, pharmacist-reviewed prescriptions and tracked handover.
            </p>
          </div>
          <div className="relative mt-12 space-y-3">
            {[
              'Licensed pharmacies only',
              'Prescription checked before dispensing',
              'OTP-verified delivery handover',
            ].map((item) => (
              <div key={item} className="flex items-center gap-3 rounded-2xl bg-white/5 px-3.5 py-3 ring-1 ring-white/10">
                <CheckCircle2 className="h-4 w-4 shrink-0 text-brand-300" />
                <span className="text-xs font-semibold text-slate-200">{item}</span>
              </div>
            ))}
            <p className="flex items-center gap-2 pt-2 text-[10px] text-slate-400">
              <Sparkles className="h-3.5 w-3.5 text-grape-300" /> Built for Kolkata&apos;s neighbourhood pharmacies.
            </p>
          </div>
        </aside>

        <div className="p-6 sm:p-10">
          <div className="mb-6 flex lg:hidden">
            <Logo />
          </div>
          <span className="eyebrow">Secure account</span>
          <h1 className="mt-4 font-display text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
          <p className="muted mt-2">{subtitle}</p>
          <div className="mt-6">{children}</div>
          {footer && <div className="mt-5 text-center text-sm text-slate-600">{footer}</div>}
          <p className="mt-7 flex items-start gap-2 border-t border-slate-100 pt-4 text-[11px] leading-relaxed text-slate-500">
            <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-brand-700" />
            Prescription medicines are dispensed only after a registered pharmacist reviews and approves them.
          </p>
        </div>
      </div>
    </div>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocationState();
  const login = useAuth((state) => state.login);
  const toast = useToast();
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const schema = z.object({
    identifier: z.string().min(3, 'Enter your email or mobile number'),
    password: z.string().min(1, 'Enter your password'),
  });
  type FormValues = z.infer<typeof schema>;
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    setSubmitting(true);
    try {
      const user = await login(values.identifier.trim(), values.password);
      toast(`Welcome back, ${user.fullName.split(' ')[0]}`, 'success');
      navigate(location ?? dashboardPathFor(user.role), { replace: true });
    } catch (error) {
      setServerError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Sign in to MediDrop"
      subtitle="Access your orders, prescriptions and deliveries."
      footer={
        <>
          New to MediDrop? <Link to="/register" className="link">Create an account</Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {serverError && <Alert tone="error">{serverError}</Alert>}
        <div>
          <label className="label" htmlFor="identifier">Email or mobile number</label>
          <input id="identifier" className="input" autoComplete="username" {...register('identifier')} />
          <FieldError message={formState.errors.identifier?.message} />
        </div>
        <div>
          <div className="flex items-center justify-between">
            <label className="label" htmlFor="password">Password</label>
            <Link to="/forgot-password" className="text-xs font-semibold text-brand-700 hover:underline">
              Forgot password?
            </Link>
          </div>
          <input id="password" type="password" className="input" autoComplete="current-password" {...register('password')} />
          <FieldError message={formState.errors.password?.message} />
        </div>
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting ? <Spinner /> : 'Sign in'}
        </button>
      </form>
      <div className="mt-4 rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
        <p className="font-semibold text-navy-800">Pharmacy or delivery partner?</p>
        <p className="mt-1">
          <Link to="/pharmacy/register" className="link">Register a pharmacy</Link> ·{' '}
          <Link to="/delivery/register" className="link">Join as a delivery partner</Link>
        </p>
      </div>
    </AuthShell>
  );
}

function useLocationState() {
  const location = useLocation();
  const state = location.state as { from?: string } | null;
  return state?.from ?? null;
}

export function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuth((state) => state.setSession);
  const toast = useToast();
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const schema = z
    .object({
      fullName: z.string().min(2, 'Enter your full name').max(120),
      email: z.string().email('Enter a valid email'),
      phone: z.string().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit Indian mobile number'),
      password: passwordRule,
      confirmPassword: z.string(),
    })
    .refine((values) => values.password === values.confirmPassword, {
      path: ['confirmPassword'],
      message: 'Passwords do not match',
    });
  type FormValues = z.infer<typeof schema>;
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    setSubmitting(true);
    try {
      const response = await apiPost<AuthResponse>('/auth/register', {
        fullName: values.fullName,
        email: values.email,
        phone: values.phone,
        password: values.password,
      });
      setSession(response);
      toast('Account created. Verify your email to start ordering.', 'success');
      navigate(`/verify?otp=${response.devOtp ?? ''}`);
    } catch (error) {
      setServerError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Create your account"
      subtitle="Order from licensed pharmacies near you."
      footer={<>Already registered? <Link to="/login" className="link">Sign in</Link></>}
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {serverError && <Alert tone="error">{serverError}</Alert>}
        <div>
          <label className="label" htmlFor="fullName">Full name</label>
          <input id="fullName" className="input" autoComplete="name" {...register('fullName')} />
          <FieldError message={formState.errors.fullName?.message} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="email">Email</label>
            <input id="email" type="email" className="input" autoComplete="email" {...register('email')} />
            <FieldError message={formState.errors.email?.message} />
          </div>
          <div>
            <label className="label" htmlFor="phone">Mobile number</label>
            <input id="phone" inputMode="numeric" className="input" autoComplete="tel" {...register('phone')} />
            <FieldError message={formState.errors.phone?.message} />
          </div>
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" type="password" className="input" autoComplete="new-password" {...register('password')} />
          <FieldError message={formState.errors.password?.message} />
        </div>
        <div>
          <label className="label" htmlFor="confirmPassword">Confirm password</label>
          <input id="confirmPassword" type="password" className="input" autoComplete="new-password" {...register('confirmPassword')} />
          <FieldError message={formState.errors.confirmPassword?.message} />
        </div>
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting ? <Spinner /> : 'Create account'}
        </button>
        <p className="text-center text-[11px] leading-relaxed text-slate-500">
          By continuing you agree that prescription-only medicines will be dispensed only after verification by a
          licensed pharmacist. MediDrop does not provide medical advice.
        </p>
      </form>
    </AuthShell>
  );
}

export function VerifyPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { user, setSession, markVerified } = useAuth();
  const toast = useToast();
  const [code, setCode] = useState(params.get('otp') ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const verify = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const response = await apiPost<AuthResponse>('/auth/otp/verify', {
        email,
        purpose: 'EMAIL_VERIFICATION',
        code,
      });
      setSession(response);
      markVerified();
      toast('Email verified — ordering unlocked', 'success');
      navigate('/');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setError(null);
    setInfo(null);
    try {
      await apiPost('/auth/otp/request', { email, purpose: 'EMAIL_VERIFICATION' });
      setInfo('A new verification code has been sent. Check the API console in local development.');
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <AuthShell
      title="Verify your email"
      subtitle="Confirm your email address to place orders."
      footer={<Link to="/" className="link">Skip for now</Link>}
    >
      <form onSubmit={verify} className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        {info && <Alert tone="info">{info}</Alert>}
        <div>
          <label className="label" htmlFor="verify-email">Email</label>
          <input id="verify-email" type="email" className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="code">Verification code</label>
          <input
            id="code"
            className="input tracking-[0.5em]"
            inputMode="numeric"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            autoComplete="one-time-code"
          />
        </div>
        <button type="submit" className="btn-primary w-full" disabled={busy || code.length < 4}>
          {busy ? <Spinner /> : <><MailCheck className="h-4 w-4" /> Verify email</>}
        </button>
        <button type="button" onClick={resend} className="btn-secondary w-full">Resend code</button>
      </form>
    </AuthShell>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [devToken, setDevToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await apiPost<{ message: string }>('/auth/forgot-password', { email });
      setMessage(response.message);
      const match = /token \(development\): (\S+)/i.exec(response.message ?? '');
      if (match) setDevToken(match[1]);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Reset your password" subtitle="We will email a secure, single-use link." footer={<Link to="/login" className="link inline-flex items-center gap-1"><ArrowLeft className="h-3.5 w-3.5" /> Back to sign in</Link>}>
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        {message && <Alert tone="success">{message}</Alert>}
        {devToken && (
          <Alert tone="warning" title="Development mode">
            <Link to={`/reset-password?token=${devToken}`} className="link break-all">{devToken}</Link>
          </Alert>
        )}
        <div>
          <label className="label" htmlFor="forgot-email">Account email</label>
          <input id="forgot-email" type="email" className="input" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <button type="submit" className="btn-primary w-full" disabled={busy}>
          {busy ? <Spinner /> : 'Send reset link'}
        </button>
      </form>
    </AuthShell>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [token, setToken] = useState(params.get('token') ?? '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    try {
      await apiPost('/auth/reset-password', { token, newPassword: password });
      toast('Password updated. Please sign in.', 'success');
      navigate('/login');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Choose a new password" subtitle="Reset links expire after 30 minutes." footer={<Link to="/login" className="link">Back to sign in</Link>}>
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <div>
          <label className="label" htmlFor="token">Reset token</label>
          <input id="token" className="input font-mono text-xs" value={token} onChange={(e) => setToken(e.target.value)} required />
        </div>
        <div>
          <label className="label" htmlFor="new-password">New password</label>
          <input id="new-password" type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required />
        </div>
        <div>
          <label className="label" htmlFor="confirm-password">Confirm password</label>
          <input id="confirm-password" type="password" className="input" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required />
        </div>
        <button type="submit" className="btn-primary w-full" disabled={busy}>
          {busy ? <Spinner /> : <><KeyRound className="h-4 w-4" /> Update password</>}
        </button>
        <p className="flex items-start gap-2 text-xs text-slate-500">
          <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-brand-700" />
          Signing in again will invalidate all other active sessions.
        </p>
      </form>
    </AuthShell>
  );
}
