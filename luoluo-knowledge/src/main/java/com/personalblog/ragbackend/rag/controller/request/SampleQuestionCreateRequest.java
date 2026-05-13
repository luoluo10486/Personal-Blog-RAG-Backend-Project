package com.personalblog.ragbackend.rag.controller.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleQuestionCreateRequest {
    private String title;
    private String description;
    private String question;
}
