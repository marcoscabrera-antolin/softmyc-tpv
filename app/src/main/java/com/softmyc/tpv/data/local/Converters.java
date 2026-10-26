package com.softmyc.tpv.data.local;

import androidx.room.TypeConverter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Converters {

    @TypeConverter
    public static String fromStringList(List<String> list) {
        if (list == null) return null;
        return String.join(";;", list);
    }

    @TypeConverter
    public static List<String> toStringList(String str) {
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(str.split(";;")));
    }

}
