package com.example.walletbot.addressbook.application;

import com.example.walletbot.addressbook.domain.AddressBookEntity;
import com.example.walletbot.addressbook.domain.AddressBookRepository;
import com.example.walletbot.platform.blockchain.BlockchainService;
import com.example.walletbot.platform.blockchain.BlockchainServiceFactory;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.BadRequestException;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.shared.util.AddressNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressBookServiceTest {

    @Mock
    AddressBookRepository repo;
    @Mock
    BlockchainServiceFactory blockchainFactory;
    @Mock
    AddressNormalizer addressNormalizer;
    @Mock
    BlockchainService blockchainService;

    @InjectMocks
    AddressBookService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxEntriesPerUser", 500);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addressNormalizer.canonical(any(), any())).thenAnswer(inv -> "CANON:" + inv.getArgument(1));
        when(blockchainFactory.getService(any())).thenReturn(blockchainService);
        when(blockchainService.isValidAddress(any())).thenReturn(true);
    }

    @Test
    void saveEntry_invalidAddress_throws() {
        when(blockchainService.isValidAddress("0xbad")).thenReturn(false);

        assertThatThrownBy(() -> service.saveEntry(1L, BlockchainNetwork.ETH, "0xbad", "label"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.invalid_address_for_network");
    }

    @Test
    void saveEntry_newEntryUnderLimit_persists() {
        when(repo.findByUserIdAndNetworkAndAddress(1L, BlockchainNetwork.ETH, "CANON:0xabc"))
                .thenReturn(Optional.empty());
        when(repo.countByUserId(1L)).thenReturn(3L);

        AddressBookEntity saved = service.saveEntry(1L, BlockchainNetwork.ETH, "0xabc", "  My wallet ");

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAddress()).isEqualTo("CANON:0xabc");
        assertThat(saved.getLabel()).isEqualTo("My wallet");
        assertThat(saved.getLastUsed()).isNotNull();
    }

    @Test
    void saveEntry_limitReached_throws() {
        when(repo.findByUserIdAndNetworkAndAddress(any(), any(), any())).thenReturn(Optional.empty());
        when(repo.countByUserId(1L)).thenReturn(500L);

        assertThatThrownBy(() -> service.saveEntry(1L, BlockchainNetwork.ETH, "0xabc", "x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.addressbook_limit_reached");
    }

    @Test
    void saveEntry_existingEntry_updatesLabelWithoutCountCheck() {
        AddressBookEntity existing = new AddressBookEntity();
        existing.setUserId(1L);
        existing.setNetwork(BlockchainNetwork.ETH);
        existing.setAddress("CANON:0xabc");
        when(repo.findByUserIdAndNetworkAndAddress(1L, BlockchainNetwork.ETH, "CANON:0xabc"))
                .thenReturn(Optional.of(existing));

        AddressBookEntity saved = service.saveEntry(1L, BlockchainNetwork.ETH, "0xabc", "Renamed");

        assertThat(saved.getLabel()).isEqualTo("Renamed");
        verify(repo, never()).countByUserId(any());
    }

    @Test
    void saveEntry_blankLabel_storedAsNull() {
        when(repo.findByUserIdAndNetworkAndAddress(any(), any(), any())).thenReturn(Optional.empty());
        when(repo.countByUserId(1L)).thenReturn(0L);

        AddressBookEntity saved = service.saveEntry(1L, BlockchainNetwork.ETH, "0xabc", "   ");

        assertThat(saved.getLabel()).isNull();
    }

    @Test
    void updateLabel_wrongOwner_throwsNotFound() {
        AddressBookEntity entry = new AddressBookEntity();
        entry.setUserId(2L);
        when(repo.findById(7L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.updateLabel(1L, 7L, "x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateLabel_validOwner_updates() {
        AddressBookEntity entry = new AddressBookEntity();
        entry.setUserId(1L);
        when(repo.findById(7L)).thenReturn(Optional.of(entry));

        AddressBookEntity saved = service.updateLabel(1L, 7L, " New ");

        assertThat(saved.getLabel()).isEqualTo("New");
    }

    @Test
    void deleteEntry_wrongOwner_throwsNotFound() {
        AddressBookEntity entry = new AddressBookEntity();
        entry.setUserId(2L);
        when(repo.findById(7L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.deleteEntry(1L, 7L))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void deleteEntry_validOwner_deletes() {
        AddressBookEntity entry = new AddressBookEntity();
        entry.setUserId(1L);
        when(repo.findById(7L)).thenReturn(Optional.of(entry));

        service.deleteEntry(1L, 7L);

        verify(repo).delete(entry);
    }

    @Test
    void recordUsage_existingEntry_updatesLastUsed() {
        AddressBookEntity existing = new AddressBookEntity();
        when(repo.findByUserIdAndNetworkAndAddress(1L, BlockchainNetwork.ETH, "CANON:0xabc"))
                .thenReturn(Optional.of(existing));

        service.recordUsage(1L, BlockchainNetwork.ETH, "0xabc");

        assertThat(existing.getLastUsed()).isNotNull();
        verify(repo).save(existing);
    }

    @Test
    void recordUsage_newEntry_inserts() {
        when(repo.findByUserIdAndNetworkAndAddress(any(), any(), any())).thenReturn(Optional.empty());

        service.recordUsage(1L, BlockchainNetwork.ETH, "0xabc");

        verify(repo).save(any(AddressBookEntity.class));
    }
}
