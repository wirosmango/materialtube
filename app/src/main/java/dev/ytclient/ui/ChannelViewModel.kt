package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import dev.ytclient.innertube.ChannelInfo
import dev.ytclient.innertube.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelUiState(
    val channel: ChannelInfo? = null,
    val items: List<VideoItem> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class ChannelViewModel(private val channelId: String) : ViewModel() {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ChannelUiState(loading = true)
        viewModelScope.launch {
            try {
                val page = Graph.innerTube.channel(channelId)
                _state.value = ChannelUiState(
                    channel = page.info,
                    items = page.videos.items.map { fill(it, page.info) },
                    continuation = page.videos.continuation,
                    loading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ChannelUiState(loading = false, error = e.message ?: "Ошибка запроса")
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        val channel = current.channel ?: return
        if (current.loading || current.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = Graph.innerTube.feed(channelId, token)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = (it.items + page.items.map { v -> fill(v, channel) }).distinctBy { v -> v.id },
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

    /** Подписка на этот канал: кнопка меняется сразу, при ошибке откатывается. */
    fun toggleSubscribe(onError: (String) -> Unit) {
        val channel = _state.value.channel ?: return
        if (!Graph.auth.current.loggedIn) {
            onError("Войдите в аккаунт на вкладке «Аккаунт», чтобы подписываться")
            return
        }
        val target = channel.subscribed != true
        _state.update { it.copy(channel = channel.copy(subscribed = target)) }
        viewModelScope.launch {
            try {
                Graph.innerTube.setSubscribed(channel.id, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(channel = it.channel?.copy(subscribed = channel.subscribed)) }
                onError(e.message ?: "Не удалось изменить подписку")
            }
        }
    }

    /** В списке видео канала имя и аватарка автора не приходят: подставляем свои. */
    private fun fill(item: VideoItem, channel: ChannelInfo) = item.copy(
        channel = item.channel ?: channel.name,
        channelAvatar = item.channelAvatar ?: channel.avatar,
        channelId = item.channelId ?: channel.id,
    )
}
