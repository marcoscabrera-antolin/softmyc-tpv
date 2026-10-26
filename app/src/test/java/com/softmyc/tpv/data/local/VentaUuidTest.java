package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.UUID;

public class VentaUuidTest {

    @Test
    public void newSaleGetsValidUuidV4() {
        VentaLocal venta = new VentaLocal(1L, 2.4, "[]", false, "Efectivo");
        assertEquals(4, UUID.fromString(venta.uuid).version());
    }

    @Test
    public void twoSalesGetDifferentUuids() {
        VentaLocal a = new VentaLocal(1L, 1.0, "[]", false, "Efectivo");
        VentaLocal b = new VentaLocal(1L, 1.0, "[]", false, "Efectivo");
        assertNotEquals(a.uuid, b.uuid);
    }

    @Test
    public void editedSaleKeepsTheUuidOfTheExistingOne() {
        VentaLocal existente = new VentaLocal(1L, 2.4, "[]", true, "Efectivo");
        assertEquals(existente.uuid, VentaLocal.uuidParaEdicion(existente));
    }

    @Test
    public void editedSaleWithoutExistingUuidGetsANewOne() {
        VentaLocal sinUuid = new VentaLocal();
        assertTrue(UUID.fromString(VentaLocal.uuidParaEdicion(sinUuid)).toString().length() == 36);
        assertEquals(4, UUID.fromString(VentaLocal.uuidParaEdicion(null)).version());
    }
}
