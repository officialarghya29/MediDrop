package com.medidrop.notification;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<Notification>> list() {
        return ApiResponse.ok(notificationService.list(CurrentUser.id()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread() {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(CurrentUser.id())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }
}
