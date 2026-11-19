package com.medidrop.user;

import com.medidrop.audit.AuditService;
import com.medidrop.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserDtos.ProfileResponse profile(Long userId) {
        return UserDtos.ProfileResponse.of(user(userId));
    }

    @Transactional
    public UserDtos.ProfileResponse updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        User user = user(userId);
        user.setFullName(request.fullName().trim());
        if (request.phone() != null && !request.phone().isBlank()) {
            userRepository.findByPhone(request.phone())
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> {
                        throw ApiException.conflict("This mobile number is linked to another account");
                    });
            if (!request.phone().equals(user.getPhone())) {
                user.setPhone(request.phone());
                user.setPhoneVerified(false);
            }
        }
        user.setAvatarUrl(request.avatarUrl());
        userRepository.save(user);
        return UserDtos.ProfileResponse.of(user);
    }

    @Transactional(readOnly = true)
    public List<UserDtos.AddressResponse> addresses(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .stream().map(UserDtos.AddressResponse::of).toList();
    }

    @Transactional
    public UserDtos.AddressResponse addAddress(Long userId, UserDtos.AddressRequest request) {
        Address address = new Address();
        apply(address, userId, request);
        if (request.defaultAddress() || addressRepository.countByUserId(userId) == 0) {
            clearDefaults(userId);
            address.setDefaultAddress(true);
        }
        addressRepository.save(address);
        return UserDtos.AddressResponse.of(address);
    }

    @Transactional
    public UserDtos.AddressResponse updateAddress(Long userId, Long addressId, UserDtos.AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("Address not found"));
        apply(address, userId, request);
        if (request.defaultAddress()) {
            clearDefaults(userId);
            address.setDefaultAddress(true);
        }
        addressRepository.save(address);
        return UserDtos.AddressResponse.of(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("Address not found"));
        addressRepository.delete(address);
        auditService.recordForUser(userId, "CUSTOMER", "ADDRESS_DELETED", "Address", addressId, null);
    }

    @Transactional
    public void requestDeletion(Long userId, String reason) {
        User user = user(userId);
        user.setDeletionRequestedAt(Instant.now());
        user.setEnabled(false);
        userRepository.save(user);
        auditService.recordForUser(userId, "CUSTOMER", "ACCOUNT_DELETION_REQUESTED", "User", userId, reason);
    }

    private void apply(Address address, Long userId, UserDtos.AddressRequest request) {
        address.setUserId(userId);
        address.setLabel(request.label() == null || request.label().isBlank() ? "Home" : request.label());
        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
    }

    private void clearDefaults(Long userId) {
        List<Address> existing = addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);
        existing.forEach(a -> a.setDefaultAddress(false));
        addressRepository.saveAll(existing);
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Account not found"));
    }
}
