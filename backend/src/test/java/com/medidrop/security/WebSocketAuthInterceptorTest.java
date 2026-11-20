package com.medidrop.security;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.delivery.DeliveryAssignment;
import com.medidrop.delivery.DeliveryAssignmentRepository;
import com.medidrop.delivery.DeliveryPartner;
import com.medidrop.delivery.DeliveryPartnerRepository;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PharmacyRepository pharmacyRepository;
    @Mock private DeliveryPartnerRepository deliveryPartnerRepository;
    @Mock private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(jwtService, userRepository, orderRepository,
                pharmacyRepository, deliveryPartnerRepository, deliveryAssignmentRepository);
    }

    @Test
    void rejectsConnectWithoutAccessToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = message(accessor);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void authenticatesConnectAndAllowsOwnerTrackingSubscription() {
        User user = user(7L, Role.CUSTOMER);
        Claims claims = claims(7L);
        when(jwtService.parse("token")).thenReturn(claims);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> connectMessage = message(connect);
        interceptor.preSend(connectMessage, channel);
        assertNotNull(connect.getUser());

        Order order = mock(Order.class);
        when(order.getCustomerId()).thenReturn(7L);
        when(orderRepository.findByTrackingKey("tracking-key")).thenReturn(Optional.of(order));

        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/tracking/tracking-key");
        subscribe.setUser(connect.getUser());
        assertNotNull(interceptor.preSend(message(subscribe), channel));
    }

    @Test
    void rejectsAnotherCustomersTrackingSubscription() {
        Order order = mock(Order.class);
        when(order.getCustomerId()).thenReturn(8L);
        when(orderRepository.findByTrackingKey("tracking-key")).thenReturn(Optional.of(order));

        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/tracking/tracking-key");
        subscribe.setUser(authentication(7L, Role.CUSTOMER));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(subscribe), channel));
    }

    @Test
    void rejectsTrackingSubscriptionForSuspendedPharmacy() {
        Order order = mock(Order.class);
        when(orderRepository.findByTrackingKey("tracking-key")).thenReturn(Optional.of(order));

        Pharmacy pharmacy = mock(Pharmacy.class);
        when(pharmacy.getStatus()).thenReturn(ApprovalStatus.SUSPENDED);
        when(pharmacyRepository.findByOwnerUserId(7L)).thenReturn(Optional.of(pharmacy));

        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/tracking/tracking-key");
        subscribe.setUser(authentication(7L, Role.PHARMACY));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(subscribe), channel));
    }

    @Test
    void rejectsDeliveryAndTrackingChannelsForSuspendedRider() {
        DeliveryPartner partner = mock(DeliveryPartner.class);
        when(partner.getStatus()).thenReturn(ApprovalStatus.SUSPENDED);
        when(deliveryPartnerRepository.findByUserId(7L)).thenReturn(Optional.of(partner));

        StompHeaderAccessor delivery = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        delivery.setDestination("/topic/delivery/4");
        delivery.setUser(authentication(7L, Role.DELIVERY));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(delivery), channel));

        Order order = mock(Order.class);
        when(order.getId()).thenReturn(42L);
        when(orderRepository.findByTrackingKey("tracking-key")).thenReturn(Optional.of(order));
        DeliveryAssignment assignment = mock(DeliveryAssignment.class);
        when(assignment.getPartnerId()).thenReturn(4L);
        when(deliveryAssignmentRepository.findByOrderId(42L)).thenReturn(Optional.of(assignment));
        when(deliveryPartnerRepository.findById(4L)).thenReturn(Optional.of(partner));

        StompHeaderAccessor tracking = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        tracking.setDestination("/topic/tracking/tracking-key");
        tracking.setUser(authentication(7L, Role.DELIVERY));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(tracking), channel));
    }

    @Test
    void rejectsDeliveryChannelForCustomerAndClientPublishing() {
        StompHeaderAccessor delivery = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        delivery.setDestination("/topic/delivery/1");
        delivery.setUser(authentication(7L, Role.CUSTOMER));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(delivery), channel));

        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/tracking/tracking-key");
        send.setUser(authentication(7L, Role.CUSTOMER));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(send), channel));
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Claims claims(Long userId) {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        return claims;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@medidrop.test");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setEnabled(true);
        user.setLocked(false);
        return user;
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId, Role role) {
        UserPrincipal principal = new UserPrincipal(userId, "user" + userId + "@medidrop.test",
                "hash", role, true, true);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
