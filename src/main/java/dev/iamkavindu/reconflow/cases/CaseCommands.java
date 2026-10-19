package dev.iamkavindu.reconflow.cases;

import java.util.List;

public interface CaseCommands {

    CaseView startReview(CaseId caseId);

    CaseView addNote(CaseId caseId, String text);

    CaseView resolveCase(CaseId caseId, ResolutionType type, String reason, List<String> evidenceReferences);
}
