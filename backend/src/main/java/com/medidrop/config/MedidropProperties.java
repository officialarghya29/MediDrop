package com.medidrop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "medidrop")
@Getter
@Setter
public class MedidropProperties {

    private String publicBaseUrl = "http://localhost:8080";
    private String frontendBaseUrl = "http://localhost:5173";
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Otp otp = new Otp();
    private Storage storage = new Storage();
    private Maps maps = new Maps();
    private Payments payments = new Payments();
    private Delivery delivery = new Delivery();
    private Commerce commerce = new Commerce();
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "";
        private long accessTokenMinutes = 15;
        private long refreshTokenDays = 7;
        private String issuer = "medidrop";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 10;
        private boolean exposeInResponse = false;
        private int maxAttempts = 5;
    }

    @Getter
    @Setter
    public static class Storage {
        private String provider = "local";
        private String localPath = "./storage/prescriptions";
        private long maxFileSizeBytes = 5 * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Maps {
        private String provider = "haversine";
        private String apiKey = "";
        private String baseUrl = "https://maps.googleapis.com/maps/api";
        private double defaultDeliveryRadiusKm = 10.0;
    }

    @Getter
    @Setter
    public static class Payments {
        private String provider = "sandbox";
        private String razorpayKeyId = "";
        private String razorpayKeySecret = "";
        private String razorpayWebhookSecret = "";
        private String razorpayBaseUrl = "https://api.razorpay.com/v1";
    }

    @Getter
    @Setter
    public static class Delivery {
        private BigDecimal baseFee = new BigDecimal("25.00");
        private BigDecimal freeAbove = new BigDecimal("499.00");
        private BigDecimal feePerKm = new BigDecimal("4.00");
        private int assignmentSearchRadiusKm = 8;
    }

    @Getter
    @Setter
    public static class Commerce {
        private BigDecimal taxRatePercent = new BigDecimal("5.00");
        private int cartMaxQuantityPerLine = 10;
        private int cancelWindowMinutes = 10;
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = false;
        private String adminEmail = "admin@medidrop.local";
        private String adminPassword = "";
    }
}
