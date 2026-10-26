package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.repository.UniversalOrderRepository;
import com.softmyc.tpv.domain.service.UniversalPrinterService;

/**
 * Caso de Uso: Procesamiento, Persistencia y Emisión de un Pedido Universal.
 * Coordina las interfaces de dominio UniversalOrderRepository y UniversalPrinterService
 * siguiendo el Principio de Responsabilidad Única (SRP) e Inversión de Dependencias (DIP).
 */
public class ProcessUniversalOrderUseCase {

    private final UniversalOrderRepository repository;
    private final UniversalPrinterService printerService;
    private final CalculateUniversalTotalUseCase calculateTotalUseCase;

    public ProcessUniversalOrderUseCase(
            UniversalOrderRepository repository,
            UniversalPrinterService printerService
    ) {
        this.repository = repository;
        this.printerService = printerService;
        this.calculateTotalUseCase = new CalculateUniversalTotalUseCase();
    }

    public static class OrderProcessingResult {
        private final boolean success;
        private final UniversalOrder order;
        private final CalculateUniversalTotalUseCase.OrderFinancialSummary summary;
        private final String errorMessage;

        public OrderProcessingResult(UniversalOrder order, CalculateUniversalTotalUseCase.OrderFinancialSummary summary) {
            this.success = true;
            this.order = order;
            this.summary = summary;
            this.errorMessage = null;
        }

        public OrderProcessingResult(String errorMessage) {
            this.success = false;
            this.order = null;
            this.summary = null;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public UniversalOrder getOrder() {
            return order;
        }

        public CalculateUniversalTotalUseCase.OrderFinancialSummary getSummary() {
            return summary;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Procesa el pedido: valida, calcula importes, persiste y emite ticket térmico si procede.
     */
    public OrderProcessingResult execute(UniversalOrder order, String storeName, boolean printTicket) {
        if (order == null || order.getItems().isEmpty()) {
            return new OrderProcessingResult("No se puede procesar un pedido vacío");
        }

        try {
            // 1. Calcular liquidación financiera
            CalculateUniversalTotalUseCase.OrderFinancialSummary summary = calculateTotalUseCase.execute(order);

            // 2. Persistir en el repositorio
            if (repository != null) {
                repository.save(order);
            }

            // 3. Emitir ticket si se solicita
            if (printTicket && printerService != null) {
                printerService.printTicket(order, storeName);
            }

            return new OrderProcessingResult(order, summary);
        } catch (Exception e) {
            return new OrderProcessingResult("Error durante el procesamiento del pedido: " + e.getMessage());
        }
    }
}
