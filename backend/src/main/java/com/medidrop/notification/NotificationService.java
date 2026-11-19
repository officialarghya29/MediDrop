package com.medidrop.notification;

import com.medidrop.common.exception.ApiException;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @Transactional
    public void notifyUser(Long userId, String type, String title, String body, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        repository.save(n);
        userRepository.findById(userId).ifPresent(user ->
                sendEmailQuietly(user.getEmail(), title, body));
    }

    private void sendEmailQuietly(String email, String subject, String body) {
        try {
            emailSender.send(email, "[MediDrop] " + subject, body);
        } catch (RuntimeException ex) {
            // Notification failures must never roll back the business transaction.
            log.warn("Notification email failed for {}: {}", email, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> list(Long userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = repository.findById(notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw ApiException.forbidden("Notification does not belong to you");
        }
        n.setRead(true);
        repository.save(n);
    }

    public void welcome(User user) {
        notifyUser(user.getId(), "ACCOUNT", "Welcome to MediDrop",
                "Your account is ready. Add a delivery address and upload a prescription to get started.", null);
    }
}
