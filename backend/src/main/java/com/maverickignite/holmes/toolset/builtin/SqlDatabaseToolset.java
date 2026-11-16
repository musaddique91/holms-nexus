package com.maverickignite.holmes.toolset.builtin;

import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.holmes.toolset.Args;
import com.maverickignite.holmes.toolset.Schemas;
import com.maverickignite.holmes.toolset.ToolException;
import com.maverickignite.holmes.toolset.ToolSpec;
import com.maverickignite.holmes.toolset.Toolset;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Read-only diagnostics on PostgreSQL, MySQL and SQL Server: health (connections, long queries,
 * locks), schema lookup and SELECT queries. Defence in depth: statements are checked, the
 * connection is read-only, every transaction is rolled back, and the configured user should
 * itself be read-only.
 */
@Component
class SqlDatabaseToolset implements Toolset {

  private static final Pattern READ_ONLY =
      Pattern.compile("^\\s*(select|with|explain|show)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern WRITE_KEYWORDS =
      Pattern.compile(
          "\\b(insert|update|delete|merge|drop|alter|create|truncate|grant|revoke|call|exec|execute|copy|into)\\b",
          Pattern.CASE_INSENSITIVE);
  /** Functions that have side effects or touch the server even inside a read-only transaction. */
  private static final Pattern DANGEROUS =
      Pattern.compile(
          "\\b(pg_terminate_backend|pg_cancel_backend|pg_sleep\\w*|pg_reload_conf|pg_read_\\w+|pg_ls_\\w+"
              + "|pg_stat_file|set_config|dblink\\w*|lo_\\w+|nextval|setval|pg_advisory\\w*|query_to_xml"
              + "|xp_\\w+|sp_\\w+|openrowset|opendatasource|waitfor|load_file|sleep|benchmark|get_lock)\\b"
              + "|\\bfor\\s+(update|share|no\\s+key\\s+update|key\\s+share)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final int MAX_ROWS = 200;

  private final Map<String, HolmesProperties.Database> databases;

  SqlDatabaseToolset(HolmesProperties properties) {
    this.databases =
        properties.getToolsets().getDatabases().stream()
            .collect(Collectors.toMap(HolmesProperties.Database::getName, d -> d));
  }

  @Override
  public String name() {
    return "databases";
  }

  @Override
  public String description() {
    return "Read-only PostgreSQL / MySQL / SQL Server diagnostics.";
  }

  @Override
  public boolean enabled() {
    return !databases.isEmpty();
  }

  @Override
  public List<ToolSpec> tools() {
    String[] names = databases.keySet().toArray(String[]::new);
    String catalog =
        databases.values().stream()
            .map(d -> d.getName() + " (" + d.getType() + ")")
            .collect(Collectors.joining(", "));
    return List.of(
        new ToolSpec(
            "db_health",
            "Connection usage, long-running queries and blocked sessions of a database. Databases: "
                + catalog,
            Schemas.object().enumeration("database", "Database name", true, names).build(),
            args -> health(db(args))),
        new ToolSpec(
            "db_describe_table",
            "Columns, types and indexes of a table, to check a query or entity mapping against the"
                + " real schema.",
            Schemas.object()
                .enumeration("database", "Database name", true, names)
                .string("table", "Table name", true)
                .string("schema", "Schema (default: public / dbo / current database)", false)
                .build(),
            this::describe),
        new ToolSpec(
            "db_query",
            "Run one read-only SELECT (max " + MAX_ROWS + " rows). Never select secrets or"
                + " personal data you do not need.",
            Schemas.object()
                .enumeration("database", "Database name", true, names)
                .string("sql", "A single SELECT / WITH / EXPLAIN / SHOW statement", true)
                .build(),
            args -> {
              String sql = Args.required(args, "sql").strip().replaceFirst(";\\s*$", "");
              if (!READ_ONLY.matcher(sql).find()
                  || sql.contains(";")
                  || WRITE_KEYWORDS.matcher(sql).find()
                  || DANGEROUS.matcher(sql).find()) {
                throw new ToolException("Only a single read-only SELECT statement is allowed");
              }
              return query(db(args), sql);
            }));
  }

  private HolmesProperties.Database db(JsonNode args) {
    String name = Args.required(args, "database");
    HolmesProperties.Database db = databases.get(name);
    if (db == null) {
      throw new ToolException("Unknown database '" + name + "'. Known: " + databases.keySet());
    }
    return db;
  }

  private String health(HolmesProperties.Database db) throws SQLException {
    StringBuilder sb = new StringBuilder();
    for (String sql : healthQueries(db.getType())) {
      sb.append("> ").append(sql.lines().findFirst().orElse(sql)).append('\n');
      try {
        sb.append(query(db, sql)).append('\n');
      } catch (SQLException e) {
        sb.append("error: ").append(e.getMessage()).append('\n');
      }
    }
    return sb.toString();
  }

  private static List<String> healthQueries(String type) {
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "postgres", "postgresql" ->
          List.of(
              "SELECT state, count(*) FROM pg_stat_activity GROUP BY state",
              "SELECT setting AS max_connections FROM pg_settings WHERE name = 'max_connections'",
              "SELECT pid, now() - query_start AS duration, state, left(query, 200) AS query"
                  + " FROM pg_stat_activity WHERE state <> 'idle' AND query_start < now() - interval"
                  + " '30 seconds' ORDER BY duration DESC LIMIT 10",
              "SELECT blocked.pid AS blocked_pid, blocking.pid AS blocking_pid,"
                  + " left(blocked.query, 120) AS blocked_query FROM pg_stat_activity blocked JOIN"
                  + " pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))"
                  + " LIMIT 10");
      case "mysql", "mariadb" ->
          List.of(
              "SHOW GLOBAL STATUS LIKE 'Threads_connected'",
              "SHOW VARIABLES LIKE 'max_connections'",
              "SELECT id, user, time, state, left(info, 200) AS query FROM"
                  + " information_schema.processlist WHERE command <> 'Sleep' AND time > 30 ORDER BY"
                  + " time DESC LIMIT 10");
      case "sqlserver", "mssql" ->
          List.of(
              "SELECT count(*) AS sessions FROM sys.dm_exec_sessions WHERE is_user_process = 1",
              "SELECT TOP 10 r.session_id, r.status, r.blocking_session_id, r.wait_type,"
                  + " r.total_elapsed_time / 1000 AS seconds, LEFT(t.text, 200) AS query FROM"
                  + " sys.dm_exec_requests r CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t WHERE"
                  + " r.session_id <> @@SPID ORDER BY r.total_elapsed_time DESC");
      default -> throw new ToolException("Unsupported database type " + type);
    };
  }

  private String describe(JsonNode args) throws SQLException {
    HolmesProperties.Database db = db(args);
    String table = Args.required(args, "table");
    String schema = Args.optional(args, "schema");
    try (Connection c = connect(db)) {
      DatabaseMetaData meta = c.getMetaData();
      StringBuilder sb = new StringBuilder("columns:\n");
      try (ResultSet rs = meta.getColumns(c.getCatalog(), schema, table, null)) {
        while (rs.next()) {
          sb.append("  ").append(rs.getString("COLUMN_NAME")).append(' ')
              .append(rs.getString("TYPE_NAME")).append('(').append(rs.getInt("COLUMN_SIZE"))
              .append(')').append("YES".equals(rs.getString("IS_NULLABLE")) ? " null" : " not null")
              .append('\n');
        }
      }
      sb.append("indexes:\n");
      try (ResultSet rs = meta.getIndexInfo(c.getCatalog(), schema, table, false, true)) {
        while (rs.next()) {
          if (rs.getString("INDEX_NAME") != null) {
            sb.append("  ").append(rs.getString("INDEX_NAME")).append(" on ")
                .append(rs.getString("COLUMN_NAME"))
                .append(rs.getBoolean("NON_UNIQUE") ? "" : " unique").append('\n');
          }
        }
      }
      return sb.toString();
    } finally {
      // read-only metadata calls; nothing to roll back beyond connect()'s transaction
    }
  }

  private String query(HolmesProperties.Database db, String sql) throws SQLException {
    try (Connection c = connect(db);
        Statement st = c.createStatement()) {
      st.setQueryTimeout(20);
      st.setMaxRows(MAX_ROWS);
      try (ResultSet rs = st.executeQuery(sql)) {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
          sb.append(i > 1 ? "\t" : "").append(md.getColumnLabel(i));
        }
        sb.append('\n');
        int rows = 0;
        while (rs.next()) {
          for (int i = 1; i <= cols; i++) {
            String v = rs.getString(i);
            sb.append(i > 1 ? "\t" : "")
                .append(v == null ? "NULL" : v.length() > 300 ? v.substring(0, 300) + "…" : v);
          }
          sb.append('\n');
          rows++;
        }
        sb.append('(').append(rows).append(" rows)");
        return sb.toString();
      } finally {
        c.rollback();
      }
    }
  }

  private static Connection connect(HolmesProperties.Database db) throws SQLException {
    DriverManager.setLoginTimeout(10);
    Connection c = DriverManager.getConnection(db.getUrl(), db.getUsername(), db.getPassword());
    c.setAutoCommit(false);
    c.setReadOnly(true);
    return c;
  }

  @Override
  public ToolsetHealth health() {
    StringBuilder sb = new StringBuilder();
    boolean ok = true;
    for (HolmesProperties.Database db : databases.values()) {
      try (Connection c = connect(db)) {
        sb.append(db.getName()).append(": ok (")
            .append(c.getMetaData().getDatabaseProductName()).append("). ");
      } catch (SQLException e) {
        ok = false;
        sb.append(db.getName()).append(": ").append(e.getMessage()).append(". ");
      }
    }
    return new ToolsetHealth(ok, sb.toString().strip());
  }
}
