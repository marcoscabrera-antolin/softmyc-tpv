package com.softmyc.tpv.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ExtraDao {
    @Query("SELECT * FROM extras ORDER BY nombre ASC")
    List<ExtraLocal> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ExtraLocal> extras);

    @Query("DELETE FROM extras")
    void deleteAll();
}
