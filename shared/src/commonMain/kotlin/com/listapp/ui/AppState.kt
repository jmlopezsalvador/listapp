package com.listapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.listapp.domain.AppJson
import com.listapp.domain.Force
import com.listapp.domain.MagicConfig
import com.listapp.domain.MagicList
import com.listapp.domain.Matting
import com.listapp.domain.ScreenMode
import com.listapp.domain.UndoEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/** Pantallas de la app. */
sealed interface Screen {
    data object Home : Screen
    data object Lists : Screen
    data class Detail(val listId: Long) : Screen
    data object Config : Screen
    data class Editor(val target: EditTarget) : Screen
}

enum class EditTarget { Digits1, Digits2, Icon, Masks, Fixed, Wallpaper }

/** Nombres de los ficheros de imagen. */
object Slots {
    /** Modo pantalla completa: una captura por página. */
    val pages = listOf("screen1.img", "screen2.img", "screen3.img")

    /** Captura sin los elementos a tapar (modo pantalla completa). */
    const val CLEAN = "clean.img"

    /** Modo capas: imagen del fondo de pantalla (fija). */
    const val WALL = "wall.img"

    /** Modo capas blanco/negro: capturas con fondo oscuro y claro de cada página (0..2). */
    fun bwDark(p: Int) = "bwdark${p + 1}.img"
    fun bwLight(p: Int) = "bwlight${p + 1}.img"

    /** Modo capas color único: captura sobre fondo liso de cada página (0..2). */
    fun key(p: Int) = "key${p + 1}.img"

    /** Caché de la capa ya procesada (píxeles ARGB). */
    fun layerCache(mode: ScreenMode, p: Int) = "layer_${mode.name.lowercase()}_${p + 1}.raw"

    /** Imágenes que se guardan decodificadas en memoria. */
    val decoded = pages + CLEAN + WALL

    /** Capturas de entrada del modo capas (solo se guardan; la capa resultante se calcula). */
    val raw = (0..2).flatMap { listOf(bwDark(it), bwLight(it), key(it)) }

    fun modeOfRaw(slot: String) = if (slot.startsWith("key")) ScreenMode.LAYERS_KEY else ScreenMode.LAYERS_BW
    fun pageOfRaw(slot: String) = slot.last { it.isDigit() }.digitToInt() - 1
}

/** Estado global: listas, configuración secreta, dígitos elegidos e imágenes. */
class AppState(private val kv: KeyValueStore, private val blobs: BlobStore, private val scope: CoroutineScope) {
    var config by mutableStateOf(loadConfig())
        private set
    var lists by mutableStateOf(loadLists())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)

    /** Dígitos capturados al deslizar en las páginas 1 y 2 (solo en memoria). */
    var tens by mutableStateOf<Int?>(null)
    var units by mutableStateOf<Int?>(null)

    /** Sube cada vez que hay un reset para que el escritorio vuelva a la primera página. */
    var resetCount by mutableIntStateOf(0)
        private set

    private var applied = false
    private var undo: List<UndoEntry> = loadUndo()
    val images = mutableStateMapOf<String, ImageBitmap>()

    /** Modo capas: capas de iconos ya procesadas (con transparencia), por página 0..2. */
    val layers = mutableStateMapOf<Int, ImageBitmap>()

    /** Modo capas: texto de estado del procesado de cada página. */
    val layerStatus = mutableStateMapOf<Int, String>()

    /** Capturas de entrada del modo capas que ya están guardadas. */
    val rawPresent = mutableStateMapOf<String, Boolean>()

    init {
        // Arrancar la app = reset: si quedó algo insertado de la sesión anterior, se deshace.
        undoInserted()
        for (slot in Slots.decoded) blobs.read(slot)?.let(::decodeImage)?.let { images[slot] = it }
        for (slot in Slots.raw) if (blobs.exists(slot)) rawPresent[slot] = true
        loadLayers()
    }

    // ---------- Configuración ----------
    fun updateConfig(f: (MagicConfig) -> MagicConfig) {
        config = f(config)
        kv.putString(KEY_CONFIG, AppJson.encodeToString(MagicConfig.serializer(), config))
    }

    /** Guarda una imagen elegida en la galería. Devuelve false si no se pudo leer. */
    fun setImage(slot: String, bytes: ByteArray): Boolean {
        val bmp = decodeImage(bytes) ?: return false
        blobs.write(slot, bytes)
        if (slot in Slots.raw) {
            rawPresent[slot] = true
            val mode = Slots.modeOfRaw(slot)
            val page = Slots.pageOfRaw(slot)
            blobs.delete(Slots.layerCache(mode, page))
            if (mode == config.mode) rebuildLayer(page)
        } else {
            images[slot] = bmp
        }
        return true
    }

    fun clearImage(slot: String) {
        blobs.delete(slot)
        if (slot in Slots.raw) {
            rawPresent.remove(slot)
            val mode = Slots.modeOfRaw(slot)
            val page = Slots.pageOfRaw(slot)
            blobs.delete(Slots.layerCache(mode, page))
            if (mode == config.mode) {
                layers.remove(page)
                layerStatus.remove(page)
            }
        } else {
            images.remove(slot)
        }
    }

    // ---------- Modo capas
    fun setMode(mode: ScreenMode) {
        if (config.mode == mode) return
        updateConfig { it.copy(mode = mode) }
        loadLayers()
    }

    /** Carga las capas del modo activo (de la caché; si falta, las recalcula a partir de las capturas). */
    fun loadLayers() {
        layers.clear()
        layerStatus.clear()
        val mode = config.mode
        if (mode == ScreenMode.FULL) return
        for (p in 0..2) {
            scope.launch {
                val cached = withContext(Dispatchers.Default) {
                    blobs.read(Slots.layerCache(mode, p))?.let { Matting.decodeLayer(it) }
                        ?.let { (w, h, px) -> imageFromPixels(w, h, px) }
                }
                if (config.mode != mode) return@launch
                if (cached != null) layers[p] = cached else rebuildLayer(p)
            }
        }
    }

    /** Calcula la capa con transparencia de una página a partir de sus capturas (en segundo plano). */
    fun rebuildLayer(page: Int) {
        val mode = config.mode
        val inputs = when (mode) {
            ScreenMode.LAYERS_BW -> listOf(Slots.bwDark(page), Slots.bwLight(page))
            ScreenMode.LAYERS_KEY -> listOf(Slots.key(page))
            ScreenMode.FULL -> return
        }
        if (inputs.any { rawPresent[it] != true }) {
            layers.remove(page)
            layerStatus.remove(page)
            return
        }
        layerStatus[page] = "Procesando…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val imgs = inputs.map { name ->
                        decodeImage(blobs.read(name) ?: error("Falta una captura")) ?: error("Imagen no válida")
                    }
                    val w = imgs[0].width
                    val h = imgs[0].height
                    require(imgs.all { it.width == w && it.height == h }) { "Las dos capturas deben tener el mismo tamaño" }
                    val px = imgs.map { img -> IntArray(w * h).also { img.readPixels(it) } }
                    val out = if (mode == ScreenMode.LAYERS_BW) Matting.blackWhite(px[0], px[1]) else Matting.colorKey(px[0])
                    blobs.write(Slots.layerCache(mode, page), Matting.encodeLayer(w, h, out))
                    imageFromPixels(w, h, out)
                }
                if (config.mode == mode) {
                    layers[page] = result
                    layerStatus[page] = "Lista"
                }
            } catch (e: Throwable) {
                layers.remove(page)
                layerStatus[page] = "Error: ${e.message ?: "no se pudo procesar"}"
            }
        }
    }

    // ---------- Listas ----------
    fun updateLists(f: (List<MagicList>) -> List<MagicList>) {
        lists = f(lists)
        kv.putString(KEY_LISTS, AppJson.encodeToString(ListSerializer(MagicList.serializer()), lists))
    }

    fun nextListId() = (lists.maxOfOrNull { it.id } ?: 0L) + 1

    // ---------- El truco ----------
    /** Al «abrir» la app de listas: si hay dos dígitos y aún no se ha hecho, inserta las palabras. */
    fun enterListApp() {
        if (!applied) {
            val n = Force.numberOf(tens, units)
            if (n != null) {
                val (newLists, u) = Force.apply(lists, config.pairs, n)
                undo = u
                kv.putString(KEY_UNDO, AppJson.encodeToString(ListSerializer(UndoEntry.serializer()), u))
                updateLists { newLists }
                applied = true
            }
        }
        screen = Screen.Lists
    }

    /** Reset: deshace lo insertado, olvida los dígitos y vuelve a la primera página. */
    fun reset() {
        undoInserted()
        tens = null
        units = null
        applied = false
        screen = Screen.Home
        resetCount++
    }

    private fun undoInserted() {
        if (undo.isNotEmpty()) {
            updateLists { Force.undo(it, undo) }
            undo = emptyList()
            kv.putString(KEY_UNDO, "[]")
        }
    }

    // ---------- Carga inicial ----------
    private fun loadConfig(): MagicConfig =
        kv.getString(KEY_CONFIG)?.let { runCatching { AppJson.decodeFromString(MagicConfig.serializer(), it) }.getOrNull() }
            ?: MagicConfig()

    private fun loadLists(): List<MagicList> {
        val s = kv.getString(KEY_LISTS)
        if (s == null) return SampleLists.build()
        return runCatching { AppJson.decodeFromString(ListSerializer(MagicList.serializer()), s) }.getOrElse { SampleLists.build() }
    }

    private fun loadUndo(): List<UndoEntry> =
        kv.getString(KEY_UNDO)?.let {
            runCatching { AppJson.decodeFromString(ListSerializer(UndoEntry.serializer()), it) }.getOrNull()
        } ?: emptyList()

    private companion object {
        const val KEY_CONFIG = "config"
        const val KEY_LISTS = "lists"
        const val KEY_UNDO = "undo"
    }
}

/** Listas de ejemplo para la primera vez (se pueden borrar o editar). */
object SampleLists {
    fun build(): List<MagicList> = listOf(
        MagicList(1, "Juegos de mesa", split(
            "Catan,Carcassonne,Ticket to Ride,Pandemic,Dixit,7 Wonders,Agricola,Azul,Codenames,Terraforming Mars," +
                "Gloomhaven,Brass,Wingspan,Dominion,Puerto Rico,Twilight Struggle,Scythe,Root,Everdell,Cascadia," +
                "Patchwork,Jaipur,Kingdomino,Sagrada,Takenoko,Love Letter,Citadels,Resistance,Mysterium,Cluedo," +
                "Risk,Monopoly,Stratego,Hive,Jenga,Uno,Perudo,Rummikub,Tzolk'in,Terra Mystica," +
                "Great Western Trail,Lost Cities,Santorini,Quacks of Quedlinburg,Just One,Concordia,Pandemic Legacy,Orleans,Zooloretto,Lords of Waterdeep")),
        MagicList(2, "Música", split(
            "Queen,Pink Floyd,Radiohead,Nirvana,Oasis,Metallica,AC/DC,Led Zeppelin,The Beatles,Rolling Stones," +
                "U2,Coldplay,Muse,Depeche Mode,The Cure,Pearl Jam,Foo Fighters,Green Day,Blur,Pulp," +
                "Los Chikos del Maíz,Fito y Fitipaldis,Platero y Tú,Marea,Los Suaves,Barricada,Héroes del Silencio,Mecano,Radio Futura,Los Secretos," +
                "Duncan Dhu,Siniestro Total,La Oreja de Van Gogh,Estopa,Amaral,Love of Lesbian,Izal,Vetusta Morla,Fangoria,Sidonie," +
                "Daft Punk,Gorillaz,Arctic Monkeys,The Strokes,Kings of Leon,Lori Meyers,Dani Martín,Leiva,Quique González,Calamaro")),
        MagicList(3, "Países visitados", split(
            "España,Portugal,Andorra,Italia,Alemania,Suiza,Austria,Bélgica,Holanda,Luxemburgo," +
                "Reino Unido,Irlanda,Dinamarca,Suecia,Noruega,Finlandia,Polonia,Chequia,Hungría,Croacia," +
                "Grecia,Turquía,Marruecos,Egipto,Túnez,México,Cuba,Argentina,Chile,Perú," +
                "Estados Unidos,Canadá,Japón,Tailandia,Vietnam,India,Islandia,Malta,Eslovenia,Rumanía," +
                "Bulgaria,Lituania,Letonia,Estonia,Serbia,Montenegro,Albania,Jordania,Mónaco,Bosnia")),
    )

    private fun split(s: String) = s.split(",")
}
