package com.medidrop.support;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping
    public ApiResponse<List<SupportDtos.TicketRow>> list() {
        return ApiResponse.ok(supportService.customerTickets(CurrentUser.id()));
    }

    @PostMapping
    public ApiResponse<SupportDtos.TicketRow> create(@Valid @RequestBody SupportDtos.CreateRequest request) {
        return ApiResponse.ok("Support request submitted", supportService.create(CurrentUser.id(), request));
    }
}
