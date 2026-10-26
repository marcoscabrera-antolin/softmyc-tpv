package com.softmyc.tpv.data.local;

import org.junit.Test;
import static org.junit.Assert.*;

public class TenantCatalogSeederTest {

    @Test
    public void testGetBusinessName_returnsCorrectNamesForMultiSector() {
        assertEquals("La Nube Bocatería", TenantCatalogSeeder.getBusinessName("lanube"));
        assertEquals("El Bocata Gourmet", TenantCatalogSeeder.getBusinessName("elbocata"));
        assertEquals("Frutería El Huerto Bio", TenantCatalogSeeder.getBusinessName("fruteria"));
        assertEquals("Taller Mecánico MotorSport", TenantCatalogSeeder.getBusinessName("taller"));
        assertEquals("SoftMYC Express Paquetería", TenantCatalogSeeder.getBusinessName("paqueteria"));
        assertEquals("Central Mayorista de Distribución", TenantCatalogSeeder.getBusinessName("mayorista"));
    }

    @Test
    public void testGetBusinessSector_returnsCorrectSectors() {
        assertEquals("Hostelería y Restauración", TenantCatalogSeeder.getBusinessSector("lanube"));
        assertEquals("Comercio al Peso / Granel", TenantCatalogSeeder.getBusinessSector("fruteria"));
        assertEquals("Servicios y Automoción", TenantCatalogSeeder.getBusinessSector("taller"));
        assertEquals("Logística y Transporte", TenantCatalogSeeder.getBusinessSector("paqueteria"));
        assertEquals("Venta y Distribución B2B", TenantCatalogSeeder.getBusinessSector("mayorista"));
    }

    @Test
    public void testGetBusinessIcon_returnsIcons() {
        assertEquals("🍔", TenantCatalogSeeder.getBusinessIcon("lanube"));
        assertEquals("🍎", TenantCatalogSeeder.getBusinessIcon("fruteria"));
        assertEquals("🔧", TenantCatalogSeeder.getBusinessIcon("taller"));
        assertEquals("📦", TenantCatalogSeeder.getBusinessIcon("paqueteria"));
        assertEquals("🏭", TenantCatalogSeeder.getBusinessIcon("mayorista"));
    }

    @Test
    public void testIdentificadorSeNormalizaConMayusculasEspaciosYPrefijoLegacy() {
        assertEquals(TenantCatalogSeeder.getBusinessName("fruteria"), TenantCatalogSeeder.getBusinessName("  FRUTERIA "));
        assertEquals(TenantCatalogSeeder.getBusinessName("fruteria"), TenantCatalogSeeder.getBusinessName("softmyc_fruteria"));
        assertEquals(TenantCatalogSeeder.getBusinessIcon("taller"), TenantCatalogSeeder.getBusinessIcon("softmyc_taller"));
    }

    @Test
    public void testSinNegocioUsaValoresPorDefectoDeHosteleria() {
        assertEquals("La Nube Bocatería", TenantCatalogSeeder.getBusinessName(null));
        assertEquals("Hostelería", TenantCatalogSeeder.getBusinessSector(null));
        assertEquals("🍔", TenantCatalogSeeder.getBusinessIcon(null));
    }
}
