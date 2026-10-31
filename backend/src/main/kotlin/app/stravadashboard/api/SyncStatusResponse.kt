package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET and POST /api/sync (see docs/features/009-background-sync/specs.md).
// Mirrored by frontend/src/app/core/api/sync.models.ts.

@Serializable
data class SyncStatusResponse(
    val status: String,
    val lastSyncedAt: String?,
    val retryAfter: String?,
    val runsPendingDetail: Int,
    val nextSyncInSeconds: Long?,
)
