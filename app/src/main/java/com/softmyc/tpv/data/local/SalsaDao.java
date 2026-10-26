package com.softmyc.tpv.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SalsaDao {
    @Query("SELECT * FROM salsas ORDER BY nombre ASC")
    List<SalsaLocal> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SalsaLocal> salsas);

    @Query("DELETE FROM salsas")
    void deleteAll();
}
