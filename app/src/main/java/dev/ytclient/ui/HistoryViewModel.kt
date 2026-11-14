package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import dev.ytclient.innertube.HistoryEntry
import dev.ytclient.innertube.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
) {
    /** Ролики без дублей (один ролик, просмотренный в разные дни, считается один раз): для ленты на вкладке «Профиль». */
    val items: List<VideoItem> = entries.map { it.video }.distinctBy { it.id }
}

/**
 * История просмотра: общая для ленты на вкладке «Профиль» и для экрана «История», поэтому загружается один раз.
 * Удаление записи применяется сразу, а при ошибке запись возвращается на место.
 */
class HistoryViewModel : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Короткие сообщения для Snackbar: результат удаления записи. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var job: Job? = null

    init {
        // История перезагружается при входе и выходе из аккаунта (первый раз — сразу при создании).
        viewModelScope.launch {
            Graph.auth.state.map { it.loggedIn }.distinctUntilChanged().collect { load() }
        }
    }

    /** [keepItems] = true — обновление «потянуть вниз»: старый список остаётся на экране, пока грузится новый. */
    fun load(keepItems: Boolean = false) {
        job?.cancel()
        if (!Graph.auth.current.loggedIn) {
            _state.value = HistoryUiState(loaded = true)
            return
        }
        job = viewModelScope.launch {
            _state.update { if (keepItems) it.copy(loading = true, loadingMore = false, error = null) else HistoryUiState(loading = true) }
            try {
                val page = Graph.innerTube.history()
                _state.value = HistoryUiState(entries = page.entries, continuation = page.continuation, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loaded = true, error = e.message ?: "Ошибка запроса") }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.loading || current.loadingMore) return

        job = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = Graph.innerTube.history(token, section = current.entries.lastOrNull()?.section)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        entries = (it.entries + page.entries).distinctBy { entry -> entry.key },
                        continuation = page.continuation,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Убирает запись из списка сразу; если YouTube её не удалил, возвращает на прежнее место. */
    fun remove(entry: HistoryEntry) {
        val token = entry.removeToken ?: return
        val index = _state.value.entries.indexOf(entry)
        if (index < 0) return
        _state.update { it.copy(entries = it.entries - entry) }

        viewModelScope.launch {
            try {
                Graph.innerTube.removeFromHistory(token)
                _messages.tryEmit("Удалено из истории")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(entries = it.entries.toMutableList().apply { add(index.coerceAtMost(size), entry) })
                }
                _messages.tryEmit("Не удалось удалить: ${e.message ?: "ошибка запроса"}")
            }
        }
    }
}
