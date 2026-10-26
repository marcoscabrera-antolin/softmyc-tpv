package com.softmyc.tpv.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "extras")
public class ExtraLocal {
    @PrimaryKey
    @NonNull
    public String nombre;
    public double precio;

    public ExtraLocal() {
        this.nombre = "";
    }

    @Ignore
    public ExtraLocal(@NonNull String nombre, double precio) {
        this.nombre = nombre;
        this.precio = precio;
    }
}
