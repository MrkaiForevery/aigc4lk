package com.air.memory.utils;

import cn.hutool.json.JSONUtil;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

public class MKJsonUtils {

    public static PGobject transferToPGJsonB(Object obj) throws SQLException {
        String jsonStr = JSONUtil.toJsonStr(obj);
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue(jsonStr);
        return pg;
    }
}
