package com.riceawa.llm.config;

import java.util.HashSet;
import java.util.Set;

public final class SecuritySettings {
    private boolean enableExecuteCommand;
    private boolean executeCommandReturnFullOutput;
    private Set<String> executeCommandBlocklist;
    private int executeCommandMaxLength;
    private String wikiApiUrl;
    private Set<String> wikiAllowedHosts;

    private SecuritySettings() {}

    public static SecuritySettings defaults() {
        SecuritySettings s = new SecuritySettings();
        s.enableExecuteCommand = ConfigDefaults.DEFAULT_ENABLE_EXECUTE_COMMAND;
        s.executeCommandReturnFullOutput = ConfigDefaults.DEFAULT_EXECUTE_COMMAND_RETURN_FULL_OUTPUT;
        s.executeCommandBlocklist = ConfigDefaults.createDefaultExecuteCommandBlocklist();
        s.executeCommandMaxLength = ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH;
        s.wikiApiUrl = ConfigDefaults.DEFAULT_WIKI_API_URL;
        s.wikiAllowedHosts = ConfigDefaults.createDefaultWikiAllowedHosts();
        return s;
    }

    public boolean isEnableExecuteCommand() { return enableExecuteCommand; }
    public boolean isExecuteCommandReturnFullOutput() { return executeCommandReturnFullOutput; }
    public Set<String> getExecuteCommandBlocklist() { return new HashSet<>(executeCommandBlocklist); }
    public int getExecuteCommandMaxLength() { return executeCommandMaxLength; }
    public String getWikiApiUrl() { return wikiApiUrl; }
    public Set<String> getWikiAllowedHosts() { return new HashSet<>(wikiAllowedHosts); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final SecuritySettings instance = new SecuritySettings();

        public Builder cloneFrom(SecuritySettings s) {
            instance.enableExecuteCommand = s.enableExecuteCommand;
            instance.executeCommandReturnFullOutput = s.executeCommandReturnFullOutput;
            instance.executeCommandBlocklist = s.executeCommandBlocklist != null ? new HashSet<>(s.executeCommandBlocklist) : new HashSet<>();
            instance.executeCommandMaxLength = s.executeCommandMaxLength;
            instance.wikiApiUrl = s.wikiApiUrl;
            instance.wikiAllowedHosts = s.wikiAllowedHosts != null ? new HashSet<>(s.wikiAllowedHosts) : new HashSet<>();
            return this;
        }

        public Builder enableExecuteCommand(boolean v) { instance.enableExecuteCommand = v; return this; }
        public Builder executeCommandReturnFullOutput(boolean v) { instance.executeCommandReturnFullOutput = v; return this; }
        public Builder executeCommandBlocklist(Set<String> v) { instance.executeCommandBlocklist = v != null ? new HashSet<>(v) : new HashSet<>(); return this; }
        public Builder executeCommandMaxLength(int v) { instance.executeCommandMaxLength = v; return this; }
        public Builder wikiApiUrl(String v) { instance.wikiApiUrl = v; return this; }
        public Builder wikiAllowedHosts(Set<String> v) { instance.wikiAllowedHosts = v != null ? new HashSet<>(v) : new HashSet<>(); return this; }

        public SecuritySettings build() { return instance; }
    }
}
