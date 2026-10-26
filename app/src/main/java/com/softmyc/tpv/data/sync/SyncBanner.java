package com.softmyc.tpv.data.sync;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Texto del aviso "N ventas sin sincronizar · motivo". Lógica pura: sin Android, para poder probarla en la JVM. */
public final class SyncBanner {

    private static final SimpleDateFormat HORA = new SimpleDateFormat("HH:mm", new Locale("es", "ES"));

    private SyncBanner() { }

    /** Qué mostrar: si {@link #visible()} es false no hay nada que avisar. */
    public static final class Aviso {
        private final boolean visible;
        private final String texto;

        private Aviso(boolean visible, String texto) {
            this.visible = visible;
            this.texto = texto;
        }

        public boolean visible() {
            return visible;
        }

        public String texto() {
            return texto;
        }
    }

    public static Aviso describir(int pendientes, SyncStatus estado) {
        if (pendientes <= 0) return new Aviso(false, "");
        StringBuilder texto = new StringBuilder()
                .append(pendientes == 1 ? "1 venta sin sincronizar" : pendientes + " ventas sin sincronizar")
                .append(" · ").append(motivo(estado));
        if (estado.getUltimoExitoMs() > 0) {
            texto.append(" · última subida ").append(HORA.format(new Date(estado.getUltimoExitoMs())));
        }
        return new Aviso(true, texto.toString());
    }

    private static String motivo(SyncStatus estado) {
        if (estado.getOmision() != SyncOmision.NINGUNA) return estado.getOmision().descripcion();
        if (estado.getRechazadasUltimoIntento() > 0) {
            int n = estado.getRechazadasUltimoIntento();
            return n == 1 ? "1 rechazada por el servidor" : n + " rechazadas por el servidor";
        }
        return "pendiente de envío";
    }
}
