package com.chengxinjian.media;

import com.chengxinjian.auth.AdminPrincipal;
import com.chengxinjian.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.chengxinjian.media.MediaModels.*;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
    private final MediaService service;
    public MediaController(MediaService service) { this.service=service; }
    @PostMapping("/uploads") ApiResponse<Ticket> create(@AuthenticationPrincipal Object actor,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Upload input) {
        return ApiResponse.ok(service.create(actor,key,input));
    }
    @PostMapping("/uploads/{id}/complete") ApiResponse<Asset> complete(@AuthenticationPrincipal Object actor,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody @Valid Complete input) {
        return ApiResponse.ok(service.complete(actor,id,key,input));
    }
    @GetMapping("/assets/{id}") ApiResponse<Asset> get(@AuthenticationPrincipal Object actor,@PathVariable String id) { return ApiResponse.ok(service.get(actor,id)); }
    @PostMapping("/admin/assets/{id}/preview") ApiResponse<Preview> preview(@AuthenticationPrincipal AdminPrincipal admin,@PathVariable String id) { return ApiResponse.ok(service.preview(admin,id)); }
}
