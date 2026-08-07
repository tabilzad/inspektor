package com.example.docsonly

/**
 * External audit record shared across services.
 *
 * @property id Stable identifier of the record.
 * @property reason Human readable reason for the audit event,
 *     as entered by the operator.
 */
data class ExternalAuditRecord(
    val id: String,
    val reason: String,
    /** Identity of the actor who triggered the event. */
    val actor: String,
    /** Severity classification of the event. */
    val severity: AuditSeverity,
)

/** Severity levels recognized by the sample audit pipeline. */
enum class AuditSeverity { LOW, MEDIUM, HIGH }
