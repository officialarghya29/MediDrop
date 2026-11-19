package com.medidrop.maps;

public interface MapsService {

    GeoPoint geocode(String address);

    GeoPoint reverseGeocode(double latitude, double longitude);

    Route route(double fromLat, double fromLng, double toLat, double toLng);

    String providerName();

    record GeoPoint(double latitude, double longitude, String formattedAddress) {
    }

    record Route(double distanceKm, int durationMinutes) {
    }
}
