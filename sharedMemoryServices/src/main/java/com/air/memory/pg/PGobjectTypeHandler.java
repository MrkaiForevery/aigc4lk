package com.air.memory.pg;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;
import java.sql.*;

public class PGobjectTypeHandler extends BaseTypeHandler<PGobject> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PGobject parameter, JdbcType jdbcType) throws SQLException {
        // 直接将 PGobject 设置进去，驱动会识别它的类型
        ps.setObject(i, parameter);
    }

    @Override
    public PGobject getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return (PGobject) rs.getObject(columnName);
    }

    @Override
    public PGobject getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return (PGobject) rs.getObject(columnIndex);
    }

    @Override
    public PGobject getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return (PGobject) cs.getObject(columnIndex);
    }
}