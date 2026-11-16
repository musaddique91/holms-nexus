package com.maverickignite.mcp.mcp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.maverickignite.mcp.common.ApiException;
import com.maverickignite.mcp.common.Json;
import com.maverickignite.mcp.common.NexusProperties;

/**
 * Browses the official MCP Registry (registry.modelcontextprotocol.io) and turns any listed server — remote
 * endpoint, npm, PyPI or OCI image — into a Nexus server configuration. The registry schema is read leniently as
 * maps so newer schema revisions keep working.
 */
@Service
@SuppressWarnings("unchecked")
public class McpRegistryService {

    private static final Logger log = LoggerFactory.getLogger(McpRegistryService.class);

    private static final String BASE = "https://registry.modelcontextprotocol.io";

    private static final Pattern VAR = Pattern.compile("\\{([A-Za-z0-9_\\-]+)}");

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** A value the user must (or may) provide to install an option. */
    public record Field(String key, String label, String description, boolean required, boolean secret,
            String defaultValue) {
    }

    /** One way of running a registry server. */
    public record InstallOption(String id, String kind, String label, TransportType transport, String preview,
            List<Field> fields) {
    }

    public record RegistryServer(String name, String title, String description, String version, String websiteUrl,
            String repositoryUrl, String iconUrl, List<InstallOption> options) {
    }

    public record SearchResult(List<RegistryServer> servers, String nextCursor) {
    }

    public record InstallRequest(String name, String optionId, Map<String, String> values, String displayName) {
    }

    private final RestClient http;

    private final StringRedisTemplate redis;

    private final NexusProperties properties;

    public McpRegistryService(StringRedisTemplate redis, NexusProperties properties) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory();
        rf.setReadTimeout(Duration.ofSeconds(45));
        this.http = RestClient.builder().baseUrl(BASE).requestFactory(rf).build();
        this.redis = redis;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ search

    public SearchResult search(String query, String cursor) {
        StringBuilder uri = new StringBuilder("/v0/servers?version=latest&limit=40");
        if (query != null && !query.isBlank()) {
            uri.append("&search=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        }
        if (cursor != null && !cursor.isBlank()) {
            uri.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }
        Map<String, Object> body = cachedGet(uri.toString());
        List<RegistryServer> servers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (body.get("servers") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> server = (Map<String, Object>) ((Map<String, Object>) o).get("server");
                if (server != null && seen.add(String.valueOf(server.get("name")))) {
                    RegistryServer view = toView(server);
                    if (!view.options().isEmpty()) {
                        servers.add(view);
                    }
                }
            }
        }
        Object next = body.get("metadata") instanceof Map<?, ?> m ? m.get("nextCursor") : null;
        return new SearchResult(servers, next == null ? null : String.valueOf(next));
    }

    private Map<String, Object> cachedGet(String uri) {
        String key = properties.redisPrefix() + "registry:" + uri;
        try {
            String hit = redis.opsForValue().get(key);
            if (hit != null) {
                return Json.readMap(hit);
            }
        }
        catch (Exception ex) {
            log.debug("Registry cache unavailable: {}", ex.getMessage());
        }
        String json;
        try {
            json = http.get().uri(uri).retrieve().body(String.class);
        }
        catch (Exception ex) {
            throw ApiException.badRequest("MCP Registry is unreachable: " + ex.getMessage());
        }
        try {
            redis.opsForValue().set(key, json, CACHE_TTL);
        }
        catch (Exception ignored) {
            // cache is best effort
        }
        return Json.readMap(json);
    }

    // ------------------------------------------------------------------ mapping

    private RegistryServer toView(Map<String, Object> s) {
        String name = str(s.get("name"));
        String title = s.get("title") != null ? str(s.get("title")) : prettyName(name);
        String repo = s.get("repository") instanceof Map<?, ?> r ? str(r.get("url")) : null;
        String icon = s.get("icons") instanceof List<?> icons && !icons.isEmpty() && icons.getFirst() instanceof Map<?, ?> i
                ? str(i.get("src")) : null;
        return new RegistryServer(name, title, str(s.get("description")), str(s.get("version")),
                str(s.get("websiteUrl")), repo, icon, options(s));
    }

    private static List<InstallOption> options(Map<String, Object> s) {
        List<InstallOption> out = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> remote : list(s.get("remotes"))) {
            String type = str(remote.get("type"));
            TransportType transport = "sse".equals(type) ? TransportType.SSE : TransportType.STREAMABLE_HTTP;
            List<Field> fields = new ArrayList<>(templateFields(str(remote.get("url")), remote.get("variables")));
            for (Map<String, Object> h : list(remote.get("headers"))) {
                String hn = str(h.get("name"));
                fields.add(new Field("header:" + hn, hn, str(h.get("description")), bool(h.get("isRequired")),
                        bool(h.get("isSecret")) || hn.equalsIgnoreCase("authorization"), str(h.get("value"))));
            }
            out.add(new InstallOption("remote-" + i++, "remote", "Remote · " + (transport == TransportType.SSE ? "SSE" : "HTTP"),
                    transport, str(remote.get("url")), fields));
        }
        i = 0;
        for (Map<String, Object> pkg : list(s.get("packages"))) {
            int idx = i++;
            String reg = str(pkg.get("registryType"));
            String transportType = pkg.get("transport") instanceof Map<?, ?> t ? str(t.get("type")) : "stdio";
            if (!"stdio".equals(transportType) || !Set.of("npm", "pypi", "oci").contains(reg)) {
                continue;
            }
            List<Field> fields = new ArrayList<>();
            for (Map<String, Object> arg : concat(list(pkg.get("runtimeArguments")), list(pkg.get("packageArguments")))) {
                fields.addAll(argFields(arg));
            }
            for (Map<String, Object> e : list(pkg.get("environmentVariables"))) {
                String en = str(e.get("name"));
                fields.add(new Field("env:" + en, en, str(e.get("description")), bool(e.get("isRequired")),
                        bool(e.get("isSecret")), str(e.get("default"))));
            }
            String label = switch (reg) {
                case "npm" -> "npm · npx";
                case "pypi" -> "PyPI · uvx";
                default -> "Docker image";
            };
            out.add(new InstallOption("package-" + idx, reg, label, TransportType.STDIO, previewOf(pkg), dedupe(fields)));
        }
        return out;
    }

    private static List<Field> argFields(Map<String, Object> arg) {
        List<Field> fields = new ArrayList<>();
        String value = str(arg.get("value"));
        if (value != null) {
            fields.addAll(templateFields(value, arg.get("variables")));
        }
        else if (arg.get("valueHint") != null || arg.get("name") != null) {
            String key = "arg:" + (arg.get("name") != null ? str(arg.get("name")) : str(arg.get("valueHint")));
            fields.add(new Field(key, str(arg.get("valueHint")) != null ? str(arg.get("valueHint")) : str(arg.get("name")),
                    str(arg.get("description")), bool(arg.get("isRequired")), bool(arg.get("isSecret")),
                    str(arg.get("default"))));
        }
        return fields;
    }

    private static List<Field> templateFields(String template, Object variables) {
        List<Field> fields = new ArrayList<>();
        if (template == null) {
            return fields;
        }
        Map<String, Object> vars = variables instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Matcher matcher = VAR.matcher(template);
        while (matcher.find()) {
            String v = matcher.group(1);
            Map<String, Object> meta = vars.get(v) instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
            fields.add(new Field("var:" + v, v, str(meta.get("description")), bool(meta.get("isRequired")),
                    bool(meta.get("isSecret")), str(meta.get("default"))));
        }
        return fields;
    }

    private static List<Field> dedupe(List<Field> fields) {
        Map<String, Field> byKey = new LinkedHashMap<>();
        fields.forEach(f -> byKey.putIfAbsent(f.key(), f));
        return new ArrayList<>(byKey.values());
    }

    private static String previewOf(Map<String, Object> pkg) {
        String reg = str(pkg.get("registryType"));
        String id = str(pkg.get("identifier"));
        String version = str(pkg.get("version"));
        return switch (reg) {
            case "npm" -> "npx -y " + id + (version != null ? "@" + version : "");
            case "pypi" -> "uvx " + id + (version != null ? "@" + version : "");
            default -> "docker run -i --rm " + image(id, version);
        };
    }

    // ------------------------------------------------------------------ install

    /** Builds a server request for the chosen option of a registry server. */
    public McpDtos.ServerRequest toServerRequest(InstallRequest req) {
        String encoded = URLEncoder.encode(req.name(), StandardCharsets.UTF_8);
        Map<String, Object> body = cachedGet("/v0/servers/" + encoded + "/versions/latest");
        Map<String, Object> s = (Map<String, Object>) body.getOrDefault("server", body);
        RegistryServer view = toView(s);
        InstallOption option = view.options().stream().filter(o -> o.id().equals(req.optionId())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown install option"));
        Map<String, String> values = req.values() == null ? Map.of() : req.values();
        for (Field f : option.fields()) {
            if (f.required() && blank(values.get(f.key())) && blank(f.defaultValue())) {
                throw ApiException.badRequest(f.label() + " is required");
            }
        }
        String displayName = blank(req.displayName()) ? view.title() : req.displayName().trim();
        String description = view.description();

        if (option.kind().equals("remote")) {
            Map<String, Object> remote = list(s.get("remotes")).get(Integer.parseInt(option.id().substring(7)));
            String url = substitute(str(remote.get("url")), values, option.fields());
            Map<String, String> headers = new LinkedHashMap<>();
            for (Field f : option.fields()) {
                if (f.key().startsWith("header:")) {
                    String v = valueOf(f, values);
                    if (!blank(v)) {
                        headers.put(f.key().substring(7), v);
                    }
                }
            }
            return new McpDtos.ServerRequest(displayName, description, option.transport(), null, List.of(), Map.of(),
                    url, headers, true, null, null, "registry:" + view.name());
        }

        Map<String, Object> pkg = list(s.get("packages")).get(Integer.parseInt(option.id().substring(8)));
        String reg = str(pkg.get("registryType"));
        String id = str(pkg.get("identifier"));
        String version = str(pkg.get("version"));
        Map<String, String> env = new LinkedHashMap<>();
        for (Field f : option.fields()) {
            if (f.key().startsWith("env:")) {
                String v = valueOf(f, values);
                if (!blank(v)) {
                    env.put(f.key().substring(4), v);
                }
            }
        }
        List<String> runtime = renderArgs(list(pkg.get("runtimeArguments")), values, option.fields());
        List<String> pkgArgs = renderArgs(list(pkg.get("packageArguments")), values, option.fields());
        List<String> args = new ArrayList<>();
        String command;
        switch (reg) {
            case "npm" -> {
                command = "npx";
                args.add("-y");
                args.addAll(runtime);
                args.add(id + (version != null ? "@" + version : ""));
            }
            case "pypi" -> {
                command = "uvx";
                args.addAll(runtime);
                args.add(id + (version != null ? "@" + version : ""));
            }
            default -> {
                command = "docker";
                args.addAll(List.of("run", "-i", "--rm"));
                args.addAll(runtime);
                env.keySet().forEach(k -> args.addAll(List.of("-e", k)));
                args.add(image(id, version));
            }
        }
        args.addAll(pkgArgs);
        return new McpDtos.ServerRequest(displayName, description, TransportType.STDIO, command, args, env, null,
                Map.of(), true, null, null, "registry:" + view.name());
    }

    private static List<String> renderArgs(List<Map<String, Object>> args, Map<String, String> values,
            List<Field> fields) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> arg : args) {
            String name = str(arg.get("name"));
            String value = str(arg.get("value"));
            boolean named = "named".equals(str(arg.get("type")));
            if (value != null) {
                Matcher m = VAR.matcher(value);
                boolean missing = false;
                while (m.find()) {
                    String v = fieldValue("var:" + m.group(1), values, fields);
                    missing |= blank(v);
                }
                if (missing && !bool(arg.get("isRequired"))) {
                    continue; // optional arg whose variable wasn't provided
                }
                value = substitute(value, values, fields);
            }
            else {
                value = fieldValue("arg:" + (name != null ? name : str(arg.get("valueHint"))), values, fields);
                if (blank(value) && !named) {
                    continue;
                }
            }
            if (named && name != null) {
                out.add(name.startsWith("-") ? name : "--" + name); // some entries omit the dashes
            }
            if (!blank(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private static String substitute(String template, Map<String, String> values, List<Field> fields) {
        if (template == null) {
            return null;
        }
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = fieldValue("var:" + m.group(1), values, fields);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String fieldValue(String key, Map<String, String> values, List<Field> fields) {
        String v = values.get(key);
        if (!blank(v)) {
            return v;
        }
        return fields.stream().filter(f -> f.key().equals(key)).map(Field::defaultValue).filter(d -> !blank(d))
                .findFirst().orElse(null);
    }

    private static String valueOf(Field f, Map<String, String> values) {
        String v = values.get(f.key());
        return blank(v) ? f.defaultValue() : v;
    }

    // ------------------------------------------------------------------ helpers

    private static String image(String id, String version) {
        if (id == null) {
            return "";
        }
        boolean hasTag = id.substring(id.lastIndexOf('/') + 1).contains(":") || id.contains("@sha256");
        return hasTag || version == null ? id : id + ":" + version;
    }

    private static String prettyName(String name) {
        if (name == null) {
            return "";
        }
        String last = name.substring(name.lastIndexOf('/') + 1).replaceAll("[-_]+", " ").replaceAll("(?i)\\bmcp\\b|\\bserver\\b", "").trim();
        if (last.isEmpty()) {
            last = name;
        }
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private static List<Map<String, Object>> list(Object o) {
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        return Boolean.TRUE.equals(o) || "true".equals(String.valueOf(o));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
