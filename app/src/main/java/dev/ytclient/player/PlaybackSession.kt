package dev.ytclient.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dev.ytclient.Graph
import dev.ytclient.innertube.StreamInfo
import dev.ytclient.ui.PlayerUiState
import dev.ytclient.ui.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call

/** Что сейчас «в плеере»: нужно мини-плееру. [title] и [author] null, пока ролик ещё загружается. */
data class PlaybackMeta(val videoId: String, val title: String?, val author: String?)

/**
 * Единственный ExoPlayer приложения. Раньше плеер создавался и освобождался внутри экрана ролика,
 * поэтому при его закрытии воспроизведение обрывалось. Теперь экран и мини-плеер только показывают
 * этот плеер, а сама сессия:
 *  - следит за [PlayerViewModel.state] и подставляет в плеер свежий источник (выбор качества, обновление ссылок);
 *  - восстанавливается после 403 посреди ролика, даже если экран уже свёрнут.
 */
@OptIn(UnstableApi::class)
class PlaybackSession(private val context: Context, private val http: Call.Factory) {

    private val errorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val vm = vm
            if (vm != null) {
                describeHttpFailure(error)?.let { vm.reportFailure(it) }
                // 403 посреди ролика: ссылка устарела или отозвана, берём новую и продолжаем с этого места.
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                    vm.recover(player.currentPosition)
                ) return
            }
            _error.value = "${error.errorCodeName}: ${error.cause?.message ?: error.message}"
        }
    }

    private val playerLazy = lazy {
        ExoPlayer.Builder(context)
            // Грузим на 5 минут вперёд без пауз: обрывы 403 случаются, когда запрос приходит после долгого простоя.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(300_000, 300_000, 2_500, 5_000)
                    .build(),
            )
            .build()
            .also { it.addListener(errorListener) }
    }

    /** Один и тот же плеер показывают и полный экран, и мини-плеер. Обращаться только с главного потока. */
    val player: ExoPlayer get() = playerLazy.value

    private var vm: PlayerViewModel? = null
    private var collector: Job? = null
    private var applied: StreamInfo? = null

    private val _meta = MutableStateFlow<PlaybackMeta?>(null)

    /** null — ничего не открыто. */
    val meta: StateFlow<PlaybackMeta?> = _meta.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** Последняя ошибка воспроизведения или загрузки; сбрасывается, когда в плеер попадает новый источник. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Делает [vm] текущим роликом. Повторный вызов с тем же [vm] ничего не меняет (так экран можно
     * развернуть обратно, не прерывая воспроизведение). Другой ролик сразу останавливает предыдущий.
     */
    fun attach(vm: PlayerViewModel) {
        if (this.vm === vm) return
        reset()
        this.vm = vm
        _meta.value = PlaybackMeta(vm.videoId, title = null, author = null)
        collector = Graph.appScope.launch {
            vm.state.collect { state ->
                when (state) {
                    is PlayerUiState.Ready -> apply(vm, state.info)
                    is PlayerUiState.Error -> _error.value = state.message
                    PlayerUiState.Loading -> _error.value = null
                }
            }
        }
    }

    private fun apply(vm: PlayerViewModel, info: StreamInfo) {
        if (info === applied) return
        val newVideo = applied?.videoId != info.videoId
        applied = info
        _error.value = null
        _meta.value = PlaybackMeta(info.videoId, info.title, info.author)

        val p = player
        // Скорость и выбранная звуковая дорожка относятся к ролику; при смене качества того же ролика они сохраняются.
        if (newVideo) p.setPlaybackSpeed(1f)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .apply { if (newVideo) clearOverrides() }
            .clearVideoSizeConstraints()
            .apply { if (info.isHls) setMaxVideoSize(Int.MAX_VALUE, vm.maxHeight) }
            .build()
        p.setMediaSource(MediaSources.create(info, http), vm.resumePositionMs)
        p.prepare()
        p.playWhenReady = true
    }

    /** Пауза при уходе из приложения (фонового воспроизведения пока нет). */
    fun pause() {
        if (vm != null && playerLazy.isInitialized()) player.pause()
    }

    /** Закрывает мини-плеер: останавливает воспроизведение и забывает ролик. */
    fun close() = reset()

    private fun reset() {
        collector?.cancel()
        collector = null
        vm = null
        applied = null
        _meta.value = null
        _error.value = null
        if (playerLazy.isInitialized()) {
            player.stop()
            player.clearMediaItems()
        }
    }
}
