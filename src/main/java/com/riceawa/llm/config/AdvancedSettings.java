package com.riceawa.llm.config;

public final class AdvancedSettings {
    ToolCallSettings toolCall;
    HttpSettings http;
    SchedulerSettings concurrency;
    RetrySettings retry;
    LogSettings logSettings;

    private AdvancedSettings() {}

    public static AdvancedSettings defaults() {
        AdvancedSettings a = new AdvancedSettings();
        a.toolCall = ToolCallSettings.defaults();
        a.http = HttpSettings.defaults();
        a.concurrency = SchedulerSettings.defaults();
        a.retry = RetrySettings.defaults();
        a.logSettings = LogSettings.defaults();
        return a;
    }

    public ToolCallSettings getToolCall() { return toolCall; }
    public HttpSettings getHttp() { return http; }
    public SchedulerSettings getConcurrency() { return concurrency; }
    public RetrySettings getRetry() { return retry; }
    public LogSettings getLogSettings() { return logSettings; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final AdvancedSettings instance = new AdvancedSettings();

        public Builder cloneFrom(AdvancedSettings a) {
            instance.toolCall = a.toolCall;
            instance.http = a.http;
            instance.concurrency = a.concurrency;
            instance.retry = a.retry;
            instance.logSettings = a.logSettings;
            return this;
        }

        public Builder toolCall(ToolCallSettings v) { instance.toolCall = v; return this; }
        public Builder http(HttpSettings v) { instance.http = v; return this; }
        public Builder concurrency(SchedulerSettings v) { instance.concurrency = v; return this; }
        public Builder retry(RetrySettings v) { instance.retry = v; return this; }
        public Builder logSettings(LogSettings v) { instance.logSettings = v; return this; }

        public AdvancedSettings build() { return instance; }
    }

    public static final class ToolCallSettings {
        boolean enableRecursive;
        int maxDepth;

        private ToolCallSettings() {}

        public static ToolCallSettings defaults() {
            ToolCallSettings t = new ToolCallSettings();
            t.enableRecursive = ConfigDefaults.DEFAULT_ENABLE_RECURSIVE_TOOL_CALLS;
            t.maxDepth = ConfigDefaults.DEFAULT_MAX_TOOL_CALL_DEPTH;
            return t;
        }

        public boolean isEnableRecursive() { return enableRecursive; }
        public int getMaxDepth() { return maxDepth; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private final ToolCallSettings instance = new ToolCallSettings();

            public Builder cloneFrom(ToolCallSettings t) {
                instance.enableRecursive = t.enableRecursive;
                instance.maxDepth = t.maxDepth;
                return this;
            }

            public Builder enableRecursive(boolean v) { instance.enableRecursive = v; return this; }
            public Builder maxDepth(int v) { instance.maxDepth = v; return this; }

            public ToolCallSettings build() { return instance; }
        }
    }

    public static final class HttpSettings {
        int connectTimeoutMs;
        int readTimeoutMs;
        int writeTimeoutMs;
        int maxIdleConnections;
        int keepAliveDurationMs;

        private HttpSettings() {}

        public static HttpSettings defaults() {
            HttpSettings h = new HttpSettings();
            h.connectTimeoutMs = 30000;
            h.readTimeoutMs = 60000;
            h.writeTimeoutMs = 60000;
            h.maxIdleConnections = 20;
            h.keepAliveDurationMs = 300000;
            return h;
        }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public int getWriteTimeoutMs() { return writeTimeoutMs; }
        public int getMaxIdleConnections() { return maxIdleConnections; }
        public int getKeepAliveDurationMs() { return keepAliveDurationMs; }
    }

    public static final class SchedulerSettings {
        int maxConcurrentRequests;
        int queueCapacity;
        int requestTimeoutMs;
        int corePoolSize;
        int maximumPoolSize;
        int keepAliveTimeMs;

        private SchedulerSettings() {}

        public static SchedulerSettings defaults() {
            SchedulerSettings s = new SchedulerSettings();
            s.maxConcurrentRequests = 10;
            s.queueCapacity = 50;
            s.requestTimeoutMs = 30000;
            s.corePoolSize = 5;
            s.maximumPoolSize = 20;
            s.keepAliveTimeMs = 60000;
            return s;
        }

        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public int getQueueCapacity() { return queueCapacity; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getKeepAliveTimeMs() { return keepAliveTimeMs; }
    }

    public static final class RetrySettings {
        boolean enabled;
        int maxAttempts;
        int delayMs;
        double backoffMultiplier;

        private RetrySettings() {}

        public static RetrySettings defaults() {
            RetrySettings r = new RetrySettings();
            r.enabled = true;
            r.maxAttempts = 3;
            r.delayMs = 1000;
            r.backoffMultiplier = 2.0;
            return r;
        }

        public boolean isEnabled() { return enabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public int getDelayMs() { return delayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
    }

    public static final class LogSettings {
        String level;
        boolean file;
        boolean console;
        boolean json;
        boolean async;
        int maxFileSize;
        int maxBackupFiles;
        int retentionDays;
        boolean llmRequestLog;
        boolean logFullBodies;
        int maxContentLength;
        boolean debugMode;

        private LogSettings() {}

        public static LogSettings defaults() {
            LogSettings l = new LogSettings();
            l.level = "INFO";
            l.file = true;
            l.console = true;
            l.json = true;
            l.async = true;
            l.maxFileSize = 10 * 1024 * 1024;
            l.maxBackupFiles = 5;
            l.retentionDays = 30;
            l.llmRequestLog = true;
            l.logFullBodies = false;
            l.maxContentLength = 2048;
            l.debugMode = false;
            return l;
        }

        public String getLevel() { return level; }
        public boolean isFile() { return file; }
        public boolean isConsole() { return console; }
        public boolean isJson() { return json; }
        public boolean isAsync() { return async; }
        public int getMaxFileSize() { return maxFileSize; }
        public int getMaxBackupFiles() { return maxBackupFiles; }
        public int getRetentionDays() { return retentionDays; }
        public boolean isLlmRequestLog() { return llmRequestLog; }
        public boolean isLogFullBodies() { return logFullBodies; }
        public int getMaxContentLength() { return maxContentLength; }
        public boolean isDebugMode() { return debugMode; }
    }
}
