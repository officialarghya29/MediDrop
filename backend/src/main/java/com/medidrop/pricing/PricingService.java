package com.medidrop.pricing;

import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import com.medidrop.promo.Coupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final MedidropProperties properties;

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal deliveryFee,
                         BigDecimal taxAmount, BigDecimal totalAmount) {
    }

    public Totals totals(BigDecimal subtotal, Coupon coupon, double distanceKm) {
        BigDecimal discount = discountFor(coupon, subtotal);
        BigDecimal taxable = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal tax = taxable.multiply(properties.getCommerce().getTaxRatePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal delivery = deliveryFee(taxable, distanceKm);
        BigDecimal total = taxable.add(tax).add(delivery).setScale(2, RoundingMode.HALF_UP);
        return new Totals(scale(subtotal), scale(discount), scale(delivery), scale(tax), total);
    }

    public BigDecimal deliveryFee(BigDecimal subtotalAfterDiscount, double distanceKm) {
        var config = properties.getDelivery();
        if (subtotalAfterDiscount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (subtotalAfterDiscount.compareTo(config.getFreeAbove()) >= 0) return BigDecimal.ZERO;
        BigDecimal distancePart = config.getFeePerKm()
                .multiply(BigDecimal.valueOf(Math.ceil(Math.max(distanceKm, 0))))
                .min(new BigDecimal("150.00"));
        return scale(config.getBaseFee().add(distancePart));
    }

    public BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        if (coupon == null) return BigDecimal.ZERO;
        Instant now = Instant.now();
        if (!coupon.isActive()
                || (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom()))
                || (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo()))) {
            throw ApiException.badRequest("This coupon is not currently valid");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw ApiException.badRequest("This coupon has reached its usage limit");
        }
        if (subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw ApiException.badRequest("Minimum order value for this coupon is ₹"
                    + coupon.getMinOrderValue().stripTrailingZeros().toPlainString());
        }
        BigDecimal discount = coupon.getType() == Coupon.Type.PERCENT
                ? subtotal.multiply(coupon.getValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : coupon.getValue();
        if (coupon.getMaxDiscount() != null) {
            discount = discount.min(coupon.getMaxDiscount());
        }
        return scale(discount.min(subtotal));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
