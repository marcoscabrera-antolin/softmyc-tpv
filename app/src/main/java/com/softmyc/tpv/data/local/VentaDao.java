package com.softmyc.tpv.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VentaDao {
    @Query("SELECT * FROM ventas ORDER BY fecha DESC")
    List<VentaLocal> getAll();

    /** Ventas desde un instante (inclusive), las más recientes primero. Usa el índice sobre fecha. */
    @Query("SELECT * FROM ventas WHERE fecha >= :inicioMs ORDER BY fecha DESC")
    List<VentaLocal> getDesde(long inicioMs);

    /** Las últimas {@code limite} ventas, la más reciente primero. Usa el índice sobre fecha (límite positivo). */
    @Query("SELECT * FROM ventas ORDER BY fecha DESC LIMIT :limite")
    List<VentaLocal> getRecientes(int limite);

    @Query("SELECT * FROM ventas WHERE isSynced = 0")
    List<VentaLocal> getUnsynced();

    @Query("SELECT * FROM ventas WHERE id = :id LIMIT 1")
    VentaLocal getById(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(VentaLocal venta);

    @Update
    void update(VentaLocal venta);

    @Query("UPDATE ventas SET isSynced = 1 WHERE id = :id")
    void markAsSynced(int id);

    /**
     * Marca como subida solo la versión que se envió: si la venta se editó mientras tanto
     * (total, líneas o método de pago distintos) sigue pendiente y se reenviará.
     */
    @Query("UPDATE ventas SET isSynced = 1 WHERE id = :id AND total = :total AND itemsJson IS :itemsJson AND metodoPago IS :metodoPago")
    int markAsSyncedSiNoCambio(int id, double total, String itemsJson, String metodoPago);

    @Query("DELETE FROM ventas")
    void deleteAll();
}
