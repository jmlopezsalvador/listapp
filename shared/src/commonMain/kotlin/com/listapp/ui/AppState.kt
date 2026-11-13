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
import com.listapp.domain.UndoEntry
import kotlinx.serialization.builtins.ListSerializer

/** Pantallas de la app. */
sealed interface Screen {
    data object Home : Screen
    data object Lists : Screen
    data class Detail(val listId: Long) : Screen
    data object Config : Screen
    data class Editor(val target: EditTarget) : Screen
}

enum class EditTarget { Digits1, Digits2, Icon, Masks }

/** Nombres de los ficheros de imagen: una por página del escritorio y la «imagen limpia» de las máscaras. */
object Slots {
    val pages = listOf("screen1.img", "screen2.img", "screen3.img")
    const val CLEAN = "clean.img"
    val all = pages + CLEAN
}

/** Estado global: listas, configuración secreta, dígitos elegidos e imágenes. */
class AppState(private val kv: KeyValueStore, private val blobs: BlobStore) {
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

    init {
        // Arrancar la app = reset: si quedó algo insertado de la sesión anterior, se deshace.
        undoInserted()
        for (slot in Slots.all) blobs.read(slot)?.let(::decodeImage)?.let { images[slot] = it }
    }

    // ---------- Configuración ----------
    fun updateConfig(f: (MagicConfig) -> MagicConfig) {
        config = f(config)
        kv.putString(KEY_CONFIG, AppJson.encodeToString(MagicConfig.serializer(), config))
    }

    fun setImage(slot: String, bytes: ByteArray) {
        val bmp = decodeImage(bytes) ?: return
        blobs.write(slot, bytes)
        images[slot] = bmp
    }

    fun clearImage(slot: String) {
        blobs.delete(slot)
        images.remove(slot)
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
