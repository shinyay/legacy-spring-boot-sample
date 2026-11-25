package com.techbookstore.app.service;

import com.techbookstore.app.dto.IntegratedAnalysisRequest;
import com.techbookstore.app.dto.IntegratedAnalysisResult;
import com.techbookstore.app.dto.InventoryReportDto;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegratedInventoryAnalysisServiceTest {

    @Mock private ReportService reportService;
    @Mock private ABCXYZAnalysisService abcxyzService;
    @Mock private AnalyticsService analyticsService;
    @Mock private DemandForecastService forecastService;
    @Mock private OptimalStockCalculatorService optimalStockService;
    @InjectMocks private IntegratedInventoryAnalysisService analysisService;

    @ParameterizedTest(name = "publicationYear={0}, parsed={1}")
    @MethodSource("publicationYears")
    void shouldForwardSameFilters_whenGeneratingDashboardAndIntegratedAnalysis(String year, Integer parsedYear) {
        IntegratedAnalysisRequest request = new IntegratedAnalysisRequest();
        request.setCategory("Java");
        request.setLevel("INTERMEDIATE");
        request.setPublisher("Publisher");
        request.setStockStatus("LOW");
        request.setPriceRange("1000-5000");
        request.setPublicationYear(year);
        request.setIncludeOptimization(false);
        request.setAnalysisTypes(Collections.emptyList());
        InventoryReportDto report = new InventoryReportDto(LocalDate.now(), 3, 1, 0, BigDecimal.TEN);
        when(reportService.generateInventoryReport(
            "Java", "INTERMEDIATE", "Publisher", "LOW", "1000-5000", parsedYear)).thenReturn(report);

        Map<String, Object> dashboard = analysisService.generateRealtimeDashboardData(request);
        IntegratedAnalysisResult analysis = analysisService.executeIntegratedAnalysis(request);

        assertSame(report, dashboard.get("inventorySummary"));
        assertSame(report, analysis.getBaseReport());
        assertEquals("COMPLETED", analysis.getStatus());
        verify(reportService, times(2)).generateInventoryReport(
            "Java", "INTERMEDIATE", "Publisher", "LOW", "1000-5000", parsedYear);
    }

    static Stream<Arguments> publicationYears() {
        return Stream.of(
            Arguments.of("2023", 2023),
            Arguments.of(null, null),
            Arguments.of("", null),
            Arguments.of("   ", null),
            Arguments.of("invalid", null),
            Arguments.of(" 2023 ", null),
            Arguments.of("999999999999", null)
        );
    }
}
