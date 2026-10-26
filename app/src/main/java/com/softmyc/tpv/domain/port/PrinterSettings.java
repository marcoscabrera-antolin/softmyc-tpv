package com.softmyc.tpv.domain.port;

/** Puerto que lee los ajustes de impresión guardados por el usuario (con valores por defecto si faltan). */
public interface PrinterSettings {

    AjustesImpresora leer();
}
