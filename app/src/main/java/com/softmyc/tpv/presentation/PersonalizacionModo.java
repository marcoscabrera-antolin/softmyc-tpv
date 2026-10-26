package com.softmyc.tpv.presentation;

import com.softmyc.tpv.domain.Producto;

/**
 * Configuración de la personalización según el sector del producto. Cada modo rellena cabecera, tres tarjetas de
 * chips y el stepper de cantidad del diálogo.
 */
interface PersonalizacionModo {

    void configurar(Producto producto, PersonalizacionVistas vistas, PersonalizacionEstado estado,
                    PersonalizacionAcciones acciones);
}
