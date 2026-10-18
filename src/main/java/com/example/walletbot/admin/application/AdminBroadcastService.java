package com.example.walletbot.admin.application;

import com.example.walletbot.notification.application.BroadcastMapper;
import com.example.walletbot.notification.application.BroadcastService;
import com.example.walletbot.notification.domain.BroadcastDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AdminBroadcastService {

    private final BroadcastService broadcastService;
    private final BroadcastMapper broadcastMapper;

    public Page<BroadcastDto> list(int page, int size) {
        return broadcastService.list(page, size).map(broadcastMapper::toDto);
    }

    public BroadcastDto get(Long id) {
        return broadcastMapper.toDto(broadcastService.get(id));
    }

    public String uploadImage(MultipartFile file) {
        return broadcastService.uploadImage(file);
    }

    public BroadcastDto create(Long adminId, String type, String titleRu, String titleEn,
                               String bodyRu, String bodyEn, String target, String channels,
                               String imageUrl) {
        return broadcastMapper.toDto(broadcastService.create(
                adminId, type, titleRu, titleEn, bodyRu, bodyEn, target, channels, imageUrl));
    }

    public BroadcastDto send(Long id) {
        return broadcastMapper.toDto(broadcastService.send(id));
    }

    public void delete(Long id) {
        broadcastService.delete(id);
    }
}
