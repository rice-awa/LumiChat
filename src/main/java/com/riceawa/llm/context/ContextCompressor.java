package com.riceawa.llm.context;

import com.riceawa.llm.core.LLMMessage;

import java.util.List;

@FunctionalInterface
public interface ContextCompressor {
    String compress(List<LLMMessage> messages);
}
