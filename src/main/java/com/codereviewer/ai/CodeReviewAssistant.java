package com.codereviewer.ai;

import com.codereviewer.model.ReviewOutput;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CodeReviewAssistant {

    @SystemMessage(ReviewPrompts.SYSTEM_PROMPT)
    ReviewOutput reviewPatch(@UserMessage String prompt);
}
