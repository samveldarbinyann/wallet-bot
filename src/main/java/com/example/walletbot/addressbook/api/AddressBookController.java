package com.example.walletbot.addressbook.api;

import com.example.walletbot.addressbook.api.dto.AddressBookEntryDto;
import com.example.walletbot.addressbook.api.dto.SaveAddressBookEntryRequest;
import com.example.walletbot.addressbook.api.dto.UpdateAddressBookLabelRequest;
import com.example.walletbot.addressbook.application.AddressBookMapper;
import com.example.walletbot.addressbook.application.AddressBookService;
import com.example.walletbot.platform.web.security.CurrentUser;
import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.user.domain.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/miniapp/address-book")
@RequiredArgsConstructor
public class AddressBookController {

    private final AddressBookService addressBookService;
    private final NetworkParser networkParser;
    private final AddressBookMapper addressBookMapper;
    private final AddressBookRestMapper addressBookRestMapper;

    @GetMapping
    public List<AddressBookEntryDto> list(@CurrentUser UserEntity user) {
        return addressBookService.listEntries(user.getId()).stream()
                .map(e -> addressBookRestMapper.toRestDto(addressBookMapper.toDto(e)))
                .toList();
    }

    @PostMapping
    public AddressBookEntryDto save(@CurrentUser UserEntity user,
                                    @Valid @RequestBody SaveAddressBookEntryRequest body) {
        BlockchainNetwork network = networkParser.parseStrict(body.network());
        String address = body.address().trim();
        var entity = addressBookService.saveEntry(user.getId(), network, address, body.label());
        return addressBookRestMapper.toRestDto(addressBookMapper.toDto(entity));
    }

    @PatchMapping("/{entryId}/label")
    public AddressBookEntryDto updateLabel(@CurrentUser UserEntity user,
                                           @PathVariable Long entryId,
                                           @Valid @RequestBody UpdateAddressBookLabelRequest body) {
        var entity = addressBookService.updateLabel(user.getId(), entryId, body.label());
        return addressBookRestMapper.toRestDto(addressBookMapper.toDto(entity));
    }

    @DeleteMapping("/{entryId}")
    public void delete(@CurrentUser UserEntity user, @PathVariable Long entryId) {
        addressBookService.deleteEntry(user.getId(), entryId);
    }
}