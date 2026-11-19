package com.medidrop.pricing;

import com.medidrop.config.MedidropProperties;
import com.medidrop.promo.Coupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        MedidropProperties properties = new MedidropProperties();
        properties.getDelivery().setBaseFee(new BigDecimal("25.00"));
        properties.getDelivery().setFreeAbove(new BigDecimal("499.00"));
        properties.getDelivery().setFeePerKm(new BigDecimal("4.00"));
        properties.getCommerce().setTaxRatePercent(new BigDecimal("5.00"));
        pricingService = new PricingService(properties);
    }

    @Test
    void freeDeliveryAboveThreshold() {
        assertEquals(BigDecimal.ZERO, pricingService.deliveryFee(new BigDecimal("600.00"), 7));
    }

    @Test
    void deliveryFeeIncludesPerKmComponent() {
        BigDecimal fee = pricingService.deliveryFee(new BigDecimal("200.00"), 5);
        assertEquals(new BigDecimal("45.00"), fee);
    }

    @Test
    void percentCouponIsCapped() {
        Coupon coupon = new Coupon();
        coupon.setType(Coupon.Type.PERCENT);
        coupon.setValue(new BigDecimal("10"));
        coupon.setMaxDiscount(new BigDecimal("100.00"));
        coupon.setMinOrderValue(BigDecimal.ZERO);
        coupon.setActive(true);
        assertEquals(new BigDecimal("100.00"), pricingService.discountFor(coupon, new BigDecimal("2000.00")));
        assertEquals(new BigDecimal("20.00"), pricingService.discountFor(coupon, new BigDecimal("200.00")));
    }

    @Test
    void totalsApplyDiscountBeforeTaxAndDelivery() {
        PricingService.Totals totals = pricingService.totals(new BigDecimal("500.00"), null, 3);
        assertEquals(new BigDecimal("25.00"), totals.taxAmount());
        assertEquals(0, totals.deliveryFee().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("525.00"), totals.totalAmount());
    }

    @Test
    void couponBelowMinimumIsRejected() {
        Coupon coupon = new Coupon();
        coupon.setType(Coupon.Type.FLAT);
        coupon.setValue(new BigDecimal("50.00"));
        coupon.setMinOrderValue(new BigDecimal("299.00"));
        coupon.setActive(true);
        assertThrows(RuntimeException.class,
                () -> pricingService.discountFor(coupon, new BigDecimal("100.00")));
    }
}
