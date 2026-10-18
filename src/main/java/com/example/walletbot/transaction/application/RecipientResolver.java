package com.example.walletbot.transaction.application;

import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.ProfilePhotoPort;
import com.example.walletbot.user.domain.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RecipientResolver {

    private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9_]{5,32}$");

    private final UserService userService;
    private final ProfilePhotoPort profilePhotoPort;

    public Optional<byte[]> fetchPhoto(String rawUsername) {
        return findRecipient(normalize(rawUsername))
                .flatMap(recipient -> profilePhotoPort.fetchPhoto(recipient.getTelegramId()));
    }

    public RecipientLookup lookup(String rawUsername, UserEntity caller) {
        String handle = normalize(rawUsername);
        Optional<UserEntity> match = findRecipient(handle);
        if (match.isEmpty()) {
            return new RecipientLookup(false, handle, null, false);
        }
        UserEntity recipient = match.get();
        boolean self = recipient.getId().equals(caller.getId());
        return new RecipientLookup(true, recipient.getUsername(), displayName(recipient), self);
    }

    public UserEntity resolve(String rawUsername) {
        return findRecipient(normalize(rawUsername))
                .orElseThrow(() -> new BadRequestException("error.recipient_not_found"));
    }

    private Optional<UserEntity> findRecipient(String handle) {
        return handle == null ? Optional.empty() : userService.findByUsername(handle);
    }

    public static String normalize(String rawUsername) {
        if (rawUsername == null) {
            return null;
        }
        String handle = rawUsername.trim();
        if (handle.startsWith("@")) {
            handle = handle.substring(1);
        }
        return HANDLE.matcher(handle).matches() ? handle : null;
    }

    private static String displayName(UserEntity user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? null : name;
    }

    public record RecipientLookup(boolean found, String username, String displayName, boolean self) {
    }
}