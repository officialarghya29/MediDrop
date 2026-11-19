import { useCallback, useState } from 'react';

export type GeoState = 'idle' | 'requesting' | 'granted' | 'denied' | 'unavailable';

export function useGeolocation() {
  const [state, setState] = useState<GeoState>('idle');
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const request = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setState('unavailable');
      setError('Location is not supported by this browser.');
      return;
    }
    setState('requesting');
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setCoords({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        setState('granted');
        setError(null);
      },
      (err) => {
        setState(err.code === err.PERMISSION_DENIED ? 'denied' : 'unavailable');
        setError(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission was denied. Pick your area manually instead.'
            : 'We could not determine your location. Pick your area manually instead.',
        );
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 60_000 },
    );
  }, []);

  return { state, coords, error, request };
}
