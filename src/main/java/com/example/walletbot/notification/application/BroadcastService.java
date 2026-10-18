package com.example.walletbot.notification.application;

import com.example.walletbot.notification.domain.BroadcastEntity;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastStatus;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastTarget;
import com.example.walletbot.notification.domain.BroadcastEntity.BroadcastType;
import com.example.walletbot.notification.domain.BroadcastRepository;
import com.example.walletbot.notification.infrastructure.BroadcastImageStorage;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastImageStorage imageStorage;
    private final BroadcastDispatcher broadcastDispatcher;

    @Transactional(readOnly = true)
    public Page<BroadcastEntity> list(int page, int size) {
        return broadcastRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 50)));
    }

    @Transactional(readOnly = true)
    public BroadcastEntity get(Long id) {
        return broadcastRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Broadcast not found"));
    }

    private static BroadcastType parseType(String type) {
        try {
            return BroadcastType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("error.broadcast_unknown_type", type);
        }
    }

    private static BroadcastTarget parseTarget(String target) {
        if (target == null) {
            return BroadcastTarget.ALL;
        }
        try {
            return BroadcastTarget.valueOf(target.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("error.broadcast_unknown_target", target);
        }
    }

    public String uploadImage(MultipartFile file) {
        return imageStorage.save(file);
    }

    @Transactional
    public BroadcastEntity create(Long adminUserId, String type, String titleRu, String titleEn,
                                  String bodyRu, String bodyEn, String target, String channels,
                                  String imageUrl) {
        BroadcastEntity entity = new BroadcastEntity();
        entity.setCreatedBy(adminUserId);
        entity.setType(parseType(type));
        entity.setTitleRu(titleRu);
        entity.setTitleEn(titleEn);
        entity.setBodyRu(bodyRu);
        entity.setBodyEn(bodyEn);
        entity.setTarget(parseTarget(target));
        entity.setChannels(channels != null ? channels : "IN_APP,BOT");
        entity.setImageUrl(imageUrl);
        return broadcastRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        BroadcastEntity broadcast = broadcastRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Broadcast not found"));
        if (broadcast.getStatus() == BroadcastStatus.SENDING) {
            throw new BadRequestException("error.broadcast_cannot_delete_sending");
        }
        if (broadcast.getImageUrl() != null) {
            imageStorage.deleteOne(broadcast.getImageUrl());
        }
        broadcastRepository.delete(broadcast);
    }

    @Transactional
    public BroadcastEntity send(Long broadcastId) {
        BroadcastEntity broadcast = broadcastRepository.findByIdForUpdate(broadcastId)
                .orElseThrow(() -> new NotFoundException("Broadcast not found"));

        if (broadcast.getStatus() == BroadcastStatus.SENDING || broadcast.getStatus() == BroadcastStatus.SENT) {
            return broadcast;
        }

        broadcast.setStatus(BroadcastStatus.SENDING);
        broadcastRepository.save(broadcast);

        broadcastDispatcher.dispatch(broadcastId);
        return broadcast;
    }
}
