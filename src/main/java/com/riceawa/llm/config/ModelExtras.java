package com.riceawa.llm.config;

public final class ModelExtras {
    private String compressionModel;
    private String titleGenerationModel;
    private boolean enableTitleGeneration;
    private boolean enableCompressionNotification;

    private ModelExtras() {}

    public static ModelExtras defaults() {
        ModelExtras m = new ModelExtras();
        m.compressionModel = ConfigDefaults.DEFAULT_COMPRESSION_MODEL;
        m.titleGenerationModel = ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL;
        m.enableTitleGeneration = ConfigDefaults.DEFAULT_ENABLE_TITLE_GENERATION;
        m.enableCompressionNotification = ConfigDefaults.DEFAULT_ENABLE_COMPRESSION_NOTIFICATION;
        return m;
    }

    public String getCompressionModel() { return compressionModel; }
    public String getTitleGenerationModel() { return titleGenerationModel; }
    public boolean isEnableTitleGeneration() { return enableTitleGeneration; }
    public boolean isEnableCompressionNotification() { return enableCompressionNotification; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final ModelExtras instance = new ModelExtras();

        public Builder cloneFrom(ModelExtras m) {
            instance.compressionModel = m.compressionModel;
            instance.titleGenerationModel = m.titleGenerationModel;
            instance.enableTitleGeneration = m.enableTitleGeneration;
            instance.enableCompressionNotification = m.enableCompressionNotification;
            return this;
        }

        public Builder compressionModel(String v) { instance.compressionModel = v; return this; }
        public Builder titleGenerationModel(String v) { instance.titleGenerationModel = v; return this; }
        public Builder enableTitleGeneration(boolean v) { instance.enableTitleGeneration = v; return this; }
        public Builder enableCompressionNotification(boolean v) { instance.enableCompressionNotification = v; return this; }

        public ModelExtras build() { return instance; }
    }
}
