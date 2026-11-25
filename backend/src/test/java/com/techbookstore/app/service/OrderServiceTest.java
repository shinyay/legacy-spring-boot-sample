package com.techbookstore.app.service;

import com.techbookstore.app.entity.Book;
import com.techbookstore.app.entity.Inventory;
import com.techbookstore.app.entity.Order;
import com.techbookstore.app.entity.Order.OrderStatus;
import com.techbookstore.app.entity.OrderItem;
import com.techbookstore.app.exception.InsufficientInventoryException;
import com.techbookstore.app.exception.InvalidOrderStatusException;
import com.techbookstore.app.exception.InventoryNotFoundException;
import com.techbookstore.app.repository.BookRepository;
import com.techbookstore.app.repository.InventoryRepository;
import com.techbookstore.app.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private BookRepository bookRepository;
    @Mock private InventoryRepository inventoryRepository;
    @InjectMocks private OrderService orderService;

    private Order order;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        Book book = new Book("9781234567890", "Java");
        book.setId(2L);
        order = new Order(Order.OrderType.ONLINE, Order.PaymentMethod.CASH);
        order.setId(1L);
        order.setOrderItems(Collections.singletonList(new OrderItem(order, book, 4, BigDecimal.TEN)));
        inventory = new Inventory(book);
        inventory.setStoreStock(10);
        inventory.setWarehouseStock(5);
        inventory.setReservedCount(3);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
    }

    @Test
    void shouldDebitOnlyStoreStock_whenConfirmingPendingOrder() {
        when(inventoryRepository.findByBookId(2L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderService.confirmOrder(1L);

        assertAll(
            () -> assertEquals(OrderStatus.CONFIRMED, result.getStatus()),
            () -> assertNotNull(result.getConfirmedDate()),
            () -> assertEquals(6, inventory.getStoreStock()),
            () -> assertEquals(5, inventory.getWarehouseStock()),
            () -> assertEquals(3, inventory.getReservedCount())
        );
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void shouldRejectConfirmation_whenReservationsLeaveInsufficientAvailability() {
        inventory.setReservedCount(12);
        when(inventoryRepository.findByBookId(2L)).thenReturn(Optional.of(inventory));

        assertThrows(InsufficientInventoryException.class, () -> orderService.confirmOrder(1L));

        assertEquals(10, inventory.getStoreStock());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectConfirmation_whenInventoryDoesNotExist() {
        when(inventoryRepository.findByBookId(2L)).thenReturn(Optional.empty());

        assertThrows(InventoryNotFoundException.class, () -> orderService.confirmOrder(1L));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectConfirmation_whenOrderIsAlreadyConfirmed() {
        order.setStatus(OrderStatus.CONFIRMED);

        assertThrows(InvalidOrderStatusException.class, () -> orderService.confirmOrder(1L));

        verifyNoInteractions(inventoryRepository);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @MethodSource("statusTransitions")
    void shouldPreserveTransitionRules_whenUpdatingStatus(OrderStatus current, OrderStatus next, boolean allowed) {
        order.setStatus(current);

        if (!allowed) {
            assertThrows(InvalidOrderStatusException.class, () -> orderService.updateOrderStatus(1L, next));
            assertEquals(current, order.getStatus());
            verify(orderRepository, never()).save(any());
            return;
        }

        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderService.updateOrderStatus(1L, next);

        assertEquals(next, result.getStatus());
        assertEquals(next == OrderStatus.CONFIRMED, result.getConfirmedDate() != null);
        assertEquals(next == OrderStatus.SHIPPED, result.getShippedDate() != null);
        assertEquals(next == OrderStatus.DELIVERED, result.getDeliveredDate() != null);
        verify(orderRepository).save(order);
    }

    static Stream<Arguments> statusTransitions() {
        String[][] destinations = {
            {"PENDING", "PENDING,CONFIRMED,CANCELLED"},
            {"CONFIRMED", "CONFIRMED,PICKING,CANCELLED"},
            {"PICKING", "PICKING,SHIPPED,CANCELLED"},
            {"SHIPPED", "SHIPPED,DELIVERED"},
            {"DELIVERED", "DELIVERED"},
            {"CANCELLED", "CANCELLED"}
        };
        return Arrays.stream(destinations).flatMap(row -> Arrays.stream(OrderStatus.values())
            .map(next -> Arguments.of(OrderStatus.valueOf(row[0]), next,
                Arrays.asList(row[1].split(",")).contains(next.name()))));
    }
}
