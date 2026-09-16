package com.opensocket.aievent.core.iam.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class RowValues {
    private static final String SEP = "\u001f";
    private RowValues() {}
    static String string(Map<String,Object> row, String key) { Object v=row.get(key); return v==null?null:String.valueOf(v); }
    static long longValue(Map<String,Object> row, String key) { Object v=row.get(key); return v instanceof Number n?n.longValue():Long.parseLong(String.valueOf(v)); }
    static int intValue(Map<String,Object> row, String key) { Object v=row.get(key); return v instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(v)); }
    static boolean bool(Map<String,Object> row, String key) { Object v=row.get(key); return v instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(v)); }
    static Instant instant(Map<String,Object> row, String key) {
        Object v=row.get(key); if(v==null)return null;
        if(v instanceof Instant i)return i; if(v instanceof OffsetDateTime o)return o.toInstant();
        if(v instanceof Timestamp t)return t.toInstant(); return Instant.parse(String.valueOf(v));
    }
    static List<String> csv(Map<String,Object> row, String key) {
        String value=string(row,key); if(value==null||value.isEmpty())return List.of(); return Arrays.asList(value.split(SEP,-1));
    }
    static String join(java.util.Collection<String> values) { return String.join(SEP, values); }
}
