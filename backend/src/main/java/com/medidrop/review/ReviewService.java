package com.medidrop.review;

import com.medidrop.common.exception.ApiException;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderStatus;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final PharmacyRepository pharmacyRepository;

    @Transactional
    public void submit(Long customerId, Long orderId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw ApiException.badRequest("Rating must be between 1 and 5");
        }
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.conflict("You can review an order after it is delivered");
        }
        if (reviewRepository.findByOrderId(orderId).isPresent()) {
            throw ApiException.conflict("This order has already been reviewed");
        }
        Review review = new Review();
        review.setOrderId(orderId);
        review.setCustomerId(customerId);
        review.setPharmacyId(order.getPharmacyId());
        review.setRating(rating);
        review.setComment(comment);
        reviewRepository.save(review);

        Double average = reviewRepository.averageForPharmacy(order.getPharmacyId());
        Pharmacy pharmacy = pharmacyRepository.findById(order.getPharmacyId()).orElse(null);
        if (pharmacy != null && average != null) {
            pharmacy.setRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
            pharmacyRepository.save(pharmacy);
        }
    }

    @Transactional(readOnly = true)
    public List<Review> forPharmacy(Long pharmacyId) {
        return reviewRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId);
    }
}
