package com.softmyc.tpv.data.sync;

/** Estado de la última sincronización de ventas: motivo de omisión, rechazos del último intento y hora del último éxito. Inmutable. */
public final class SyncStatus {

    private final SyncOmision omision;
    private final int rechazadasUltimoIntento;
    private final long ultimoExitoMs;

    public SyncStatus(SyncOmision omision, int rechazadasUltimoIntento, long ultimoExitoMs) {
        this.omision = omision;
        this.rechazadasUltimoIntento = rechazadasUltimoIntento;
        this.ultimoExitoMs = ultimoExitoMs;
    }

    public SyncOmision getOmision() {
        return omision;
    }

    public int getRechazadasUltimoIntento() {
        return rechazadasUltimoIntento;
    }

    /** Instante (epoch ms) del último lote aceptado por el servidor; 0 si nunca. */
    public long getUltimoExitoMs() {
        return ultimoExitoMs;
    }
}
