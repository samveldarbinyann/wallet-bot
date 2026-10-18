package com.example.walletbot.addressbook.application;

import com.example.walletbot.addressbook.domain.AddressBookEntity;
import com.example.walletbot.addressbook.domain.AddressBookRepository;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.shared.util.AddressNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressBookService {

    private final AddressBookRepository addressBookRepository;
    private final BlockchainServiceFactory blockchainFactory;
    private final AddressNormalizer addressNormalizer;

    @Value("${address-book.max-entries-per-user:500}")
    private int maxEntriesPerUser;

    private static String normalizeLabel(String label) {
        return label != null && !label.isBlank() ? label.trim() : null;
    }

    @Transactional(readOnly = true)
    public List<AddressBookEntity> listEntries(Long userId) {
        return addressBookRepository.findByUserIdOrderByLastUsedDesc(userId);
    }

    @Transactional
    public void recordUsage(Long userId, BlockchainNetwork network, String address) {
        String canonical = addressNormalizer.canonical(network, address);
        try {
            addressBookRepository.findByUserIdAndNetworkAndAddress(userId, network, canonical)
                    .ifPresentOrElse(
                            entry -> {
                                entry.setLastUsed(LocalDateTime.now());
                                addressBookRepository.save(entry);
                            },
                            () -> addressBookRepository.save(newEntry(userId, network, canonical)));
        } catch (Exception e) {
            log.debug("Skipped address usage record for userId={}, net={}: {}", userId, network, e.toString());
        }
    }

    @Transactional
    public AddressBookEntity saveEntry(Long userId, BlockchainNetwork network, String address, String label) {
        if (!blockchainFactory.getService(network).isValidAddress(address)) {
            throw new BadRequestException("error.invalid_address_for_network", network.name());
        }

        String canonical = addressNormalizer.canonical(network, address);

        AddressBookEntity entry = addressBookRepository
                .findByUserIdAndNetworkAndAddress(userId, network, canonical)
                .orElse(null);

        if (entry == null) {
            if (addressBookRepository.countByUserId(userId) >= maxEntriesPerUser) {
                throw new BadRequestException("error.addressbook_limit_reached", maxEntriesPerUser);
            }
            entry = newEntry(userId, network, canonical);
        }

        entry.setLabel(normalizeLabel(label));
        entry.setLastUsed(LocalDateTime.now());
        return addressBookRepository.save(entry);
    }

    @Transactional
    public AddressBookEntity updateLabel(Long userId, Long entryId, String label) {
        AddressBookEntity entry = requireOwnedEntry(userId, entryId);
        entry.setLabel(normalizeLabel(label));
        return addressBookRepository.save(entry);
    }

    @Transactional
    public void deleteEntry(Long userId, Long entryId) {
        addressBookRepository.delete(requireOwnedEntry(userId, entryId));
    }

    private AddressBookEntity requireOwnedEntry(Long userId, Long entryId) {
        return addressBookRepository.findById(entryId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Address book entry not found"));
    }

    private AddressBookEntity newEntry(Long userId, BlockchainNetwork network, String canonicalAddress) {
        AddressBookEntity entry = new AddressBookEntity();
        entry.setUserId(userId);
        entry.setNetwork(network);
        entry.setAddress(canonicalAddress);
        return entry;
    }
}
