package com.medidrop.cart;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartDtos.CartView> view() {
        return ApiResponse.ok(cartService.view(CurrentUser.id()));
    }

    @PostMapping("/items")
    public ApiResponse<CartDtos.CartView> add(@Valid @RequestBody CartDtos.AddItemRequest request) {
        return ApiResponse.ok("Added to cart",
                cartService.addItem(CurrentUser.id(), request.inventoryId(), request.quantity()));
    }

    @PatchMapping("/items/{id}")
    public ApiResponse<CartDtos.CartView> update(@PathVariable Long id,
                                                 @Valid @RequestBody CartDtos.UpdateItemRequest request) {
        return ApiResponse.ok(cartService.updateItem(CurrentUser.id(), id, request.quantity()));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<CartDtos.CartView> remove(@PathVariable Long id) {
        return ApiResponse.ok(cartService.removeItem(CurrentUser.id(), id));
    }

    @DeleteMapping
    public ApiResponse<CartDtos.CartView> clear() {
        return ApiResponse.ok("Cart cleared", cartService.clear(CurrentUser.id()));
    }
}
