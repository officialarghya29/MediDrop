import { Link } from 'react-router-dom';
import { ArrowUpRight, Pill, ShieldAlert } from 'lucide-react';
import { Badge } from './ui';
import { inr } from '../lib/format';
import type { MedicineSummary } from '../types';

const TILES = [
  'from-grape-500 via-violet-500 to-fuchsia-500',
  'from-brand-400 via-emerald-400 to-teal-500',
  'from-sky-400 via-cyan-400 to-blue-500',
  'from-sun-300 via-amber-400 to-orange-400',
  'from-coral-300 via-rose-400 to-pink-500',
  'from-indigo-400 via-grape-500 to-purple-600',
];

const WASHES = [
  'from-grape-50/80',
  'from-mint/80',
  'from-sky-50/80',
  'from-sun-50/80',
  'from-coral-50/80',
  'from-indigo-50/80',
];

export default function MedicineCard({ medicine }: { medicine: MedicineSummary }) {
  const tone = medicine.id % TILES.length;
  const available = medicine.pharmacyCount > 0;

  return (
    <Link
      to={`/medicines/${medicine.id}`}
      className={`card group relative flex h-full flex-col overflow-hidden p-3.5 transition duration-300 hover:-translate-y-1 hover:shadow-soft sm:p-4`}
    >
      <div className={`relative overflow-hidden rounded-2xl bg-gradient-to-br ${TILES[tone]} p-4 text-white`}>
        <div className="absolute -right-5 -top-7 h-24 w-24 rounded-full bg-white/20 blur-xl" />
        <div className="absolute -bottom-8 -left-4 h-20 w-20 rounded-full bg-navy-900/20 blur-xl" />
        <div className="relative flex items-start justify-between gap-2">
          <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white/20 ring-1 ring-white/30 backdrop-blur">
            <Pill className="h-5 w-5" />
          </span>
          {medicine.prescriptionRequired ? (
            <Badge tone="bg-white/90 text-amber-800">
              <ShieldAlert className="h-3 w-3" /> Rx
            </Badge>
          ) : (
            <Badge tone="bg-white/90 text-emerald-700">OTC</Badge>
          )}
        </div>
        <p className="relative mt-5 truncate text-[10px] font-bold uppercase tracking-[0.16em] text-white/70">
          {medicine.categoryName ?? 'Everyday care'}
        </p>
      </div>

      <div className={`-mx-1 mt-1 rounded-b-2xl bg-gradient-to-b ${WASHES[tone]} to-transparent px-1 pb-1`}>
        <h3 className="mt-2 line-clamp-2 text-[15px] font-bold leading-snug text-navy-900 transition group-hover:text-brand-800">
          {medicine.name}
        </h3>
        <p className="mt-1 line-clamp-1 text-[11px] text-slate-500">
          {[medicine.strength, medicine.dosageForm, medicine.packSize].filter(Boolean).join(' · ') || 'Medicine'}
        </p>
      </div>

      <div className="mt-auto flex items-end justify-between gap-2 pt-4">
        <div>
          {medicine.lowestPrice ? (
            <>
              <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">From</p>
              <p className="font-display text-lg font-bold text-navy-900">{inr(medicine.lowestPrice)}</p>
              {medicine.mrp && medicine.mrp > medicine.lowestPrice && (
                <p className="text-[10px] text-slate-400 line-through">{inr(medicine.mrp)}</p>
              )}
            </>
          ) : (
            <p className="text-xs font-semibold text-slate-500">Check availability</p>
          )}
        </div>
        <div className="flex flex-col items-end gap-1">
          <span className={`inline-flex items-center gap-1 text-[10px] font-bold ${available ? 'text-emerald-700' : 'text-slate-400'}`}>
            <span className={`h-1.5 w-1.5 rounded-full ${available ? 'bg-emerald-500' : 'bg-slate-300'}`} />
            {available
              ? `${medicine.pharmacyCount} ${medicine.pharmacyCount === 1 ? 'pharmacy' : 'pharmacies'}`
              : 'No stock'}
          </span>
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-navy-900 text-white transition group-hover:bg-brand-700">
            <ArrowUpRight className="h-3.5 w-3.5" />
          </span>
        </div>
      </div>
    </Link>
  );
}
