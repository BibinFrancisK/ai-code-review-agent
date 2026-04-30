package com.codereviewer.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CodeReviewAssistant {

    String reviewPatch(@UserMessage String prompt);
}
