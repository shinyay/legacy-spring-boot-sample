package com.techbookstore.app.controller;

import com.techbookstore.app.entity.Book;
import com.techbookstore.app.entity.Inventory;
import com.techbookstore.app.repository.InventoryRepository;
import com.techbookstore.app.service.AdvancedInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private AdvancedInventoryService advancedInventoryService;
    @InjectMocks private InventoryController inventoryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController).build();
    }

    @Test
    void shouldDebitStoreAndSetSaleDate_whenStockIsSufficient() throws Exception {
        Inventory inventory = inventory(10, 5, 3);
        when(inventoryRepository.findByBookId(2L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        mockMvc.perform(post("/api/v1/inventory/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":2,\"quantity\":4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storeStock").value(6))
            .andExpect(jsonPath("$.warehouseStock").value(5))
            .andExpect(jsonPath("$.reservedCount").value(3))
            .andExpect(jsonPath("$.availableStock").value(8));

        assertEquals(LocalDate.now(), inventory.getLastSoldDate());
        verify(inventoryRepository).save(inventory);
    }

    @ParameterizedTest
    @CsvSource({"2, 8, 0, 5", "10, 0, 8, 3"})
    void shouldRejectSaleWithoutMutation_whenStoreOrAvailabilityIsInsufficient(
            int store, int warehouse, int reserved, int quantity) throws Exception {
        Inventory inventory = inventory(store, warehouse, reserved);
        when(inventoryRepository.findByBookId(2L)).thenReturn(Optional.of(inventory));

        mockMvc.perform(post("/api/v1/inventory/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":2,\"quantity\":" + quantity + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(""));

        assertAll(
            () -> assertEquals(store, inventory.getStoreStock()),
            () -> assertEquals(warehouse, inventory.getWarehouseStock()),
            () -> assertEquals(reserved, inventory.getReservedCount()),
            () -> assertNull(inventory.getLastSoldDate())
        );
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldReturnNotFound_whenSellingBookWithoutInventory() throws Exception {
        when(inventoryRepository.findByBookId(2L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/inventory/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":2,\"quantity\":4}"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));

        verify(inventoryRepository, never()).save(any());
    }

    private Inventory inventory(int store, int warehouse, int reserved) {
        Book book = new Book("9781234567890", "Java");
        book.setId(2L);
        Inventory inventory = new Inventory(book);
        inventory.setStoreStock(store);
        inventory.setWarehouseStock(warehouse);
        inventory.setReservedCount(reserved);
        return inventory;
    }
}
