package com.softmyc.tpv.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "salsas")
public class SalsaLocal {
    @PrimaryKey
    @NonNull
    public String nombre;

    public SalsaLocal() {
        this.nombre = "";
    }

    @Ignore
    public SalsaLocal(@NonNull String nombre) {
        this.nombre = nombre;
    }
}
