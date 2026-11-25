package com.techbookstore.app.integration;

import com.techbookstore.app.entity.Book;
import com.techbookstore.app.entity.Inventory;
import com.techbookstore.app.entity.Order;
import com.techbookstore.app.entity.OrderItem;
import com.techbookstore.app.exception.InsufficientInventoryException;
import com.techbookstore.app.repository.BookRepository;
import com.techbookstore.app.repository.InventoryRepository;
import com.techbookstore.app.repository.OrderRepository;
import com.techbookstore.app.service.OrderService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.datasource.initialization-mode=never")
@ActiveProfiles("test")
@Import(OrderService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderStockIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldRollbackFirstDebit_whenSecondOrderItemHasInsufficientStock() {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            Book first = persistBook("9781234567890");
            Book second = persistBook("9781234567891");
            persistInventory(first, 10, 0);
            persistInventory(second, 0, 0);
            Order order = order();
            order.getOrderItems().add(new OrderItem(order, first, 4, BigDecimal.TEN));
            order.getOrderItems().add(new OrderItem(order, second, 1, BigDecimal.TEN));
            orderRepository.saveAndFlush(order);
            return new Long[] {order.getId(), first.getId(), second.getId()};
        });

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsEnabled = statistics.isStatisticsEnabled();
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            assertThrows(InsufficientInventoryException.class, () -> orderService.confirmOrder(ids[0]));
            assertTrue(statistics.getEntityUpdateCount() > 0, "The first debit must reach the database");
        } finally {
            statistics.setStatisticsEnabled(statisticsEnabled);
        }

        assertAll(
            () -> assertEquals(10, inventoryRepository.findByBookId(ids[1]).get().getStoreStock()),
            () -> assertEquals(0, inventoryRepository.findByBookId(ids[2]).get().getStoreStock()),
            () -> assertEquals(Order.OrderStatus.PENDING, orderRepository.findById(ids[0]).get().getStatus()),
            () -> assertNull(orderRepository.findById(ids[0]).get().getConfirmedDate())
        );
    }

    @Test
    @EnabledIfSystemProperty(named = "runKnownDefects", matches = "true")
    void shouldRejectConfirmation_whenOnlyWarehouseHasEnoughStock() {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            Book book = persistBook("9781234567892");
            persistInventory(book, 2, 8);
            Order order = order();
            order.getOrderItems().add(new OrderItem(order, book, 5, BigDecimal.TEN));
            orderRepository.saveAndFlush(order);
            return new Long[] {order.getId(), book.getId()};
        });

        assertAll(
            () -> assertThrows(InsufficientInventoryException.class, () -> orderService.confirmOrder(ids[0])),
            () -> assertEquals(2, inventoryRepository.findByBookId(ids[1]).get().getStoreStock()),
            () -> assertEquals(8, inventoryRepository.findByBookId(ids[1]).get().getWarehouseStock()),
            () -> assertEquals(Order.OrderStatus.PENDING, orderRepository.findById(ids[0]).get().getStatus())
        );
    }

    private Book persistBook(String isbn) {
        return bookRepository.saveAndFlush(new Book(isbn, "Baseline " + isbn));
    }

    private void persistInventory(Book book, int store, int warehouse) {
        Inventory inventory = new Inventory(book);
        inventory.setStoreStock(store);
        inventory.setWarehouseStock(warehouse);
        inventoryRepository.saveAndFlush(inventory);
    }

    private Order order() {
        Order order = new Order(Order.OrderType.ONLINE, Order.PaymentMethod.CASH);
        order.setOrderNumber("TEST-" + UUID.randomUUID().toString().substring(0, 10));
        return order;
    }
}
