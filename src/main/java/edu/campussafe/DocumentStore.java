package edu.campussafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/** Stores the API's flexible documents in PostgreSQL JSONB, with durable UUID keys. */
@Component
class DocumentStore {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  DocumentStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    jdbc.execute("CREATE TABLE IF NOT EXISTS campus_documents (id text PRIMARY KEY, collection text NOT NULL, body jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now())");
    jdbc.execute("CREATE INDEX IF NOT EXISTS campus_documents_collection_idx ON campus_documents(collection)");
    jdbc.execute("CREATE INDEX IF NOT EXISTS campus_documents_body_idx ON campus_documents USING gin(body)");
  }

  @SuppressWarnings("unchecked")
  private Map<String,Object> decode(String json) {
    try { return mapper.readValue(json, MAP); }
    catch (Exception e) { throw new IllegalStateException("Could not read stored record", e); }
  }
  private String encode(Map<String,?> value) {
    try { return mapper.writeValueAsString(value); }
    catch (Exception e) { throw new IllegalArgumentException("Could not save record", e); }
  }
  private List<Map<String,Object>> rows(String sql, Object... args) {
    return jdbc.query(sql, (rs, n) -> decode(rs.getString(1)), args);
  }
  Map<String,Object> insert(Map<String,Object> value, String collection) {
    Map<String,Object> doc = new LinkedHashMap<>(value);
    doc.putIfAbsent("_id", UUID.randomUUID().toString());
    jdbc.update("INSERT INTO campus_documents(id, collection, body) VALUES (?, ?, ?::jsonb)", doc.get("_id"), collection, encode(doc));
    return doc;
  }
  Map<String,Object> save(Map<String,Object> doc, String collection) {
    String id = String.valueOf(doc.get("_id"));
    jdbc.update("INSERT INTO campus_documents(id, collection, body) VALUES (?, ?, ?::jsonb) ON CONFLICT(id) DO UPDATE SET collection=EXCLUDED.collection, body=EXCLUDED.body", id, collection, encode(doc));
    return doc;
  }
  Map<String,Object> byId(String id, String collection) {
    List<Map<String,Object>> found = rows("SELECT body::text FROM campus_documents WHERE id=? AND collection=?", id, collection);
    return found.isEmpty() ? null : found.get(0);
  }
  Map<String,Object> first(String collection, String field, Object value) {
    List<Map<String,Object>> found = rows("SELECT body::text FROM campus_documents WHERE collection=? AND body ->> ? = ? LIMIT 1", collection, field, String.valueOf(value));
    return found.isEmpty() ? null : found.get(0);
  }
  boolean exists(String collection, String field, Object value) {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=? AND body ->> ? = ?", Integer.class, collection, field, String.valueOf(value));
    return count != null && count > 0;
  }
  List<Map<String,Object>> all(String collection) { return rows("SELECT body::text FROM campus_documents WHERE collection=? ORDER BY created_at DESC", collection); }
  List<Map<String,Object>> where(String collection, String field, Object value, String sort, int limit, int offset) {
    String order = sort == null ? "created_at" : "(body ->> '" + sort.replace("'", "") + "')";
    return rows("SELECT body::text FROM campus_documents WHERE collection=? AND body ->> ? = ? ORDER BY " + order + " DESC LIMIT ? OFFSET ?", collection, field, String.valueOf(value), limit, offset);
  }
  List<Map<String,Object>> incidents(String priority, String status, boolean active, int limit, int offset) {
    String sql = "SELECT body::text FROM campus_documents WHERE collection='incidents'" +
      (priority == null ? "" : " AND body ->> 'priority' = ?") +
      (status == null ? "" : " AND body ->> 'status' = ?") +
      (active ? " AND body ->> 'status' <> 'Resolved'" : "") +
      " ORDER BY created_at DESC LIMIT ? OFFSET ?";
    List<Object> args = new ArrayList<>(); if (priority != null) args.add(priority); if (status != null) args.add(status); args.add(limit); args.add(offset);
    return rows(sql, args.toArray());
  }
  List<Map<String,Object>> notifications(Map<String,Object> user, int limit) {
    return rows("SELECT body::text FROM campus_documents WHERE collection='notifications' AND ((body ->> 'user' = ?) OR (NOT (body ? 'user') AND body ->> 'role' IN (?, 'all'))) ORDER BY created_at DESC LIMIT ?",
      String.valueOf(user.get("_id")), String.valueOf(user.get("role")), limit);
  }
  long count(String collection) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=?", Long.class, collection);
    return count == null ? 0 : count;
  }
  long count(String collection, String field, Object value) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=? AND body ->> ? = ?", Long.class, collection, field, String.valueOf(value));
    return count == null ? 0 : count;
  }
  long countNotEqual(String collection, String field, Object value, String otherField, Object otherValue) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM campus_documents WHERE collection=? AND body ->> ? = ? AND body ->> ? <> ?", Long.class, collection, field, String.valueOf(value), otherField, String.valueOf(otherValue));
    return count == null ? 0 : count;
  }
  void markRead(Object userId) {
    jdbc.update("UPDATE campus_documents SET body=jsonb_set(body, '{read}', 'true'::jsonb) WHERE collection='notifications' AND body ->> 'user' = ?", String.valueOf(userId));
  }
}
