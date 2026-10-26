package com.softmyc.tpv.domain;

import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.MeasurementUnit;
import com.softmyc.tpv.domain.model.PaymentMethod;
import com.softmyc.tpv.domain.model.TaxCalculationMode;
import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;
import com.softmyc.tpv.domain.repository.InMemoryUniversalOrderRepository;
import com.softmyc.tpv.domain.service.TextReceiptFormatter;
import com.softmyc.tpv.domain.usecase.CalculateUniversalTotalUseCase;
import com.softmyc.tpv.domain.usecase.ProcessUniversalOrderUseCase;

import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas unitarias para los Casos de Uso de Dominio de la Fase 2.
 */
public class ProcessUniversalOrderUseCaseTest {

    private InMemoryUniversalOrderRepository repository;
    private TextReceiptFormatter printerService;
    private ProcessUniversalOrderUseCase useCase;

    @Before
    public void setUp() {
        repository = new InMemoryUniversalOrderRepository();
        printerService = new TextReceiptFormatter();
        useCase = new ProcessUniversalOrderUseCase(repository, printerService);
    }

    @Test
    public void testProcessOrder_success() {
        CatalogItem item = CatalogItem.builder("ITEM-01", "Libro de Programación", new BigDecimal("25.00"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.SUPER_REDUCED_4)
                .build();

        UniversalOrderItem line = UniversalOrderItem.builder(item, new BigDecimal("2")).build();

        UniversalOrder order = new UniversalOrder(
                "ORD-999",
                System.currentTimeMillis(),
                "BOOKSTORE",
                "Cliente VIP",
                Collections.singletonList(line),
                PaymentMethod.CARD,
                TaxCalculationMode.INCLUSIVE
        );

        ProcessUniversalOrderUseCase.OrderProcessingResult result =
                useCase.execute(order, "LIBRERÍA CENTRAL", true);

        assertTrue(result.isSuccess());
        assertNotNull(result.getSummary());
        assertEquals(new BigDecimal("50.00"), result.getSummary().getGrandTotal());

        // Verificar que el repositorio lo almacenó
        assertEquals(1, repository.count());
        UniversalOrder saved = repository.findById("ORD-999");
        assertNotNull(saved);
        assertEquals("ORD-999", saved.getId());
        assertEquals("BOOKSTORE", saved.getBusinessSector());
    }

    @Test
    public void testProcessOrder_rejectsEmptyOrder() {
        UniversalOrder emptyOrder = new UniversalOrder(
                "EMPTY-01",
                System.currentTimeMillis(),
                "RETAIL",
                "",
                Collections.emptyList(),
                PaymentMethod.CASH,
                TaxCalculationMode.INCLUSIVE
        );

        ProcessUniversalOrderUseCase.OrderProcessingResult result =
                useCase.execute(emptyOrder, "TIENDA", true);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertEquals(0, repository.count());
    }

    @Test
    public void testCalculateUniversalTotalUseCase_emptyOrderReturnsZeroes() {
        CalculateUniversalTotalUseCase calcUseCase = new CalculateUniversalTotalUseCase();
        CalculateUniversalTotalUseCase.OrderFinancialSummary summary = calcUseCase.execute(null);

        assertNotNull(summary);
        assertEquals(BigDecimal.ZERO.setScale(2), summary.getGrandTotal());
        assertEquals(0, summary.getLineCount());
    }
}
