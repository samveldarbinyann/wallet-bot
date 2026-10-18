package com.example.walletbot.admin.application;

import com.example.walletbot.admin.domain.AdminUserDto;
import com.example.walletbot.user.domain.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {
    @Mapping(target = "maskedEmail", expression = "java(user.getEmail() != null ? com.example.walletbot.shared.util.EmailMasker.mask(user.getEmail()) : null)")
    AdminUserDto toDto(UserEntity user);
}
