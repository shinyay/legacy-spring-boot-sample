package com.techbookstore.app.service;

import com.techbookstore.app.dto.InventoryReportDto;
import com.techbookstore.app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private BookRepository bookRepository;
    @Mock private AggregationCacheRepository cacheRepository;
    @InjectMocks private ReportService reportService;

    @Test
    void shouldGenerateInventoryReport_whenCalledWithoutFilters() {
        when(bookRepository.count()).thenReturn(100L);

        InventoryReportDto report = reportService.generateInventoryReport();

        assertAll(
            () -> assertEquals(100, report.getTotalProducts()),
            () -> assertEquals(10, report.getLowStockCount()),
            () -> assertEquals(5, report.getOutOfStockCount()),
            () -> assertEquals(0, new BigDecimal("350000").compareTo(report.getTotalInventoryValue())),
            () -> assertFalse(report.getItems().isEmpty()),
            () -> assertFalse(report.getReorderSuggestions().isEmpty()),
            () -> assertNotNull(report.getTurnoverSummary()),
            () -> assertTrue(report.getAverageTurnoverRate() >= 3.2 && report.getAverageTurnoverRate() <= 5.2),
            () -> assertTrue(report.getObsolescenceRiskIndex() >= 0 && report.getObsolescenceRiskIndex() < 100)
        );
    }

    @ParameterizedTest
    @CsvSource({"jAvA, OK, Java", "PYTHON, OUT, Python"})
    void shouldFilterInventoryItems_whenCategoryAndStatusAreProvided(
            String category, String status, String expectedCategory) {
        when(bookRepository.count()).thenReturn(100L);

        InventoryReportDto report = reportService.generateInventoryReport(
            category, null, null, status, null, null);

        assertEquals(1, report.getItems().size());
        assertEquals(expectedCategory, report.getItems().get(0).getCategory());
        assertEquals(status, report.getItems().get(0).getStockStatus());
    }
}
