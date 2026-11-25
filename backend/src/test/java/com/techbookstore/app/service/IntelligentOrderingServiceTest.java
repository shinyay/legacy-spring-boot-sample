package com.techbookstore.app.service;

import com.techbookstore.app.dto.OptimalStockDto;
import com.techbookstore.app.dto.OrderSuggestionDto;
import com.techbookstore.app.repository.BookRepository;
import com.techbookstore.app.repository.OptimalStockSettingsRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntelligentOrderingServiceTest {

    @Mock private OptimalStockCalculatorService optimalStockCalculatorService;
    @Mock private DemandForecastService demandForecastService;
    @Mock private TechTrendAnalysisService techTrendAnalysisService;
    @Mock private SeasonalAnalysisService seasonalAnalysisService;
    @Mock private BookRepository bookRepository;
    @Mock private OptimalStockSettingsRepository optimalStockSettingsRepository;
    @InjectMocks private IntelligentOrderingService orderingService;

    @ParameterizedTest
    @CsvSource({"5, 20, 5", ", 20, 5"})
    void shouldApplyCurrentSeasonalMultiplier_whenGeneratingSuggestions(
            Integer recommended, int optimal, int current) {
        OptimalStockDto stock = new OptimalStockDto();
        stock.setBookTitle("Java");
        stock.setStockStatus("REORDER_NEEDED");
        stock.setCurrentStock(current);
        stock.setOptimalStockLevel(optimal);
        stock.setRecommendedOrderQuantity(recommended);
        when(optimalStockCalculatorService.calculateOptimalStock(1L)).thenReturn(stock);

        OrderSuggestionDto suggestion = orderingService.generateOrderSuggestions(
            Collections.singletonList(1L), "SEASONAL");

        String[] multipliers = {"1.1", "1.1", "1.3", "1.3", "0.9", "0.9",
            "1.1", "1.1", "1.4", "1.4", "1.4", "1.0"};
        BigDecimal multiplier = new BigDecimal(multipliers[suggestion.getSuggestionDate().getMonthValue() - 1]);
        int base = recommended != null ? recommended : optimal - current;
        int expected = BigDecimal.valueOf(base).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValue();
        assertEquals(expected, stock.getRecommendedOrderQuantity());
        assertEquals(1, suggestion.getTotalSuggestions());
    }
}
