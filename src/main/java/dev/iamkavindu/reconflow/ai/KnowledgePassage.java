package dev.iamkavindu.reconflow.ai;

import java.util.UUID;

public record KnowledgePassage(
        UUID id, UUID documentId, KnowledgeKind kind, String title, String content, int chunkIndex) {}
