package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReference
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDetail
import es.alejandro.grandarbiter.domain.model.quickrefs.QuickReferenceDraft
import kotlinx.coroutines.flow.Flow

interface QuickReferenceRepository {
    fun observeAll(): Flow<List<QuickReference>>
    /** Re-emits when the reference or the installed rules change. Null once deleted. */
    fun observeDetail(id: Long): Flow<QuickReferenceDetail?>
    suspend fun draft(id: Long): QuickReferenceDraft?
    /** Returns the saved reference's id. */
    suspend fun save(draft: QuickReferenceDraft): Long
    suspend fun delete(id: Long)
    /** True when a built-in reference was deleted and can be brought back. */
    fun observeCanRestorePresets(): Flow<Boolean>
    suspend fun restorePresets()
}
