package com.softmyc.tpv.data.local;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ventas", indices = {
    @Index("fecha"),
    @Index("isSynced")
})
public class VentaLocal {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long fecha;
    public double total;
    public String itemsJson;
    public boolean isSynced;

    public String metodoPago;

    /** Identificador global de la venta (UUID v4): hace idempotente la subida al servidor aunque se reintente. */
    public String uuid;

    public VentaLocal() {}

    /** uuid de una venta que se edita: el existente si lo tiene (misma identidad en el servidor) o uno nuevo. */
    public static String uuidParaEdicion(VentaLocal existente) {
        return (existente != null && existente.uuid != null && !existente.uuid.isEmpty())
                ? existente.uuid : java.util.UUID.randomUUID().toString();
    }

    /** Fecha de una venta que se edita: la original si existe; editarla no la mueve al día (ni al arqueo) de hoy. */
    public static long fechaParaEdicion(VentaLocal existente, long ahora) {
        return (existente != null && existente.fecha > 0) ? existente.fecha : ahora;
    }

    @Ignore
    public VentaLocal(long fecha, double total, String itemsJson, boolean isSynced, String metodoPago) {
        this.fecha = fecha;
        this.total = total;
        this.itemsJson = itemsJson;
        this.isSynced = isSynced;
        this.metodoPago = metodoPago;
        this.uuid = java.util.UUID.randomUUID().toString();
    }
}
