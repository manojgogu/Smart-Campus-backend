package edu.campussafe.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stores API documents as JSON in MySQL, with a relational index for each collection. */
@Component
public class DocumentStore {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public DocumentStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    jdbc.execute("CREATE TABLE IF NOT EXISTS campus_documents (" +
      "id VARCHAR(64) PRIMARY KEY, " +
      "collection VARCHAR(100) NOT NULL, " +
      "body JSON NOT NULL, " +
      "created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), " +
      "KEY campus_documents_collection_idx (collection), " +
      "KEY campus_documents_created_at_idx (created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }

  private Map<String, Object> decode(String json) {
    try {
      return mapper.readValue(json, MAP);
    } catch (Exception e) {
      throw new IllegalStateException("Could not read stored record", e);
    }
  }

  private String encode(Map<String, ?> value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not save record", e);
    }
  }

  private List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.query(sql, (rs, n) -> decode(rs.getString(1)), args);
  }

  private String jsonText(String field) {
    if (field == null || !field.matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("Invalid document field");
    }
    return "JSON_UNQUOTE(JSON_EXTRACT(body, '$." + field + "'))";
  }

  public Map<String, Object> insert(Map<String, Object> value, String collection) {
    Map<String, Object> doc = new LinkedHashMap<>(value);
    doc.putIfAbsent("_id", UUID.randomUUID().toString());
    jdbc.update("INSERT INTO campus_documents(id, collection, body) VALUES (?, ?, CAST(? AS JSON))",
      doc.get("_id"), collection, encode(doc));
    return doc;
  }

  public Map<String, Object> save(Map<String, Object> doc, String collection) {
    String id = String.valueOf(doc.get("_id"));
    jdbc.update("INSERT INTO campus_documents(id, collection, body) VALUES (?, ?, CAST(? AS JSON)) " +
        "ON DUPLICATE KEY UPDATE collection=VALUES(collection), body=VALUES(body)",
      id, collection, encode(doc));
    return doc;
  }

  public Map<String, Object> byId(String id, String collection) {
    List<Map<String, Object>> found = rows(
      "SELECT body FROM campus_documents WHERE id=? AND collection=?", id, collection);
    return found.isEmpty() ? null : found.get(0);
  }

  public Map<String, Object> first(String collection, String field, Object value) {
    List<Map<String, Object>> found = rows("SELECT body FROM campus_documents WHERE collection=? AND " +
      jsonText(field) + "=? LIMIT 1", collection, String.valueOf(value));
    return found.isEmpty() ? null : found.get(0);
  }

  public boolean exists(String collection, String field, Object value) {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=? AND " +
      jsonText(field) + "=?", Integer.class, collection, String.valueOf(value));
    return count != null && count > 0;
  }

  public List<Map<String, Object>> all(String collection) {
    return rows("SELECT body FROM campus_documents WHERE collection=? ORDER BY created_at DESC", collection);
  }

  public List<Map<String, Object>> where(String collection, String field, Object value,
      String sort, int limit, int offset) {
    String order = sort == null ? "created_at" : jsonText(sort);
    return rows("SELECT body FROM campus_documents WHERE collection=? AND " + jsonText(field) +
      "=? ORDER BY " + order + " DESC LIMIT ? OFFSET ?", collection, String.valueOf(value), limit, offset);
  }

  public List<Map<String, Object>> incidents(String priority, String status, boolean active, int limit, int offset) {
    StringBuilder sql = new StringBuilder("SELECT body FROM campus_documents WHERE collection='incidents'");
    List<Object> args = new ArrayList<>();
    if (priority != null) {
      sql.append(" AND ").append(jsonText("priority")).append("=?");
      args.add(priority);
    }
    if (status != null) {
      sql.append(" AND ").append(jsonText("status")).append("=?");
      args.add(status);
    }
    if (active) sql.append(" AND ").append(jsonText("status")).append("<>'Resolved'");
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return rows(sql.toString(), args.toArray());
  }

  public List<Map<String, Object>> notifications(Map<String, Object> user, int limit) {
    return rows("SELECT body FROM campus_documents WHERE collection='notifications' AND " +
      "(" + jsonText("user") + "=? OR (JSON_CONTAINS_PATH(body, 'one', '$.user')=0 AND " +
      jsonText("role") + " IN (?, 'all'))) ORDER BY created_at DESC LIMIT ?",
      String.valueOf(user.get("_id")), String.valueOf(user.get("role")), limit);
  }

  public long count(String collection) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=?", Long.class, collection);
    return count == null ? 0 : count;
  }

  public long count(String collection, String field, Object value) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=? AND " +
      jsonText(field) + "=?", Long.class, collection, String.valueOf(value));
    return count == null ? 0 : count;
  }

  public long countNotEqual(String collection, String field, Object value, String otherField, Object otherValue) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=? AND " +
      jsonText(field) + "=? AND " + jsonText(otherField) + "<>?", Long.class,
      collection, String.valueOf(value), String.valueOf(otherValue));
    return count == null ? 0 : count;
  }

  public void markRead(Object userId) {
    jdbc.update("UPDATE campus_documents SET body=JSON_SET(body, '$.read', true) WHERE " +
      "collection='notifications' AND " + jsonText("user") + "=?", String.valueOf(userId));
  }
}
