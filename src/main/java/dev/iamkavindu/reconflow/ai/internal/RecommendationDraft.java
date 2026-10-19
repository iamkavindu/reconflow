package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.cases.InvestigationEvidence;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record RecommendationDraft(
        @NotBlank @Size(max = 4000) String summary,
        @NotBlank @Size(max = 1000) String likelyCause,
        @NotEmpty @Size(max = 30) List<@NotNull @Valid InvestigationEvidence> evidence,
        ResolutionType suggestedResolution,
        @DecimalMin("0") @DecimalMax("1") double confidence,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1000) String> limitations) {}
