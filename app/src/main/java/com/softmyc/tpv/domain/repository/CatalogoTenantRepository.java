package com.softmyc.tpv.domain.repository;

import java.util.List;

/**
 * Contrato del catálogo completo del negocio activo: siembra inicial, extras, salsas y la
 * aplicación de la descarga del servidor. La capa domain define QUÉ se necesita; data decide CÓMO.
 */
public interface CatalogoTenantRepository {

    /** Carga el catálogo inicial del sector del negocio indicado (la base de datos del negocio). */
    void sembrar(String tenantId);

    /** Extras disponibles con su precio. */
    List<ExtraCatalogo> listarExtras();

    /** Nombres de las salsas; si no hay ninguna, se siembran desde el catálogo de salsas. */
    List<String> listarSalsas();

    /**
     * Aplica la descarga del catálogo del servidor conservando el stock local y los cambios aún no enviados.
     * @return false si se descartó la descarga (hay cambios de catálogo sin enviar) o no había productos.
     */
    boolean aplicarDescargaServidor(String tenantId, List<ProductoCatalogo> productos,
                                    List<ExtraCatalogo> extras, List<String> salsas);
}
