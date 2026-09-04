package com.charizard.charify

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    companion object {
        private const val YTM_PACKAGE = "com.google.android.apps.youtube.music"
        private const val TEXT = Color.WHITE
        private const val MUTED = 0xFFC9CCD3.toInt()
        private const val DARK = 0xFF101216.toInt()
        private const val PANEL = 0xD91A1C22.toInt()
        private const val PANEL_SOFT = 0xB81A1C22.toInt()
    }

    data class ThemeOption(val name: String, val subtitle: String, val image: Int, val accent: Int)

    private lateinit var store: LibraryStore
    private lateinit var content: FrameLayout
    private lateinit var background: ImageView
    private lateinit var headerSubtitle: TextView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: Button
    private lateinit var navButtons: List<Button>

    private var controller: MediaController? = null
    private var currentTab = 0
    private var introVisible = false
    private var playerArt: ImageView? = null
    private var playerTitle: TextView? = null
    private var playerArtist: TextView? = null
    private var playerPlay: Button? = null
    private var playerSeek: SeekBar? = null
    private var playerTime: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val themes by lazy {
        listOf(
            ThemeOption("Ember", "Quente, laranja e escuro", R.drawable.theme_ember, 0xFFFF8A2A.toInt()),
            ThemeOption("Midnight", "Azul profundo e elegante", R.drawable.theme_midnight, 0xFF72A7FF.toInt()),
            ThemeOption("Aurora", "Roxo e verde neon", R.drawable.theme_aurora, 0xFF8AE6C2.toInt()),
            ThemeOption("Graphite", "AMOLED quase preto", R.drawable.theme_graphite, 0xFFE3E5EA.toInt()),
            ThemeOption("Rose", "Rosa escuro e suave", R.drawable.theme_rose, 0xFFFF7AA8.toInt())
        )
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshMediaUi()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshMediaUi()
        override fun onSessionDestroyed() { controller = null; refreshMediaUi() }
    }

    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LibraryStore(this)
        setContentView(R.layout.activity_main)
        bindShell()
        applyTheme()
        if (store.shouldShowIntro()) showIntro() else showPlayer()
        handler.post(progressTick)
    }

    override fun onResume() {
        super.onResume()
        connectToYouTubeMusic()
        refreshMediaUi()
    }

    override fun onDestroy() {
        controller?.unregisterCallback(controllerCallback)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun bindShell() {
        background = findViewById(R.id.backgroundImage)
        content = findViewById(R.id.content)
        headerSubtitle = findViewById(R.id.headerSubtitle)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniArt = findViewById(R.id.miniArt)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        miniPlay = findViewById(R.id.miniPlay)
        val navPlayer: Button = findViewById(R.id.navPlayer)
        val navSearch: Button = findViewById(R.id.navSearch)
        val navLibrary: Button = findViewById(R.id.navLibrary)
        val navSettings: Button = findViewById(R.id.navSettings)
        navButtons = listOf(navPlayer, navSearch, navLibrary, navSettings)

        findViewById<View>(R.id.header).background = round(PANEL_SOFT, 28)
        findViewById<View>(R.id.premiumPill).background = round(0x663A3D45, 18, 0x55FFFFFF)
        miniPlayer.background = round(PANEL, 24)
        findViewById<View>(R.id.bottomNav).background = round(0xE61A1C22.toInt(), 24)

        navPlayer.setOnClickListener { showPlayer() }
        navSearch.setOnClickListener { showSearch() }
        navLibrary.setOnClickListener { showLibrary() }
        navSettings.setOnClickListener { showSettings() }
        miniPlayer.setOnClickListener { showPlayer() }
        miniPlay.setOnClickListener { togglePlay() }
    }

    private fun activeTheme() = themes[store.themeIndex().coerceIn(0, themes.lastIndex)]

    private fun applyTheme() {
        val theme = activeTheme()
        background.setImageResource(theme.image)
        headerSubtitle.text = "Tema ${theme.name} • YouTube Music com a tua cara"
        highlightNav()
    }

    private fun setScreen(tab: Int, title: String, viewBuilder: () -> View): Unit {
        currentTab = tab
        introVisible = false
        content.removeAllViews()
        try {
            val view = viewBuilder()
            content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } catch (t: Throwable) {
            val fallback = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                background = round(PANEL, 28)
                addView(text("A tela deu erro", 22f, TEXT, true).apply { gravity = Gravity.CENTER })
                addView(text(t.message ?: t.javaClass.simpleName, 13f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
            }
            content.addView(fallback, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        highlightNav()
        refreshMediaUi()
    }

    private fun showIntro(): Unit {
        introVisible = true
        currentTab = -1
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(18))
        }
        box.addView(card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(text("Bem-vindo ao Charify", 26f, TEXT, true).apply { gravity = Gravity.CENTER })
            addView(text("O YouTube Music continua tocando. O Charify só troca a experiência por uma interface nossa.", 14f, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(10), dp(6), dp(18))
            })
            addView(primary("Começar") {
                store.markIntroSeen()
                showPlayer()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        })
        box.addView(section("Escolhe um tema"))
        themes.forEachIndexed { index, theme -> box.addView(themeRow(theme, index)) }
        box.addView(card().apply {
            addView(text("O que esta versão conserta", 18f, TEXT, true))
            addView(text("• layout nativo em XML\n• conteúdo sempre ocupa a área central\n• navegação nunca some\n• retrato e paisagem\n• sem animação que deixa a tela invisível", 14f, MUTED).apply { setPadding(0, dp(10), 0, 0) })
        })
        scroll.addView(box)
        content.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        highlightNav()
    }

    private fun showPlayer(): Unit = setScreen(0, "Player") {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(16))
        }

        if (!hasNotificationAccess()) {
            box.addView(card().apply {
                addView(text("Conecta o Charify", 19f, TEXT, true))
                addView(text("Ativa o acesso às notificações para eu enxergar a sessão do YouTube Music.", 14f, MUTED).apply { setPadding(0, dp(8), 0, dp(12)) })
                addView(primary("Ativar acesso") { openNotificationAccess() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
            })
        }

        box.addView(card(20).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            playerArt = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = round(0x552B2D34, 28)
                clipToOutline = true
            }
            val artSize = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) dp(180) else dp(250)
            addView(playerArt, LinearLayout.LayoutParams(artSize, artSize).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) })
            playerTitle = text("Nenhuma música conectada", 22f, TEXT, true).apply { gravity = Gravity.CENTER }
            playerArtist = text("Abra o YouTube Music e comece uma faixa", 14f, MUTED).apply { gravity = Gravity.CENTER }
            addView(playerTitle)
            addView(playerArtist, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5); bottomMargin = dp(12) })
            playerSeek = SeekBar(this@MainActivity).apply {
                max = 1000
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val duration = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                        if (duration > 0) controller?.transportControls?.seekTo(duration * (seekBar?.progress ?: 0) / 1000L)
                    }
                })
            }
            addView(playerSeek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            playerTime = text("0:00 / 0:00", 12f, MUTED).apply { gravity = Gravity.CENTER }
            addView(playerTime)

            val controls = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
            controls.addView(control("◀") { controller?.transportControls?.skipToPrevious() }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { rightMargin = dp(8) })
            playerPlay = control("▶") { togglePlay() }
            controls.addView(playerPlay, LinearLayout.LayoutParams(0, dp(54), 1f).apply { rightMargin = dp(8) })
            controls.addView(control("▶▶") { controller?.transportControls?.skipToNext() }, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(controls)
        })

        box.addView(card().apply {
            addView(text("Ações rápidas", 18f, TEXT, true))
            addView(primary("Abrir YouTube Music") { launchYouTubeMusic() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12); bottomMargin = dp(8) })
            addView(secondary("Buscar música") { showSearch() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        })

        scroll.addView(box)
        scroll
    }

    private fun showSearch(): Unit = setScreen(1, "Buscar") {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(12)) }
        val query = EditText(this).apply {
            hint = "Música, artista ou álbum"
            setTextColor(TEXT); setHintTextColor(0x99FFFFFF.toInt()); setSingleLine(true)
            background = round(0xB52A2D35.toInt(), 18)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val status = text("Busca pelo catálogo público do YouTube.", 12f, MUTED)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(card().apply {
            addView(text("Buscar", 20f, TEXT, true))
            addView(query, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10); bottomMargin = dp(10) })
            addView(primary("Buscar") {
                val q = query.text.toString().trim()
                if (q.isBlank()) return@primary
                val key = store.apiKey()
                if (key.isBlank()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Falta a API Key")
                        .setMessage("Configura a YouTube Data API Key em Config. Ou abre a busca direto no YouTube Music.")
                        .setPositiveButton("Abrir no YT Music") { _, _ -> openMusicSearch(q) }
                        .setNegativeButton("Configurar") { _, _ -> showSettings() }
                        .show()
                    return@primary
                }
                status.text = "Buscando..."
                results.removeAllViews()
                thread {
                    runCatching { YouTubeApi.search(key, q) }
                        .onSuccess { songs -> runOnUiThread {
                            status.text = if (songs.isEmpty()) "Nada encontrado." else "${songs.size} resultados"
                            songs.forEach { results.addView(songCard(it)) }
                        } }
                        .onFailure { e -> runOnUiThread { status.text = "Erro: ${e.message}" } }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) })
            addView(status)
        })
        val scroll = ScrollView(this).apply { addView(results) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root
    }

    private fun showLibrary(): Unit = setScreen(2, "Biblioteca") {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(14)) }
        box.addView(section("Favoritos"))
        box.addView(card().apply {
            val fav = store.favorites()
            if (fav.isEmpty()) addView(text("Nenhum favorito ainda.", 14f, MUTED)) else fav.take(30).forEach { addView(compactSong(it)) }
        })
        box.addView(section("Playlists"))
        box.addView(card().apply {
            addView(primary("Nova playlist") { promptPlaylist { showLibrary() } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(10) })
            val lists = store.playlists()
            if (lists.isEmpty()) addView(text("Cria tua primeira playlist.", 14f, MUTED))
            lists.forEach { p ->
                addView(text("${p.name} • ${p.songs.size}", 16f, TEXT, true).apply { setPadding(0, dp(8), 0, dp(6)) })
                p.songs.take(12).forEach { addView(compactSong(it)) }
            }
        })
        box.addView(section("Recentes"))
        box.addView(card().apply {
            val history = store.history()
            if (history.isEmpty()) addView(text("Nada tocado pelo Charify ainda.", 14f, MUTED)) else history.take(30).forEach { addView(compactSong(it)) }
        })
        scroll.addView(box)
        scroll
    }

    private fun showSettings(): Unit = setScreen(3, "Config") {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(14)) }
        box.addView(section("Temas"))
        themes.forEachIndexed { index, theme -> box.addView(themeRow(theme, index)) }

        box.addView(section("YouTube Data API"))
        box.addView(card().apply {
            addView(text("A chave é usada só para pesquisar. Ela fica salva localmente.", 13f, MUTED))
            val key = EditText(this@MainActivity).apply {
                setText(store.apiKey()); hint = "Cole sua API Key"
                setTextColor(TEXT); setHintTextColor(0x99FFFFFF.toInt()); setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = round(0xB52A2D35.toInt(), 18)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            addView(key, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10); bottomMargin = dp(10) })
            addView(primary("Salvar chave") { store.saveApiKey(key.text.toString()); Toast.makeText(this@MainActivity, "Chave salva", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        })

        box.addView(section("Controle de mídia"))
        box.addView(card().apply {
            addView(text(if (hasNotificationAccess()) "✓ Permissão ativa" else "✕ Permissão desativada", 14f, if (hasNotificationAccess()) activeTheme().accent else MUTED, true))
            addView(primary("Abrir permissões") { openNotificationAccess() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10); bottomMargin = dp(8) })
            addView(secondary("Testar conexão") {
                val ok = connectToYouTubeMusic()
                Toast.makeText(this@MainActivity, if (ok) "YouTube Music conectado" else "Nenhuma sessão ativa", Toast.LENGTH_LONG).show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        })

        box.addView(card().apply {
            addView(primary("Ver apresentação de novo") { store.resetIntro(); showIntro() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            addView(text("Charify 0.5.0 Stable • UI reconstruída do zero", 12f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
        })
        scroll.addView(box)
        scroll
    }

    private fun songCard(song: Song): View = card().apply {
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val art = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = round(0x552B2D34, 16); clipToOutline = true }
        row.addView(art, LinearLayout.LayoutParams(dp(70), dp(70)).apply { rightMargin = dp(12) })
        val info = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(song.title, 16f, TEXT, true))
        info.addView(text(song.artist, 12f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(row)
        val buttons = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(primary("Tocar") { playSong(song) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        buttons.addView(secondary(if (store.isFavorite(song)) "♥" else "♡") { b -> (b as Button).text = if (store.toggleFavorite(song)) "♥" else "♡" }, LinearLayout.LayoutParams(dp(58), dp(44)).apply { rightMargin = dp(8) })
        buttons.addView(secondary("+") { choosePlaylist(song) }, LinearLayout.LayoutParams(dp(58), dp(44)))
        addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        if (song.thumbnail.isNotBlank()) loadBitmap(song.thumbnail) { art.setImageBitmap(it) }
    }

    private fun compactSong(song: Song): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = round(0x702A2D35, 16); setPadding(dp(12), dp(10), dp(10), dp(10))
        val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(song.title, 14f, TEXT, true)); labels.addView(text(song.artist, 12f, MUTED))
        addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        addView(secondary("▶") { playSong(song) }, LinearLayout.LayoutParams(dp(54), dp(42)))
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) } }

    private fun themeRow(theme: ThemeOption, index: Int): View = card(12).apply {
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val preview = ImageView(this@MainActivity).apply { setImageResource(theme.image); scaleType = ImageView.ScaleType.CENTER_CROP; background = round(0x552B2D34, 16); clipToOutline = true }
        row.addView(preview, LinearLayout.LayoutParams(dp(88), dp(70)).apply { rightMargin = dp(12) })
        val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(theme.name, 16f, TEXT, true)); labels.addView(text(theme.subtitle, 12f, MUTED).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val selected = store.themeIndex() == index
        row.addView(if (selected) secondary("✓") {} else primary("Usar") {
            store.saveThemeIndex(index); applyTheme(); if (introVisible) showIntro() else when (currentTab) { 0 -> showPlayer(); 1 -> showSearch(); 2 -> showLibrary(); else -> showSettings() }
        }, LinearLayout.LayoutParams(if (selected) dp(54) else dp(72), dp(44)))
        addView(row)
    }

    private fun playSong(song: Song) {
        store.addHistory(song)
        connectToYouTubeMusic()
        val uri = Uri.parse("https://music.youtube.com/watch?v=${song.id}")
        val c = controller
        val actions = c?.playbackState?.actions ?: 0L
        try {
            if (c != null && actions and PlaybackState.ACTION_PLAY_FROM_URI != 0L) c.transportControls.playFromUri(uri, null)
            else openMusicUri(uri)
        } catch (_: Exception) { openMusicUri(uri) }
    }

    private fun choosePlaylist(song: Song) {
        val playlists = store.playlists()
        val names = playlists.map { it.name }.toMutableList().apply { add("+ Nova playlist") }
        AlertDialog.Builder(this).setTitle("Adicionar à playlist").setItems(names.toTypedArray()) { _, which ->
            if (which == names.lastIndex) promptPlaylist { store.addToPlaylist(it, song) } else store.addToPlaylist(names[which], song)
        }.show()
    }

    private fun promptPlaylist(done: (String) -> Unit) {
        val input = EditText(this).apply { hint = "Nome da playlist"; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        AlertDialog.Builder(this).setTitle("Nova playlist").setView(input).setPositiveButton("Criar") { _, _ ->
            val name = input.text.toString().trim(); if (name.isNotBlank()) { store.createPlaylist(name); done(name) }
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun connectToYouTubeMusic(): Boolean {
        if (!hasNotificationAccess()) return false
        return try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MusicNotificationListener::class.java)
            val candidate = manager.getActiveSessions(component).firstOrNull { it.packageName == YTM_PACKAGE }
            if (candidate != null) {
                if (controller?.sessionToken != candidate.sessionToken) {
                    controller?.unregisterCallback(controllerCallback); controller = candidate; controller?.registerCallback(controllerCallback)
                }
                refreshMediaUi(); true
            } else { controller = null; refreshMediaUi(); false }
        } catch (_: SecurityException) { false }
    }

    private fun refreshMediaUi() {
        runOnUiThread {
            val meta = controller?.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            val art: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            val connected = !title.isNullOrBlank()
            miniPlayer.visibility = if (connected) View.VISIBLE else View.GONE
            if (connected) {
                miniTitle.text = title; miniArtist.text = artist ?: "YouTube Music"; if (art != null) miniArt.setImageBitmap(art) else miniArt.setImageDrawable(null)
            }
            playerTitle?.text = title ?: "Nenhuma música conectada"
            playerArtist?.text = artist ?: "Abra o YouTube Music e comece uma faixa"
            if (art != null) playerArt?.setImageBitmap(art) else playerArt?.setImageDrawable(null)
            val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
            miniPlay.text = if (playing) "❚❚" else "▶"; playerPlay?.text = if (playing) "❚❚" else "▶"
            updateProgress()
        }
    }

    private fun updateProgress() {
        val c = controller ?: return
        val state = c.playbackState ?: return
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) pos += ((android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        pos = pos.coerceAtLeast(0).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
        if (duration > 0) playerSeek?.progress = ((pos * 1000L) / duration).toInt()
        playerTime?.text = "${formatTime(pos)} / ${formatTime(duration)}"
    }

    private fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    private fun hasNotificationAccess(): Boolean = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)
    private fun openNotificationAccess() = startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    private fun launchYouTubeMusic() {
        packageManager.getLaunchIntentForPackage(YTM_PACKAGE)?.let { startActivity(it) } ?: openMusicUri(Uri.parse("https://music.youtube.com"))
    }
    private fun openMusicSearch(q: String) = openMusicUri(Uri.parse("https://music.youtube.com/search?q=${Uri.encode(q)}"))
    private fun openMusicUri(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(YTM_PACKAGE)) }
        catch (_: ActivityNotFoundException) { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun loadBitmap(url: String, done: (Bitmap?) -> Unit) = thread {
        val bmp = runCatching { android.graphics.BitmapFactory.decodeStream(URL(url).openStream()) }.getOrNull()
        runOnUiThread { done(bmp) }
    }

    private fun highlightNav() {
        navButtons.forEachIndexed { index, button ->
            button.setTextColor(if (currentTab == index) DARK else TEXT)
            button.background = round(if (currentTab == index) activeTheme().accent else 0x00202020, 16)
            button.typeface = if (currentTab == index) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun card(padding: Int = 16) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = round(PANEL, 26); setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    }
    private fun section(s: String) = text(s, 19f, TEXT, true).apply { setPadding(dp(6), dp(7), dp(6), dp(8)) }
    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply { text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun primary(s: String, click: (View) -> Unit) = Button(this).apply { text = s; isAllCaps = false; setTextColor(DARK); typeface = Typeface.DEFAULT_BOLD; background = round(activeTheme().accent, 18); setOnClickListener(click) }
    private fun secondary(s: String, click: (View) -> Unit) = Button(this).apply { text = s; isAllCaps = false; setTextColor(TEXT); background = round(PANEL_SOFT, 18); setOnClickListener(click) }
    private fun control(s: String, click: (View) -> Unit) = secondary(s, click).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }

    private fun round(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radiusDp).toFloat(); setColor(color); if (strokeColor != null) setStroke(dp(1), strokeColor)
    }
    private fun formatTime(ms: Long): String { if (ms <= 0) return "0:00"; val s = ms / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
