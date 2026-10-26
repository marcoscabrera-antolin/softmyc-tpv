package com.softmyc.tpv.presentation;

/** Estado mutable de la personalización en curso: cantidad entera, cantidad decimal (peso, horas) y tara. */
final class PersonalizacionEstado {
    private int quantity;
    private double decimalQuantity;
    private double tareKg;

    PersonalizacionEstado(int quantity, double decimalQuantity) {
        this.quantity = quantity;
        this.decimalQuantity = decimalQuantity;
        this.tareKg = 0.0;
    }

    int getQuantity() {
        return quantity;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    double getDecimalQuantity() {
        return decimalQuantity;
    }

    void setDecimalQuantity(double decimalQuantity) {
        this.decimalQuantity = decimalQuantity;
    }

    double getTareKg() {
        return tareKg;
    }

    void setTareKg(double tareKg) {
        this.tareKg = tareKg;
    }
}
