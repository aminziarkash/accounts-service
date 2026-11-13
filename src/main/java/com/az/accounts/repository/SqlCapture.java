package com.az.accounts.repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class SqlCapture implements StatementInspector {

    static final List<String> SQL = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQL.add(sql.toLowerCase());
        return sql;
    }
}
