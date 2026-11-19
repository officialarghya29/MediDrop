package com.medidrop.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "medidrop.maps.provider", havingValue = "google")
@RequiredArgsConstructor
@Slf4j
public class GoogleMapsService implements MapsService {

    private final MedidropProperties properties;
    private final RestClient restClient = RestClient.create();

    private String key() {
        String key = properties.getMaps().getApiKey();
        if (key == null || key.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "MAPS_NOT_CONFIGURED",
                    "Maps provider is not configured");
        }
        return key;
    }

    @Override
    public GeoPoint geocode(String address) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getMaps().getBaseUrl() + "/geocode/json")
                .queryParam("address", address)
                .queryParam("region", "in")
                .queryParam("key", key())
                .encode().toUriString();
        return parseGeocode(fetch(url), "Address not found");
    }

    @Override
    public GeoPoint reverseGeocode(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getMaps().getBaseUrl() + "/geocode/json")
                .queryParam("latlng", latitude + "," + longitude)
                .queryParam("key", key())
                .encode().toUriString();
        return parseGeocode(fetch(url), "Location not resolved");
    }

    private GeoPoint parseGeocode(JsonNode body, String error) {
        JsonNode results = body.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw ApiException.badRequest(error);
        }
        JsonNode first = results.get(0);
        JsonNode location = first.path("geometry").path("location");
        return new GeoPoint(location.path("lat").asDouble(), location.path("lng").asDouble(),
                first.path("formatted_address").asText(null));
    }

    @Override
    public Route route(double fromLat, double fromLng, double toLat, double toLng) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getMaps().getBaseUrl() + "/directions/json")
                .queryParam("origin", fromLat + "," + fromLng)
                .queryParam("destination", toLat + "," + toLng)
                .queryParam("mode", "driving")
                .queryParam("region", "in")
                .queryParam("key", key())
                .encode().toUriString();
        JsonNode body = fetch(url);
        JsonNode route = body.path("routes").path(0);
        if (route.isMissingNode()) {
            throw ApiException.badRequest("No route available between these locations");
        }
        JsonNode leg = route.path("legs").path(0);
        double km = leg.path("distance").path("value").asDouble() / 1000.0;
        int minutes = Math.max(1, leg.path("duration").path("value").asInt() / 60);
        return new Route(Math.round(km * 100.0) / 100.0, minutes);
    }

    @Override
    public String providerName() {
        return "google";
    }

    private JsonNode fetch(String url) {
        try {
            JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "MAPS_ERROR",
                        "Empty response from maps provider");
            }
            String status = body.path("status").asText("");
            if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
                log.warn("Maps provider returned status {} ({})", status, body.path("error_message").asText(""));
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "MAPS_ERROR",
                        "Maps provider error: " + status);
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Maps request failed: {}", ex.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "MAPS_UNAVAILABLE",
                    "Maps provider is currently unavailable");
        }
    }

    @SuppressWarnings("unused")
    private static Map<String, String> region() {
        return Map.of("region", "in");
    }
}
