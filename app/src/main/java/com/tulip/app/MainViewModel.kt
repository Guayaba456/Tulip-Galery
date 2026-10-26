package com.tulip.app

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tulip.app.data.*
import com.tulip.app.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ViewerRequest(val ids: List<Long>, val start: Int)
data class ConflictPrompt(val entries: List<MediaEntry>, val dest: String, val copy: Boolean, val clashes: List<String>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TulipApp).container

    val media: StateFlow<List<MediaEntry>> = c.media.items
    val loaded: StateFlow<Boolean> = c.media.loaded
    val settings = c.settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val favorites = c.db.favorites().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favoriteIds = favorites.map { l -> l.map { it.mediaId }.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val collections = c.db.collections().observeCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collectionItems = c.db.collections().observeItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val excludedRows = c.db.excluded().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val excludedKeys = excludedRows.map { r -> r.filter { it.enabled }.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val player = c.player.player

    val selected = MutableStateFlow<Set<Long>>(emptySet())
    val galleryPath = MutableStateFlow("")
    val viewerRequest = MutableStateFlow<ViewerRequest?>(null)
    val conflict = MutableStateFlow<ConflictPrompt?>(null)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private val consentChannel = Channel<IntentSender>(Channel.BUFFERED)
    val consentRequests = consentChannel.receiveAsFlow()
    private var pending: (suspend () -> OpResult)? = null

    init {
        c.media.start()
        // Si un favorito desaparece pero hay un archivo con el mismo nombre y tamaño, actualiza la referencia.
        viewModelScope.launch(Dispatchers.IO) {
            combine(media, favorites, loaded) { m, f, l -> Triple(m, f, l) }.collect { (m, f, l) ->
                if (l && m.isNotEmpty()) {
                    val ids = m.mapTo(HashSet()) { it.id }
                    for (fav in f) if (fav.mediaId !in ids) {
                        val cand = m.firstOrNull { it.name == fav.lastName && it.size == fav.size && it.id !in favoriteIds.value }
                        if (cand != null) c.db.favorites().relink(fav.mediaId, cand.id, cand.relativePath)
                    }
                }
            }
        }
    }

    fun message(s: String) { messageChannel.trySend(s) }
    fun refreshMedia() = c.media.refresh()
    fun entriesOf(ids: Collection<Long>): List<MediaEntry> {
        val m = media.value.associateBy { it.id }
        return ids.mapNotNull { m[it] }
    }

    // ---------- selección ----------
    fun toggleSelect(id: Long) { selected.value = selected.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { selected.value = emptySet() }
    fun selectAll(ids: Collection<Long>) { selected.value = ids.toSet() }

    // ---------- operaciones con consentimiento del sistema ----------
    fun onConsentResult(ok: Boolean) {
        val p = pending
        pending = null
        if (!ok) { message("Operación cancelada"); return }
        if (p != null) viewModelScope.launch(Dispatchers.IO) { handle(p()) }
    }

    private fun handle(r: OpResult) {
        when (r) {
            is OpResult.Done -> { message(r.message); c.media.refresh() }
            is OpResult.Failed -> { message(r.message); c.media.refresh() }
            is OpResult.NeedsConsent -> { pending = r.resume; consentChannel.trySend(r.intentSender) }
        }
    }

    private fun op(block: suspend () -> OpResult) {
        viewModelScope.launch(Dispatchers.IO) { handle(block()) }
    }

    fun delete(entries: List<MediaEntry>) = op { c.fileOps.delete(entries) }
    fun rename(e: MediaEntry, name: String) = op { c.fileOps.rename(e, name) }
    fun createFolder(parent: String, name: String) = op { c.fileOps.createFolder(parent, name) }
    fun renameFolder(path: String, name: String) = op { c.fileOps.renameFolder(path, name, media.value) }
    fun deleteFolder(path: String) = op { c.fileOps.deleteFolder(path, media.value) }

    /** Mover o copiar: primero detecta conflictos de nombre y, si los hay, pregunta al usuario. */
    fun requestTransfer(entries: List<MediaEntry>, dest: String, copy: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val clashes = c.fileOps.findConflicts(entries, dest)
            if (clashes.isEmpty()) {
                handle(if (copy) c.fileOps.copy(entries, dest, ConflictPolicy.KEEP_BOTH) else c.fileOps.move(entries, dest, ConflictPolicy.KEEP_BOTH))
            } else {
                conflict.value = ConflictPrompt(entries, dest, copy, clashes.map { it.name })
            }
        }
    }

    fun resolveConflict(policy: ConflictPolicy?) {
        val p = conflict.value ?: return
        conflict.value = null
        if (policy == null) { message("Operación cancelada"); return }
        op { if (p.copy) c.fileOps.copy(p.entries, p.dest, policy) else c.fileOps.move(p.entries, p.dest, policy) }
    }

    // ---------- favoritos y colecciones ----------
    fun toggleFavorite(e: MediaEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            if (e.id in favoriteIds.value) c.db.favorites().delete(e.id)
            else c.db.favorites().upsert(FavoriteEntity(e.id, e.name, e.relativePath, e.size, e.kind == MediaKind.VIDEO, System.currentTimeMillis()))
        }
    }

    fun addFavorites(list: List<MediaEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            c.db.favorites().upsertAll(list.map { FavoriteEntity(it.id, it.name, it.relativePath, it.size, it.kind == MediaKind.VIDEO, now) })
        }
    }

    fun removeFavoriteRef(id: Long) { viewModelScope.launch(Dispatchers.IO) { c.db.favorites().delete(id) } }

    fun createCollection(name: String, initial: List<MediaEntry> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = c.db.collections().insertCollection(CollectionEntity(name = n, createdAt = System.currentTimeMillis()))
            if (initial.isNotEmpty()) c.db.collections().addItems(initial.map { CollectionItemEntity(id, it.id, it.name) })
            message("Colección «$n» creada (virtual: no mueve ni duplica archivos)")
        }
    }

    fun addToCollection(collectionId: Long, list: List<MediaEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            c.db.collections().addItems(list.map { CollectionItemEntity(collectionId, it.id, it.name) })
            message("${list.size} archivo(s) añadidos a la colección")
        }
    }

    fun removeFromCollection(collectionId: Long, mediaId: Long) { viewModelScope.launch(Dispatchers.IO) { c.db.collections().removeItem(collectionId, mediaId) } }
    fun renameCollection(id: Long, name: String) { if (name.isNotBlank()) viewModelScope.launch(Dispatchers.IO) { c.db.collections().rename(id, name.trim()) } }
    fun deleteCollection(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { c.db.collections().deleteItemsOf(id); c.db.collections().deleteCollection(id) }
    }

    // ---------- exclusión de carpetas en la cola aleatoria ----------
    fun setExcluded(volume: String, path: String, excluded: Boolean) {
        val key = FolderExclusions.key(volume, path)
        viewModelScope.launch(Dispatchers.IO) {
            if (excluded) c.db.excluded().upsert(ExcludedFolderEntity(key, FolderExclusions.normalize(path), true))
            else c.db.excluded().delete(key)
        }
    }

    fun setExcludedEnabled(key: String, enabled: Boolean) { viewModelScope.launch(Dispatchers.IO) { c.db.excluded().setEnabled(key, enabled) } }
    fun removeExclusion(key: String) { viewModelScope.launch(Dispatchers.IO) { c.db.excluded().delete(key) } }

    fun buildVideoQueue(): List<MediaEntry> = Shuffler.buildVideoQueue(media.value, excludedKeys.value, Random.Default)

    // ---------- reproducción ----------
    fun connectPlayer() = c.player.connect()
    fun saveQueue(state: QueueState?) = updateSetting { it[SettingsKeys.savedQueue] = state?.encode() ?: "" }

    // ---------- ajustes ----------
    fun updateSetting(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch { c.settingsRepo.update(block) }
    }

    override fun onCleared() {
        c.player.release()
        super.onCleared()
    }
}
