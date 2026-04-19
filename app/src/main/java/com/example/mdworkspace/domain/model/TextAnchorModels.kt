package com.example.mdworkspace.domain.model

data class TextAnchorSnapshot(
    val projectCode: String,
    val docId: String,
    val docVersion: String,
    val bodyHash: String,
    val selectedText: String,
    val startSourceOffset: Int,
    val endSourceOffset: Int,
    val prefix: String,
    val suffix: String,
    val headingPath: List<String>? = null,
    val blockId: String? = null,
    val isLegacy: Boolean = false
)

data class TextAnchorSelection(
    val selectedText: String,
    val startSourceOffset: Int,
    val endSourceOffset: Int
)

data class TextAnchorResolution(
    val resolvedStartSourceOffset: Int?,
    val resolvedEndSourceOffset: Int?,
    val matchType: AnchorMatchType,
    val reason: AnchorResolutionReason,
    val confidence: Float,
    val needsReview: Boolean
)

enum class AnchorMatchType {
    EXACT_OFFSET,
    TEXT_UNIQUE,
    CONTEXT_MATCHED,
    FUZZY_CONTEXT,
    ORPHANED
}

enum class AnchorResolutionReason {
    HASH_MATCH_OFFSET_VALID,
    OFFSET_TEXT_MISMATCH,
    TEXT_NOT_FOUND,
    MULTIPLE_CANDIDATES_LOW_CONFIDENCE,
    LEGACY_ANCHOR,
    INVALID_OFFSET,
    EMPTY_BODY,
    MANUAL_REVIEW_REQUIRED
}
