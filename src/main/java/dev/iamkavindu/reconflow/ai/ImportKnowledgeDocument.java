package dev.iamkavindu.reconflow.ai;

import java.util.Objects;

public record ImportKnowledgeDocument(KnowledgeKind kind, String title, String content) {

    public ImportKnowledgeDocument {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(title);
        Objects.requireNonNull(content);
        title = title.strip();
        content = content.strip();
        if (title.isBlank()
                || title.length() > 200
                || content.isBlank()
                || content.length() > 20000
                || title.indexOf('\0') >= 0
                || content.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("knowledge requires a title up to 200 and text up to 20000 characters");
        }
    }
}
