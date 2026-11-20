package com.medidrop.security;

import com.medidrop.delivery.DeliveryAssignmentRepository;
import com.medidrop.delivery.DeliveryPartnerRepository;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PharmacyRepository pharmacyRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            var authentication = authenticate(accessor);
            if (authentication == null) {
                throw new AccessDeniedException("A valid access token is required for realtime updates");
            }
            accessor.setUser(authentication);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (!canSubscribe(accessor)) {
                throw new AccessDeniedException("You are not allowed to subscribe to this channel");
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            // The browser only reads server events; clients must never publish to the broker.
            throw new AccessDeniedException("Client publishing is not allowed");
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            if (!"access".equals(claims.get("type", String.class))) {
                return null;
            }
            Long userId = Long.valueOf(claims.getSubject());
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isEnabled() || user.isLocked()) {
                return null;
            }
            UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(),
                    user.getPasswordHash(), user.getRole(), user.isEnabled(), !user.isLocked());
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean canSubscribe(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return false;
        }

        if (destination.startsWith("/topic/tracking/")) {
            String trackingKey = destination.substring("/topic/tracking/".length());
            return orderRepository.findByTrackingKey(trackingKey)
                    .map(order -> canTrack(principal, order))
                    .orElse(false);
        }

        if (destination.startsWith("/topic/delivery/")) {
            if (principal.getRole() != Role.DELIVERY) {
                return false;
            }
            try {
                Long partnerId = Long.valueOf(destination.substring("/topic/delivery/".length()));
                return deliveryPartnerRepository.findByUserId(principal.getUserId())
                        .map(partner -> partner.getId().equals(partnerId))
                        .orElse(false);
            } catch (NumberFormatException exception) {
                return false;
            }
        }

        return false;
    }

    private boolean canTrack(UserPrincipal principal, Order order) {
        return switch (principal.getRole()) {
            case ADMIN -> true;
            case CUSTOMER -> principal.getUserId().equals(order.getCustomerId());
            case PHARMACY -> pharmacyRepository.findByOwnerUserId(principal.getUserId())
                    .map(pharmacy -> pharmacy.getId().equals(order.getPharmacyId()))
                    .orElse(false);
            case DELIVERY -> deliveryAssignmentRepository.findByOrderId(order.getId())
                    .flatMap(assignment -> deliveryPartnerRepository.findById(assignment.getPartnerId()))
                    .map(partner -> partner.getUserId().equals(principal.getUserId()))
                    .orElse(false);
        };
    }
}
