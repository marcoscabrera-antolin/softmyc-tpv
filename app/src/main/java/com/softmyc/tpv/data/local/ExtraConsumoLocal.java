package com.softmyc.tpv.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Lo que gasta un extra de la carta. Se enlaza por nombre (normalizado), no por clave: los extras se resincronizan. */
@Entity(tableName = "extra_consumos",
        foreignKeys = @ForeignKey(entity = IngredienteLocal.class, parentColumns = "id", childColumns = "ingredienteId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("extraNormalizado"), @Index("ingredienteId")})
public class ExtraConsumoLocal {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String extra = "";
    @NonNull
    public String extraNormalizado = "";
    public long ingredienteId;
    public double cantidad;
}
