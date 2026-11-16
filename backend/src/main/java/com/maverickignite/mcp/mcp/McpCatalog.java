package com.maverickignite.mcp.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Curated, one-click installable MCP servers. Arguments, env values, URL and headers may contain
 * {@code {{param}}} placeholders that are filled from the install form. Optional params left blank drop the
 * env var / header they feed. For everything else, the Registry tab searches the official MCP Registry.
 */
@Component
public class McpCatalog {

    public record Param(String key, String label, String placeholder, boolean secret, boolean required,
            String help) {
    }

    public record Entry(String id, String name, String description, String category, String icon, String color,
            TransportType transport, String command, List<String> args, Map<String, String> env, String url,
            Map<String, String> headers, List<Param> params, String docsUrl, String runtime) {
    }

    // ------------------------------------------------------------------ builders

    private static Param p(String key, String label, String placeholder, boolean secret, String help) {
        return new Param(key, label, placeholder, secret, true, help);
    }

    private static Param secret(String key, String label, String placeholder, String help) {
        return new Param(key, label, placeholder, true, true, help);
    }

    private static Param opt(String key, String label, String placeholder, String help) {
        return new Param(key, label, placeholder, false, false, help);
    }

    private static Map<String, String> env(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Entry npx(String id, String name, String desc, String category, String icon, String color,
            List<String> args, Map<String, String> env, List<Param> params, String docs) {
        List<String> all = new ArrayList<>(List.of("-y"));
        all.addAll(args);
        return new Entry(id, name, desc, category, icon, color, TransportType.STDIO, "npx", all, env, null, Map.of(),
                params, docs, "Node.js");
    }

    private static Entry uvx(String id, String name, String desc, String category, String icon, String color,
            List<String> args, Map<String, String> env, List<Param> params, String docs) {
        return new Entry(id, name, desc, category, icon, color, TransportType.STDIO, "uvx", args, env, null, Map.of(),
                params, docs, "Python (uv)");
    }

    private static Entry remote(String id, String name, String desc, String category, String icon, String color,
            String url, Map<String, String> headers, List<Param> params, String docs, String runtime) {
        return new Entry(id, name, desc, category, icon, color, TransportType.STREAMABLE_HTTP, null, List.of(),
                Map.of(), url, headers, params, docs, runtime);
    }

    /** Remote servers that require an OAuth sign-in: bridged through mcp-remote (opens a browser on the host). */
    private static Entry oauth(String id, String name, String desc, String category, String icon, String color,
            String url, String docs) {
        return new Entry(id, name, desc, category, icon, color, TransportType.STDIO, "npx",
                List.of("-y", "mcp-remote", url), Map.of(), null, Map.of(), List.of(), docs, "Remote · OAuth sign-in");
    }

    private static Entry mail(String id, String name, String desc, String color, String imapHost, String smtpHost,
            String smtpPort, String smtpSecure, String passwordHelp) {
        return npx(id, name, desc, "Email", "mail", color, List.of("mcp-mail-server"),
                env("IMAP_HOST", imapHost, "IMAP_PORT", "993", "IMAP_SECURE", "true",
                        "SMTP_HOST", smtpHost, "SMTP_PORT", smtpPort, "SMTP_SECURE", smtpSecure,
                        "EMAIL_USER", "{{email}}", "EMAIL_ADDRESS", "{{email}}", "EMAIL_PASS", "{{password}}"),
                List.of(p("email", "Email address", "you@example.com", false, null),
                        secret("password", "App password", "xxxx xxxx xxxx xxxx", passwordHelp)),
                "https://github.com/yunfeizhu/mcp-mail-server");
    }

    // ------------------------------------------------------------------ catalog

    private final List<Entry> entries = List.of(
            // ---------- Developer
            remote("deepwiki", "DeepWiki", "Ask questions about any public GitHub repository's docs & code.",
                    "Developer", "book-open", "#8b5cf6", "https://mcp.deepwiki.com/mcp", Map.of(), List.of(),
                    "https://docs.devin.ai/work-with-devin/deepwiki-mcp", "Remote · no auth"),
            remote("context7", "Context7", "Up-to-date library documentation and code examples for any framework.",
                    "Developer", "library", "#10b981", "https://mcp.context7.com/mcp", Map.of(), List.of(),
                    "https://github.com/upstash/context7", "Remote · no auth"),
            remote("ms-learn", "Microsoft Learn", "Search official Microsoft & Azure documentation and code samples.",
                    "Developer", "book-open", "#0078d4", "https://learn.microsoft.com/api/mcp", Map.of(), List.of(),
                    "https://github.com/MicrosoftDocs/mcp", "Remote · no auth"),
            remote("aws-knowledge", "AWS Knowledge", "AWS documentation, API references, What's New and best practices.",
                    "Cloud", "cloud", "#ff9900", "https://knowledge-mcp.global.api.aws", Map.of(), List.of(),
                    "https://awslabs.github.io/mcp/servers/aws-knowledge-mcp-server", "Remote · no auth"),
            remote("huggingface", "Hugging Face", "Search models, datasets, Spaces and papers on the Hugging Face Hub.",
                    "AI", "sparkles", "#ffcc00", "https://huggingface.co/mcp", Map.of(), List.of(),
                    "https://huggingface.co/settings/mcp", "Remote · no auth"),
            remote("github", "GitHub", "Repos, issues, pull requests, actions and code search via GitHub's official server.",
                    "Developer", "github", "#e5e7eb", "https://api.githubcopilot.com/mcp/",
                    Map.of("Authorization", "Bearer {{token}}"),
                    List.of(secret("token", "Personal access token", "ghp_…", "Create one at github.com/settings/tokens")),
                    "https://github.com/github/github-mcp-server", "Remote · token"),
            npx("circleci", "CircleCI", "Inspect pipelines, find flaky tests and read build failure logs.", "Developer",
                    "workflow", "#343434", List.of("@circleci/mcp-server-circleci"), env("CIRCLECI_TOKEN", "{{token}}"),
                    List.of(secret("token", "CircleCI API token", "CCIPAT_…", null)),
                    "https://github.com/CircleCI-Public/mcp-server-circleci"),
            npx("figma", "Figma", "Read Figma files, frames and design tokens to inform implementation.", "Design",
                    "pen-tool", "#a259ff", List.of("figma-developer-mcp", "--figma-api-key={{key}}", "--stdio"), Map.of(),
                    List.of(secret("key", "Figma personal access token", "figd_…", null)),
                    "https://github.com/GLips/Figma-Context-MCP"),
            npx("shopify-dev", "Shopify Dev", "Shopify docs, GraphQL schema introspection and API guidance.", "Developer",
                    "shopping-bag", "#95bf47", List.of("@shopify/dev-mcp@latest"), Map.of(), List.of(),
                    "https://shopify.dev/docs/apps/build/devmcp"),
            oauth("sentry", "Sentry", "Issues, errors, stack traces and releases from Sentry.", "Developer", "bug", "#362d59",
                    "https://mcp.sentry.dev/mcp", "https://docs.sentry.io/product/sentry-mcp/"),
            oauth("linear", "Linear", "Search, create and update Linear issues, projects and cycles.", "Productivity",
                    "list-tree", "#5e6ad2", "https://mcp.linear.app/sse", "https://linear.app/docs/mcp"),
            oauth("atlassian", "Atlassian (Jira & Confluence)", "Jira issues and Confluence pages via Atlassian's remote server.",
                    "Productivity", "notebook", "#0052cc", "https://mcp.atlassian.com/v1/sse",
                    "https://support.atlassian.com/rovo/docs/getting-started-with-the-atlassian-remote-mcp-server/"),
            oauth("asana", "Asana", "Tasks, projects and goals from Asana.", "Productivity", "list-tree", "#f06a6a",
                    "https://mcp.asana.com/sse", "https://developers.asana.com/docs/using-asanas-mcp-server"),
            oauth("notion-remote", "Notion (sign-in)", "Notion workspace via Notion's hosted server — sign in with OAuth.",
                    "Productivity", "notebook", "#d4d4d8", "https://mcp.notion.com/mcp",
                    "https://developers.notion.com/docs/mcp"),
            new Entry("mcp-remote", "Any remote server (OAuth)",
                    "Connect any remote MCP URL that needs an OAuth sign-in, bridged through mcp-remote.", "Developer",
                    "plug", "#64748b", TransportType.STDIO, "npx", List.of("-y", "mcp-remote", "{{url}}"), Map.of(), null,
                    Map.of(), List.of(p("url", "Server URL", "https://example.com/mcp", false,
                            "A browser window opens on the backend host to sign in")),
                    "https://github.com/geelen/mcp-remote", "Remote · OAuth sign-in"),

            // ---------- Data
            npx("postgres", "PostgreSQL", "Inspect schemas and run read-only SQL queries against a Postgres database.",
                    "Data", "database", "#3b82f6", List.of("@modelcontextprotocol/server-postgres", "{{connectionString}}"),
                    Map.of(), List.of(secret("connectionString", "Connection string", "postgresql://user:pass@localhost:5432/db", null)),
                    "https://www.npmjs.com/package/@modelcontextprotocol/server-postgres"),
            npx("mongodb", "MongoDB", "Query collections, inspect schemas and indexes, run aggregations.", "Data", "leaf",
                    "#00ed64", List.of("@mongodb-js/mongodb-mcp-server", "--readOnly"),
                    env("MDB_MCP_CONNECTION_STRING", "{{connectionString}}"),
                    List.of(secret("connectionString", "Connection string", "mongodb+srv://user:pass@cluster/db", null)),
                    "https://github.com/mongodb-js/mongodb-mcp-server"),
            npx("elasticsearch", "Elasticsearch", "List indices, inspect mappings and run searches against Elasticsearch.",
                    "Data", "search", "#00bfb3", List.of("@elastic/mcp-server-elasticsearch"),
                    env("ES_URL", "{{url}}", "ES_API_KEY", "{{apiKey}}"),
                    List.of(p("url", "Elasticsearch URL", "https://localhost:9200", false, null),
                            secret("apiKey", "API key", "base64 API key", null)),
                    "https://github.com/elastic/mcp-server-elasticsearch"),
            uvx("sqlite", "SQLite", "Query and analyse a local SQLite database, with business-insight memos.", "Data",
                    "database", "#0ea5e9", List.of("mcp-server-sqlite", "--db-path", "{{dbPath}}"), Map.of(),
                    List.of(p("dbPath", "Database file", "/path/to/data.db", false, null)),
                    "https://github.com/modelcontextprotocol/servers-archived/tree/main/src/sqlite"),
            uvx("redis", "Redis", "Browse keys and work with Redis data structures in natural language.", "Data", "layers",
                    "#ef4444", List.of("--from", "redis-mcp-server@latest", "redis-mcp-server", "--url", "{{url}}"),
                    Map.of(), List.of(secret("url", "Redis URL", "redis://localhost:6379/0", null)),
                    "https://github.com/redis/mcp-redis"),

            // ---------- Email
            mail("gmail", "Gmail", "Read, search and send Gmail over IMAP/SMTP using an app password.", "#ea4335",
                    "imap.gmail.com", "smtp.gmail.com", "465", "true",
                    "Google Account → Security → 2-Step Verification → App passwords"),
            mail("yahoo-mail", "Yahoo Mail", "Read, search and send Yahoo Mail over IMAP/SMTP using an app password.",
                    "#6001d2", "imap.mail.yahoo.com", "smtp.mail.yahoo.com", "465", "true",
                    "Yahoo Account Security → Generate app password"),
            mail("outlook-mail", "Outlook / Hotmail", "Read, search and send Outlook.com or Microsoft 365 mail over IMAP/SMTP.",
                    "#0078d4", "outlook.office365.com", "smtp.office365.com", "587", "false",
                    "Use an app password if 2FA is on; some work tenants disable IMAP/SMTP AUTH"),
            npx("imap-mail", "Email (any IMAP/SMTP)", "Any mailbox that supports IMAP and SMTP — iCloud, Zoho, Fastmail, self-hosted…",
                    "Email", "mail", "#64748b", List.of("mcp-mail-server"),
                    env("IMAP_HOST", "{{imapHost}}", "IMAP_PORT", "{{imapPort}}", "IMAP_SECURE", "true",
                            "SMTP_HOST", "{{smtpHost}}", "SMTP_PORT", "{{smtpPort}}", "SMTP_SECURE", "true",
                            "EMAIL_USER", "{{email}}", "EMAIL_ADDRESS", "{{email}}", "EMAIL_PASS", "{{password}}"),
                    List.of(p("email", "Email address", "you@example.com", false, null),
                            secret("password", "Password / app password", "", null),
                            p("imapHost", "IMAP host", "imap.example.com", false, null),
                            p("imapPort", "IMAP port", "993", false, null),
                            p("smtpHost", "SMTP host", "smtp.example.com", false, null),
                            p("smtpPort", "SMTP port", "465", false, null)),
                    "https://github.com/yunfeizhu/mcp-mail-server"),
            npx("gmail-oauth", "Gmail (Google sign-in)",
                    "Gmail API with labels, filters and threads via Google OAuth. Needs ~/.gmail-mcp/gcp-oauth.keys.json "
                            + "and a one-time `npx @gongrzhe/server-gmail-autoauth-mcp auth` on the backend host.",
                    "Email", "mail", "#34a853", List.of("@gongrzhe/server-gmail-autoauth-mcp"), Map.of(), List.of(),
                    "https://github.com/GongRzhe/Gmail-MCP-Server"),

            // ---------- Web & search
            uvx("fetch", "Fetch", "Fetch web pages and convert them to clean markdown.", "Web", "globe", "#06b6d4",
                    List.of("mcp-server-fetch"), Map.of(), List.of(),
                    "https://github.com/modelcontextprotocol/servers/tree/main/src/fetch"),
            npx("brave-search", "Brave Search", "Private web and local search powered by the Brave Search API.", "Web",
                    "search", "#fb923c", List.of("@brave/brave-search-mcp-server"), env("BRAVE_API_KEY", "{{apiKey}}"),
                    List.of(secret("apiKey", "Brave API key", "BSA…", "Get one at brave.com/search/api")),
                    "https://github.com/brave/brave-search-mcp-server"),
            npx("tavily", "Tavily", "AI-optimised web search, extraction and crawling.", "Web", "search", "#3b82f6",
                    List.of("tavily-mcp@latest"), env("TAVILY_API_KEY", "{{apiKey}}"),
                    List.of(secret("apiKey", "Tavily API key", "tvly-…", null)), "https://github.com/tavily-ai/tavily-mcp"),
            npx("perplexity", "Perplexity", "Real-time web answers with citations from Perplexity Sonar.", "Web", "search",
                    "#20808d", List.of("@perplexity-ai/mcp-server"), env("PERPLEXITY_API_KEY", "{{apiKey}}"),
                    List.of(secret("apiKey", "Perplexity API key", "pplx-…", null)),
                    "https://github.com/perplexityai/modelcontextprotocol"),
            npx("firecrawl", "Firecrawl", "Scrape, crawl and extract structured data from websites.", "Web", "flame",
                    "#ff6b35", List.of("firecrawl-mcp"), env("FIRECRAWL_API_KEY", "{{apiKey}}"),
                    List.of(secret("apiKey", "Firecrawl API key", "fc-…", null)), "https://github.com/firecrawl/firecrawl-mcp-server"),
            npx("playwright", "Playwright", "Drive a real browser: navigate, click, fill forms and take snapshots.", "Web",
                    "monitor", "#22c55e", List.of("@playwright/mcp@latest"), Map.of(), List.of(),
                    "https://github.com/microsoft/playwright-mcp"),
            npx("puppeteer", "Puppeteer", "Headless Chrome automation: screenshots, navigation and scraping.", "Web",
                    "monitor", "#40b5a4", List.of("@modelcontextprotocol/server-puppeteer"), Map.of(), List.of(),
                    "https://www.npmjs.com/package/@modelcontextprotocol/server-puppeteer"),
            npx("browserbase", "Browserbase", "Cloud browsers for scraping and automation at scale.", "Web", "monitor",
                    "#f03603", List.of("@browserbasehq/mcp"),
                    env("BROWSERBASE_API_KEY", "{{apiKey}}", "BROWSERBASE_PROJECT_ID", "{{projectId}}"),
                    List.of(secret("apiKey", "API key", "bb_live_…", null), p("projectId", "Project ID", "", false, null)),
                    "https://github.com/browserbase/mcp-server-browserbase"),
            npx("youtube-transcript", "YouTube Transcripts", "Fetch transcripts/subtitles of YouTube videos.", "Web",
                    "video", "#ff0000", List.of("@kimtaeyoon83/mcp-server-youtube-transcript"), Map.of(), List.of(),
                    "https://github.com/kimtaeyoon83/mcp-server-youtube-transcript"),
            npx("google-maps", "Google Maps", "Geocoding, places search, directions and distance matrix.", "Web", "map",
                    "#34a853", List.of("@modelcontextprotocol/server-google-maps"), env("GOOGLE_MAPS_API_KEY", "{{apiKey}}"),
                    List.of(secret("apiKey", "Google Maps API key", "AIza…", null)),
                    "https://www.npmjs.com/package/@modelcontextprotocol/server-google-maps"),
            npx("apify", "Apify", "Run thousands of Apify Actors for scraping and data extraction.", "Web", "globe",
                    "#97d700", List.of("@apify/actors-mcp-server"), env("APIFY_TOKEN", "{{token}}"),
                    List.of(secret("token", "Apify API token", "apify_api_…", null)),
                    "https://github.com/apify/actors-mcp-server"),

            // ---------- Business
            npx("stripe", "Stripe", "Customers, payments, subscriptions, invoices and balances in Stripe.", "Business",
                    "credit-card", "#635bff", List.of("@stripe/mcp", "--tools=all", "--api-key={{apiKey}}"), Map.of(),
                    List.of(secret("apiKey", "Stripe secret key", "sk_test_… or rk_…", "Prefer a restricted key")),
                    "https://github.com/stripe/agent-toolkit"),
            npx("hubspot", "HubSpot", "Contacts, companies, deals and engagements from HubSpot CRM.", "Business",
                    "users", "#ff7a59", List.of("@hubspot/mcp-server"), env("PRIVATE_APP_ACCESS_TOKEN", "{{token}}"),
                    List.of(secret("token", "Private app access token", "pat-…", null)),
                    "https://developers.hubspot.com/mcp"),
            npx("twilio", "Twilio", "Messaging, voice and account APIs from Twilio.", "Communication", "phone",
                    "#f22f46", List.of("@twilio-alpha/mcp", "{{accountSid}}/{{apiKey}}:{{apiSecret}}"), Map.of(),
                    List.of(p("accountSid", "Account SID", "AC…", false, null), p("apiKey", "API key SID", "SK…", false, null),
                            secret("apiSecret", "API key secret", "", null)),
                    "https://github.com/twilio-labs/mcp"),
            npx("slack", "Slack", "Read channels and threads, post messages in your Slack workspace.", "Communication",
                    "message-square", "#e11d48", List.of("@modelcontextprotocol/server-slack"),
                    env("SLACK_BOT_TOKEN", "{{botToken}}", "SLACK_TEAM_ID", "{{teamId}}"),
                    List.of(secret("botToken", "Bot token", "xoxb-…", null), p("teamId", "Team ID", "T0123456", false, null)),
                    "https://github.com/modelcontextprotocol/servers-archived/tree/main/src/slack"),
            npx("notion", "Notion (token)", "Search, read and update pages and databases with an integration token.",
                    "Productivity", "notebook", "#d4d4d8", List.of("@notionhq/notion-mcp-server"),
                    env("NOTION_TOKEN", "{{token}}"),
                    List.of(secret("token", "Integration token", "ntn_…", "Create an internal integration in Notion")),
                    "https://github.com/makenotion/notion-mcp-server"),

            // ---------- Productivity
            npx("filesystem", "Filesystem", "Read, search and edit files inside a folder you choose.", "Productivity",
                    "folder", "#f59e0b", List.of("@modelcontextprotocol/server-filesystem", "{{path}}"), Map.of(),
                    List.of(p("path", "Allowed folder", "/Users/me/Documents", false, "The server can only access this folder")),
                    "https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem"),
            npx("memory", "Memory", "A persistent knowledge graph the assistant can write to and recall from.",
                    "Productivity", "brain", "#ec4899", List.of("@modelcontextprotocol/server-memory"), Map.of(), List.of(),
                    "https://github.com/modelcontextprotocol/servers/tree/main/src/memory"),
            npx("sequential-thinking", "Sequential Thinking", "Structured, step-by-step reasoning tool for complex problems.",
                    "Productivity", "list-tree", "#a78bfa", List.of("@modelcontextprotocol/server-sequential-thinking"),
                    Map.of(), List.of(), "https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking"),
            uvx("time", "Time", "Current time and timezone conversions.", "Productivity", "clock", "#a3e635",
                    List.of("mcp-server-time"), Map.of(), List.of(),
                    "https://github.com/modelcontextprotocol/servers/tree/main/src/time"),
            uvx("git-local", "Git", "Read history, diffs and branches of a local git repository.", "Developer",
                    "git-branch", "#f97316", List.of("mcp-server-git", "--repository", "{{repo}}"), Map.of(),
                    List.of(p("repo", "Repository path", "/path/to/repo", false, null)),
                    "https://github.com/modelcontextprotocol/servers/tree/main/src/git"),

            // ---------- Infrastructure & cloud
            uvx("docker", "Docker", "Inspect and manage containers, images, volumes and networks. Set DOCKER_HOST to manage a remote engine (e.g. ssh://user@host).",
                    "Infrastructure", "container", "#1d63ed", List.of("mcp-server-docker"),
                    env("DOCKER_HOST", "{{dockerHost}}"),
                    List.of(opt("dockerHost", "Docker host (optional)", "ssh://user@host", "Leave blank for the local engine")),
                    "https://github.com/ckreiling/mcp-server-docker"),
            new Entry("docker-gateway", "Docker MCP Gateway",
                    "Every server enabled in Docker Desktop's MCP Toolkit, served through one gateway.", "Infrastructure",
                    "container", "#2496ed", TransportType.STDIO, "docker", List.of("mcp", "gateway", "run"), Map.of(), null,
                    Map.of(), List.of(), "https://docs.docker.com/ai/mcp-catalog-and-toolkit/mcp-gateway/", "Docker Desktop"),
            uvx("portainer", "Portainer", "Environments, stacks, containers and users via Portainer's official server. "
                    + "The MCP version must match your Portainer minor version (2.41+).",
                    "Infrastructure", "container", "#13bef9", List.of("--from", "mcp-portainer~={{version}}.0", "mcp-portainer"),
                    env("PORTAINER_URL", "{{url}}", "PORTAINER_API_KEY", "{{apiKey}}"),
                    List.of(p("url", "Portainer URL", "https://portainer.example.com", false, null),
                            secret("apiKey", "Portainer API key", "ptr_…", "My Account → Access tokens"),
                            p("version", "Portainer minor version", "2.45", false, "e.g. 2.45 — Portainer 2.41 or newer is required")),
                    "https://github.com/portainer/portainer-mcp"),
            npx("kubernetes", "Kubernetes", "Inspect pods, deployments, logs and events in your current kube context.",
                    "Infrastructure", "container", "#60a5fa", List.of("mcp-server-kubernetes"), Map.of(), List.of(),
                    "https://github.com/Flux159/mcp-server-kubernetes"),
            npx("heroku", "Heroku", "Apps, dynos, add-ons, logs and Postgres on Heroku.", "Cloud", "cloud", "#430098",
                    List.of("@heroku/mcp-server"), env("HEROKU_API_KEY", "{{apiKey}}"),
                    List.of(secret("apiKey", "Heroku API key", "", "heroku authorizations:create")),
                    "https://github.com/heroku/heroku-mcp-server"),
            npx("azure", "Azure", "Azure resources: storage, Cosmos DB, monitor logs, App Config and more (uses `az login`).",
                    "Cloud", "cloud", "#0078d4", List.of("@azure/mcp@latest", "server", "start"), Map.of(), List.of(),
                    "https://github.com/microsoft/mcp/tree/main/servers/Azure.Mcp.Server"),
            npx("desktop-commander", "Desktop Commander", "Terminal commands and file editing on the backend host. Powerful — use with care.",
                    "Infrastructure", "terminal", "#64748b", List.of("@wonderwhy-er/desktop-commander@latest"), Map.of(),
                    List.of(), "https://github.com/wonderwhy-er/DesktopCommanderMCP"),

            // ---------- Demo
            npx("everything", "Everything (demo)", "Reference server exercising every MCP feature — great for testing.",
                    "Developer", "sparkles", "#f472b6", List.of("@modelcontextprotocol/server-everything"), Map.of(),
                    List.of(), "https://github.com/modelcontextprotocol/servers/tree/main/src/everything"));

    public List<Entry> all() {
        return entries;
    }

    public Optional<Entry> find(String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
