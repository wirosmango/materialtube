package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import dev.ytclient.innertube.Channel
import dev.ytclient.innertube.LikeStatus
import dev.ytclient.innertube.StreamInfo
import dev.ytclient.innertube.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(val info: StreamInfo) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(val videoId: String) : ViewModel() {
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _related = MutableStateFlow<List<VideoItem>>(emptyList())
    val related: StateFlow<List<VideoItem>> = _related.asStateFlow()

    /** null — реакция неизвестна (нет входа или не удалось разобрать ответ). */
    private val _status = MutableStateFlow<LikeStatus?>(null)
    val status: StateFlow<LikeStatus?> = _status.asStateFlow()

    /** Автор ролика и состояние подписки. */
    private val _channel = MutableStateFlow<Channel?>(null)
    val channel: StateFlow<Channel?> = _channel.asStateFlow()

    /** Подписан ли пользователь на автора; null — неизвестно (нет входа или не разобрали ответ). */
    private val _subscribed = MutableStateFlow<Boolean?>(null)
    val subscribed: StateFlow<Boolean?> = _subscribed.asStateFlow()

    /** Токен комментариев; null — недоступны (или ещё не загружены). */
    private val _commentsToken = MutableStateFlow<String?>(null)
    val commentsToken: StateFlow<String?> = _commentsToken.asStateFlow()

    init {
        load()
        loadWatchNext()
    }

    fun load() {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = PlayerUiState.Ready(Graph.innerTube.resolveStream(videoId, maxHeight))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PlayerUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    /** Описание последнего обрыва загрузки (для отладки 403); null — обрывов не было. */
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()
    private var failures = 0

    fun reportFailure(description: String) {
        failures++
        _failure.value = "Обрыв #$failures: $description"
    }

    /** Желаемое качество (p); выбирается в меню плеера и сохраняется на время просмотра этого ролика. */
    var maxHeight = 480
        private set

    /** Для HLS качество переключается дорожками плеера без нового запроса: запоминаем выбор для следующих запусков. */
    fun rememberQuality(height: Int) {
        maxHeight = height
    }

    /** Смена качества: получаем потоки нужной высоты и продолжаем с той же позиции. */
    fun setQuality(height: Int, positionMs: Long) {
        maxHeight = height
        resumePositionMs = positionMs
        viewModelScope.launch {
            try {
                _state.value = PlayerUiState.Ready(Graph.innerTube.resolveStream(videoId, maxHeight))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PlayerUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    /** Позиция, с которой плеер продолжит после обновления ссылки. */
    var resumePositionMs: Long = 0L
        private set
    private var recoveries = 0

    /**
     * Ссылки googlevideo иногда перестают работать посреди ролика (403). Получаем свежие и продолжаем
     * с той же позиции. Возвращает false, если попыток для этого ролика уже слишком много.
     */
    fun recover(positionMs: Long): Boolean {
        // Без продвижения вперёд (403 на самом старте или повтор на той же позиции) пользователь увидит ошибку.
        if (recoveries >= MAX_RECOVERIES || positionMs <= resumePositionMs) return false
        recoveries++
        resumePositionMs = positionMs
        viewModelScope.launch {
            try {
                _state.value = PlayerUiState.Ready(Graph.innerTube.resolveStream(videoId, maxHeight))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PlayerUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
        return true
    }

    private fun loadWatchNext() {
        viewModelScope.launch {
            try {
                val next = Graph.innerTube.watchNext(videoId)
                _related.value = next.related.filter { it.id != videoId }
                _status.value = next.status
                _commentsToken.value = next.commentsToken
                _channel.value = next.channel
                _subscribed.value = next.subscribed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // «Похожие» и лайк необязательны: без них плеер всё равно работает.
            }
        }
    }

    /** Подписка на автора ролика: кнопка меняется сразу, при ошибке откатывается. */
    fun toggleSubscribe(onError: (String) -> Unit) {
        val current = _channel.value ?: return
        if (!Graph.auth.current.loggedIn) {
            onError("Войдите в аккаунт на вкладке «Аккаунт», чтобы подписываться")
            return
        }
        val previous = _subscribed.value
        val target = previous != true
        _subscribed.value = target
        viewModelScope.launch {
            try {
                Graph.innerTube.setSubscribed(current.id, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _subscribed.value = previous
                onError(e.message ?: "Не удалось изменить подписку")
            }
        }
    }

    /** Лайк/дизлайк: повторное нажатие на ту же кнопку снимает реакцию. Кнопка меняется сразу, при ошибке откатывается. */
    fun react(target: LikeStatus, onError: (String) -> Unit) {
        if (!Graph.auth.current.loggedIn) {
            onError("Войдите в аккаунт на вкладке «Аккаунт», чтобы ставить оценки")
            return
        }
        val previous = _status.value
        val new = if (previous == target) LikeStatus.None else target
        _status.value = new
        viewModelScope.launch {
            try {
                Graph.innerTube.react(videoId, new)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = previous
                onError(e.message ?: "Не удалось поставить оценку")
            }
        }
    }

    private companion object {
        const val MAX_RECOVERIES = 20
    }
}
