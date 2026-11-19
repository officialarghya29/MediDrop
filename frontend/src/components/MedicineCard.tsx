import { Link } from 'react-router-dom';
import { Pill, ShieldAlert } from 'lucide-react';
import { Badge } from './ui';
import { inr } from '../lib/format';
import type { MedicineSummary } from '../types';

export default function MedicineCard({ medicine }: { medicine: MedicineSummary }) {
  return (
    <Link
      to={`/medicines/${medicine.id}`}
      className="card group flex h-full flex-col p-4 transition hover:-translate-y-0.5 hover:shadow-soft"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-mint text-brand-700">
          <Pill className="h-5 w-5" />
        </div>
        {medicine.prescriptionRequired ? (
          <Badge tone="bg-amber-50 text-amber-800">
            <ShieldAlert className="h-3 w-3" /> Rx
          </Badge>
        ) : (
          <Badge tone="bg-emerald-50 text-emerald-700">OTC</Badge>
        )}
      </div>
      <h3 className="mt-3 line-clamp-2 text-sm font-semibold leading-snug text-navy-900 group-hover:text-brand-800">
        {medicine.name}
      </h3>
      <p className="mt-1 text-xs text-slate-500">
        {[medicine.strength, medicine.dosageForm, medicine.packSize].filter(Boolean).join(' · ') || 'Medicine'}
      </p>
      <p className="mt-0.5 text-xs text-slate-400">{medicine.categoryName}</p>
      <div className="mt-auto flex items-end justify-between pt-4">
        <div>
          {medicine.lowestPrice ? (
            <>
              <p className="text-sm font-bold text-navy-900">{inr(medicine.lowestPrice)}</p>
              {medicine.mrp && medicine.mrp > medicine.lowestPrice && (
                <p className="text-[11px] text-slate-400 line-through">{inr(medicine.mrp)}</p>
              )}
            </>
          ) : (
            <p className="text-xs font-medium text-slate-500">Check availability</p>
          )}
        </div>
        <span className="text-[11px] text-slate-500">
          {medicine.pharmacyCount > 0 ? `${medicine.pharmacyCount} pharmacy` : 'No stock'}
          {medicine.pharmacyCount > 1 ? 'ies' : ''}
        </span>
      </div>
    </Link>
  );
}
