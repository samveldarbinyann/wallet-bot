package com.example.walletbot.user.application;

import com.example.walletbot.user.domain.UserDto;
import com.example.walletbot.user.domain.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "maskedEmail", expression = "java(user.getEmail() != null ? com.example.walletbot.shared.util.EmailMasker.mask(user.getEmail()) : null)")
    @Mapping(target = "emailVerified", expression = "java(user.getEmailVerifiedAt() != null)")
    UserDto toDto(UserEntity user);
}
