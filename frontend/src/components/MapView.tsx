import { useEffect, useRef, useState } from 'react';
import { MapPin, Navigation } from 'lucide-react';

type LatLng = { lat: number; lng: number };

declare global {
  interface Window {
    google?: any;
    __medidropMapsLoading?: Promise<void>;
  }
}

const MAPS_KEY = (import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined) ?? '';

function loadGoogleMaps(): Promise<void> {
  if (window.google?.maps) return Promise.resolve();
  if (window.__medidropMapsLoading) return window.__medidropMapsLoading;
  window.__medidropMapsLoading = new Promise<void>((resolve, reject) => {
    if (!MAPS_KEY) {
      reject(new Error('Maps key not configured'));
      return;
    }
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(MAPS_KEY)}&v=weekly`;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Failed to load Google Maps'));
    document.head.appendChild(script);
  });
  return window.__medidropMapsLoading;
}

export interface MapMarker {
  lat: number;
  lng: number;
  label: string;
  tone?: 'brand' | 'navy' | 'amber';
}

export default function MapView({
  center,
  markers,
  height = 260,
  caption,
}: {
  center: LatLng | null;
  markers: MapMarker[];
  height?: number;
  caption?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'unconfigured' | 'failed'>(
    MAPS_KEY ? 'loading' : 'unconfigured',
  );

  useEffect(() => {
    if (!MAPS_KEY || !center || !ref.current) return;
    let cancelled = false;
    loadGoogleMaps()
      .then(() => {
        if (cancelled || !ref.current || !window.google?.maps) return;
        const map = new window.google.maps.Map(ref.current, {
          center,
          zoom: 13,
          disableDefaultUI: true,
          zoomControl: true,
          gestureHandling: 'cooperative',
        });
        const colors: Record<string, string> = { brand: '#0b8b68', navy: '#17313d', amber: '#d97706' };
        markers.forEach((marker) => {
          new window.google.maps.Marker({
            position: { lat: marker.lat, lng: marker.lng },
            map,
            title: marker.label,
            icon: {
              path: window.google.maps.SymbolPath.CIRCLE,
              scale: 8,
              fillColor: colors[marker.tone ?? 'brand'],
              fillOpacity: 1,
              strokeColor: '#ffffff',
              strokeWeight: 2,
            },
          });
        });
        setStatus('ready');
      })
      .catch(() => setStatus('failed'));
    return () => {
      cancelled = true;
    };
  }, [center, markers]);

  const externalLink = center
    ? `https://www.openstreetmap.org/?mlat=${center.lat}&mlon=${center.lng}#map=15/${center.lat}/${center.lng}`
    : undefined;

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      {status === 'loading' && (
        <div style={{ height }} className="flex items-center justify-center bg-slate-50">
          <span className="muted">Loading map…</span>
        </div>
      )}
      {status === 'ready' && <div ref={ref} style={{ height }} aria-label="Delivery route map" />}
      {(status === 'unconfigured' || status === 'failed' || !center) && (
        <div style={{ minHeight: height }} className="flex flex-col justify-center gap-3 bg-slate-50 p-5">
          <div className="flex items-center gap-2 text-sm font-semibold text-navy-800">
            <MapPin className="h-4 w-4 text-brand-700" />
            {center ? 'Coordinate map' : 'Location not available'}
          </div>
          {center ? (
            <>
              <p className="text-sm text-slate-600">
                Live map tiles are not configured in this environment. The coordinates below are the real
                locations returned by the MediDrop API.
              </p>
              <div className="grid gap-2 text-xs text-slate-600 sm:grid-cols-2">
                <span className="rounded-lg bg-white px-3 py-2 font-mono">
                  Latitude {center.lat.toFixed(5)}
                </span>
                <span className="rounded-lg bg-white px-3 py-2 font-mono">
                  Longitude {center.lng.toFixed(5)}
                </span>
              </div>
              {externalLink && (
                <a href={externalLink} target="_blank" rel="noreferrer" className="link inline-flex items-center gap-1 text-sm">
                  <Navigation className="h-3.5 w-3.5" /> Open in OpenStreetMap
                </a>
              )}
            </>
          ) : (
            <p className="text-sm text-slate-600">Waiting for location data from the order.</p>
          )}
        </div>
      )}
      {caption && <p className="border-t border-slate-200 px-4 py-2 text-xs text-slate-500">{caption}</p>}
      {markers.length > 0 && (
        <ul className="flex flex-wrap gap-3 border-t border-slate-200 px-4 py-2 text-xs text-slate-600">
          {markers.map((marker) => (
            <li key={`${marker.label}-${marker.lat}`} className="flex items-center gap-1.5">
              <span className="inline-block h-2 w-2 rounded-full bg-brand-700" /> {marker.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
