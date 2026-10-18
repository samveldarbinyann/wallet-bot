package com.example.walletbot.addressbook.application;

import com.example.walletbot.addressbook.domain.AddressBookDto;
import com.example.walletbot.addressbook.domain.AddressBookEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressBookMapper {
    AddressBookDto toDto(AddressBookEntity entity);
}
