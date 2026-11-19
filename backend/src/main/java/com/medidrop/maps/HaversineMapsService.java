package com.medidrop.maps;

import com.medidrop.common.Haversine;
import com.medidrop.common.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "medidrop.maps.provider", havingValue = "haversine", matchIfMissing = true)
public class HaversineMapsService implements MapsService {

    private static final double CITY_SPEED_KMH = 22.0;

    @Override
    public GeoPoint geocode(String address) {
        // Geocoding requires a configured provider; failing loudly is preferable to
        // inventing coordinates for a real address.
        throw ApiException.badRequest("Address search requires a configured maps provider. " +
                "Set MEDIDROP_MAPS_PROVIDER=google and MEDIDROP_MAPS_API_KEY.");
    }

    @Override
    public GeoPoint reverseGeocode(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude, null);
    }

    @Override
    public Route route(double fromLat, double fromLng, double toLat, double toLng) {
        double distance = Haversine.distanceKm(fromLat, fromLng, toLat, toLng);
        int minutes = (int) Math.max(1, Math.ceil((distance / CITY_SPEED_KMH) * 60));
        return new Route(round(distance), minutes);
    }

    @Override
    public String providerName() {
        return "haversine";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
