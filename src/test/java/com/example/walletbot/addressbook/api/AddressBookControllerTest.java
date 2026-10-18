package com.example.walletbot.addressbook.api;

import com.example.walletbot.addressbook.application.AddressBookMapperImpl;
import com.example.walletbot.addressbook.application.AddressBookService;
import com.example.walletbot.addressbook.domain.AddressBookEntity;
import com.example.walletbot.platform.web.validation.NetworkParser;
import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.exception.NotFoundException;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AddressBookControllerTest {

    private final UserEntity currentUser = new UserEntity();
    private AddressBookService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        currentUser.setId(1L);
        service = mock(AddressBookService.class);

        AddressBookController controller = new AddressBookController(
                service, new NetworkParser(), new AddressBookMapperImpl(), new AddressBookRestMapperImpl());

        mvc = MiniAppMvc.forController(controller, currentUser);
    }

    private AddressBookEntity entry() {
        AddressBookEntity e = new AddressBookEntity();
        e.setId(7L);
        e.setUserId(1L);
        e.setNetwork(BlockchainNetwork.ETH);
        e.setAddress("0xABC");
        e.setLabel("Friend");
        e.setLastUsed(LocalDateTime.now());
        return e;
    }

    @Test
    void list_returnsEntriesAsJson() throws Exception {
        when(service.listEntries(1L)).thenReturn(List.of(entry()));

        mvc.perform(get("/api/miniapp/address-book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].network").value("ETH"))
                .andExpect(jsonPath("$[0].address").value("0xABC"));
    }

    @Test
    void save_validRequest_delegatesAndReturnsDto() throws Exception {
        String address = "0x52908400098527886E0F7030069857D2E4169EE7";
        when(service.saveEntry(eq(1L), eq(BlockchainNetwork.ETH), eq(address), eq("Friend")))
                .thenReturn(entry());

        mvc.perform(post("/api/miniapp/address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"network\":\"ETH\",\"address\":\"" + address + "\",\"label\":\"Friend\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.network").value("ETH"));

        verify(service).saveEntry(eq(1L), eq(BlockchainNetwork.ETH), eq(address), eq("Friend"));
    }

    @Test
    void save_unknownNetwork_returns400() throws Exception {
        mvc.perform(post("/api/miniapp/address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"network\":\"NOTACHAIN\",\"address\":\"0x52908400098527886E0F7030069857D2E4169EE7\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void save_blankNetwork_returns422Validation() throws Exception {
        mvc.perform(post("/api/miniapp/address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"network\":\"\",\"address\":\"0x52908400098527886E0F7030069857D2E4169EE7\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void save_malformedAddress_returns422Validation() throws Exception {
        mvc.perform(post("/api/miniapp/address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"network\":\"ETH\",\"address\":\"0xABC\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateLabel_validRequest_delegatesAndReturnsDto() throws Exception {
        AddressBookEntity renamed = entry();
        renamed.setLabel("Renamed");
        when(service.updateLabel(1L, 7L, "Renamed")).thenReturn(renamed);

        mvc.perform(patch("/api/miniapp/address-book/7/label")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Renamed"));

        verify(service).updateLabel(1L, 7L, "Renamed");
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("nope")).when(service).deleteEntry(1L, 99L);

        mvc.perform(delete("/api/miniapp/address-book/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_existing_returns200() throws Exception {
        mvc.perform(delete("/api/miniapp/address-book/7"))
                .andExpect(status().isOk());
        verify(service).deleteEntry(1L, 7L);
    }
}