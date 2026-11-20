package com.medidrop.admin;

import com.medidrop.delivery.AssignmentStatus;
import com.medidrop.delivery.DeliveryAssignment;
import com.medidrop.delivery.DeliveryAssignmentRepository;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderStatus;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<AdminDtos.TrendPoint> orderTrend(int days) {
        int window = Math.min(Math.max(days, 2), 90);
        List<Object[]> rows = orderRepository.findTrend(Instant.now().minus(Duration.ofDays(window)));
        Map<String, long[]> byDay = new HashMap<>();
        Map<String, BigDecimal> revenue = new HashMap<>();
        for (Object[] row : rows) {
            String day = row[0].toString().substring(0, 10);
            byDay.computeIfAbsent(day, k -> new long[1])[0] = ((Number) row[1]).longValue();
            revenue.put(day, (BigDecimal) row[2]);
        }
        List<AdminDtos.TrendPoint> series = new ArrayList<>();
        LocalDate start = LocalDate.now(IST).minusDays(window - 1L);
        for (int i = 0; i < window; i++) {
            LocalDate date = start.plusDays(i);
            String key = date.format(DateTimeFormatter.ISO_DATE);
            long[] count = byDay.get(key);
            series.add(new AdminDtos.TrendPoint(key, count == null ? 0 : count[0],
                    revenue.getOrDefault(key, BigDecimal.ZERO)));
        }
        return series;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PlatformReport platform(int days) {
        int window = Math.min(Math.max(days, 7), 90);
        Instant from = Instant.now().minus(Duration.ofDays(window));
        List<com.medidrop.order.Order> orders = orderRepository.findByPlacedAtAfter(from);
        BigDecimal gross = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .map(com.medidrop.order.Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long delivered = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED
                || o.getStatus() == OrderStatus.PRESCRIPTION_REJECTED).count();
        double cancellationRate = orders.isEmpty() ? 0
                : Math.round((cancelled * 10000.0) / orders.size()) / 100.0;
        BigDecimal aov = delivered == 0 ? BigDecimal.ZERO
                : gross.divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP);

        List<DeliveryAssignment> deliveredAssignments = assignmentRepository
                .findByStatusAndDeliveredAtAfter(AssignmentStatus.DELIVERED, from);
        double avgMinutes = deliveredAssignments.isEmpty() ? 0
                : Math.round(deliveredAssignments.stream()
                .mapToLong(a -> Duration.between(a.getAssignedAt(), a.getDeliveredAt()).toMinutes())
                .average().orElse(0) * 10.0) / 10.0;

        Map<Long, Long> pharmacyCounts = new HashMap<>();
        orders.forEach(o -> pharmacyCounts.merge(o.getPharmacyId(), 1L, Long::sum));
        List<Pharmacy> pharmacies = pharmacyRepository.findAllById(pharmacyCounts.keySet());
        List<AdminDtos.NamedCount> topPharmacies = pharmacies.stream()
                .map(p -> new AdminDtos.NamedCount(p.getName(), pharmacyCounts.getOrDefault(p.getId(), 0L)))
                .sorted(Comparator.comparingLong(AdminDtos.NamedCount::count).reversed())
                .limit(5)
                .toList();
        List<AdminDtos.NamedCount> topMedicines = orderRepository.topPlatformMedicines()
                .stream().limit(5)
                .map(row -> new AdminDtos.NamedCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new AdminDtos.PlatformReport(window, orderTrend(window), gross, aov, cancellationRate,
                avgMinutes, delivered, topPharmacies, topMedicines);
    }

    @Transactional(readOnly = true)
    public PharmacyAnalytics pharmacyAnalytics(Long pharmacyId, int days) {
        int window = Math.min(Math.max(days, 7), 90);
        Instant from = Instant.now().minus(Duration.ofDays(window));
        List<com.medidrop.order.Order> orders = orderRepository.findPharmacyOrdersSince(pharmacyId, from);
        Map<String, long[]> daily = new HashMap<>();
        Map<String, BigDecimal> dailyRevenue = new HashMap<>();
        for (var order : orders) {
            String key = order.getPlacedAt().atZone(IST).toLocalDate().toString();
            daily.computeIfAbsent(key, k -> new long[1])[0]++;
            if (order.getStatus() == OrderStatus.DELIVERED) {
                dailyRevenue.merge(key, order.getTotalAmount(), BigDecimal::add);
            }
        }
        List<AdminDtos.TrendPoint> series = new ArrayList<>();
        LocalDate start = LocalDate.now(IST).minusDays(window - 1L);
        for (int i = 0; i < window; i++) {
            LocalDate date = start.plusDays(i);
            String key = date.format(DateTimeFormatter.ISO_DATE);
            long[] count = daily.get(key);
            series.add(new AdminDtos.TrendPoint(key, count == null ? 0 : count[0],
                    dailyRevenue.getOrDefault(key, BigDecimal.ZERO)));
        }
        BigDecimal revenue = orderRepository.sumRevenueForPharmacy(pharmacyId, List.of(OrderStatus.DELIVERED));
        List<AdminDtos.NamedCount> topProducts = orderRepository
                .topProductsForPharmacy(pharmacyId, org.springframework.data.domain.PageRequest.of(0, 5))
                .stream()
                .map(row -> new AdminDtos.NamedCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
        long delivered = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        long lowStock = inventoryRepository.findLowStock(pharmacyId).size();
        return new PharmacyAnalytics(window, series, revenue,
                delivered == 0 ? BigDecimal.ZERO
                        : revenue.divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP),
                delivered, cancelled, lowStock, topProducts, forecast(pharmacyId));
    }

    private List<DemandForecast> forecast(Long pharmacyId) {
        List<Object[]> top = orderRepository.topProductsForPharmacy(pharmacyId,
                org.springframework.data.domain.PageRequest.of(0, 5));
        List<DemandForecast> result = new ArrayList<>();
        for (Object[] row : top) {
            long total = ((Number) row[1]).longValue();
            double avgDaily = total / 30.0;
            result.add(new DemandForecast((String) row[0], total,
                    Math.max(0, (int) Math.round(avgDaily * 7 * 1.1)),
                    "Weighted 30-day sales average with 10% demand buffer"));
        }
        return result;
    }

    public record PharmacyAnalytics(int days, List<AdminDtos.TrendPoint> series, BigDecimal revenue,
                                    BigDecimal averageOrderValue, long deliveredOrders, long cancelledOrders,
                                    long lowStockItems, List<AdminDtos.NamedCount> topProducts,
                                    List<DemandForecast> forecast) {
    }

    public record DemandForecast(String medicine, long unitsLast30Days, int suggestedNext7Days, String method) {
    }
}
