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

enum class EditTarget { Digits1, Digits2, Icon, Masks, Fixed, Wallpaper, Hidden }

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

    /** Caché de la capa ya procesada (píxeles ARGB). */
    fun layerCache(mode: ScreenMode, p: Int) = "layer_${mode.name.lowercase()}_${p + 1}.raw"

    /** Imágenes que se guardan decodificadas en memoria. */
    val decoded = pages + CLEAN + WALL

    /** Capturas de entrada del modo capas (solo se guardan; la capa resultante se calcula). */
    val raw = (0..2).flatMap { listOf(bwDark(it), bwLight(it)) }

    fun modeOfRaw(@Suppress("UNUSED_PARAMETER") slot: String) = ScreenMode.LAYERS_BW
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

    /** Secciones desplegadas de la pantalla de configuración (solo en memoria). */
    val openSections = mutableStateMapOf("efecto" to true)

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
                    val out = Matting.blackWhite(px[0], px[1])
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

/** Listas de ejemplo para la primera vez (se pueden borrar o editar): 8 listas con entre 35 y 50 elementos cada una. */
object SampleLists {
    fun build(): List<MagicList> = listOf(
        MagicList(1, "Juegos de mesa", split(
            "Catan,Carcassonne,Ticket to Ride,Pandemic,Dixit,7 Wonders,Agricola,Azul,Codenames,Terraforming Mars," +
            "Gloomhaven,Brass,Wingspan,Dominion,Puerto Rico,Twilight Struggle,Scythe,Root,Everdell,Cascadia,Patchwork," +
            "Jaipur,Kingdomino,Sagrada,Takenoko,Love Letter,Citadels,Resistance,Mysterium,Cluedo,Risk,Monopoly," +
            "Stratego,Hive,Jenga,Uno,Perudo,Rummikub,Tzolk'in,Terra Mystica,Great Western Trail,Lost Cities,Santorini",
        )),
        MagicList(2, "Música", split(
            "Queen,Pink Floyd,Radiohead,Nirvana,Oasis,Metallica,AC/DC,Led Zeppelin,The Beatles,Rolling Stones,U2," +
            "Coldplay,Muse,Depeche Mode,The Cure,Pearl Jam,Foo Fighters,Green Day,Blur,Pulp,Los Chikos del Maíz," +
            "Fito y Fitipaldis,Platero y Tú,Marea,Los Suaves,Barricada,Héroes del Silencio,Mecano,Radio Futura," +
            "Los Secretos,Duncan Dhu,Siniestro Total,La Oreja de Van Gogh,Estopa,Amaral,Love of Lesbian,Izal," +
            "Vetusta Morla",
        )),
        MagicList(3, "Países", split(
            "España,Portugal,Andorra,Italia,Alemania,Suiza,Austria,Bélgica,Holanda,Luxemburgo,Reino Unido,Irlanda," +
            "Dinamarca,Suecia,Noruega,Finlandia,Polonia,Chequia,Hungría,Croacia,Grecia,Turquía,Marruecos,Egipto,Túnez," +
            "México,Cuba,Argentina,Chile,Perú,Estados Unidos,Canadá,Japón,Tailandia,Vietnam,India,Islandia,Malta," +
            "Eslovenia,Rumanía,Bulgaria,Lituania,Letonia,Estonia,Serbia,Montenegro,Albania",
        )),
        MagicList(4, "Sueños", split(
            "Viajar por el mundo,Aprender a tocar el piano,Escribir un libro,Correr un maratón," +
            "Tener una casa en el campo,Ver la aurora boreal,Hacer paracaidismo,Aprender japonés," +
            "Montar un negocio propio,Dar la vuelta al mundo en velero,Plantar un árbol,Bucear con tiburones," +
            "Ir a un mundial de fútbol,Hacer el Camino de Santiago,Aprender a surfear,Tener un perro grande," +
            "Recorrer la Ruta 66,Ver una ópera en Viena,Pilotar una avioneta,Dormir en un iglú," +
            "Ver las pirámides de Egipto,Hacer un curso de cocina en Italia,Montar en globo aerostático," +
            "Aprender a bailar tango,Visitar Machu Picchu,Escalar una gran montaña,Vivir un año en otro país," +
            "Pintar un cuadro,Montar una banda de música,Ir a Disneyland,Ver el amanecer en Angkor Wat," +
            "Hacer un safari en África,Tener una biblioteca propia,Aprender a bucear,Ayudar como voluntario," +
            "Conocer a mi ídolo",
        )),
        MagicList(5, "Películas", split(
            "El padrino,Casablanca,Pulp Fiction,El señor de los anillos,Matrix,Gladiator,Titanic,Forrest Gump," +
            "La lista de Schindler,El club de la lucha,Origen,Interstellar,Regreso al futuro,Jurassic Park,Star Wars," +
            "Indiana Jones,Alien,Blade Runner,El resplandor,Psicosis,Cantando bajo la lluvia,Cinema Paradiso," +
            "La vida es bella,Amélie,El laberinto del fauno,Los otros,Ocho apellidos vascos,Mar adentro,Volver," +
            "Todo sobre mi madre,El Rey León,Toy Story,Buscando a Nemo,Up,Shrek,Los Increíbles,Coco,Ratatouille," +
            "WALL·E,Harry Potter y la piedra filosofal,Piratas del Caribe,Los Goonies,E.T.,Tiburón,Taxi Driver," +
            "Apocalypse Now,El silencio de los corderos,Seven,Memento",
        )),
        MagicList(6, "Marca de coches", split(
            "Seat,Renault,Peugeot,Citroën,Ford,Opel,Volkswagen,Audi,BMW,Mercedes-Benz,Toyota,Honda,Nissan,Mazda," +
            "Hyundai,Kia,Fiat,Alfa Romeo,Volvo,Skoda,Dacia,Mini,Porsche,Ferrari,Lamborghini,Maserati,Tesla,Jaguar," +
            "Land Rover,Suzuki,Mitsubishi,Subaru,Jeep,Chevrolet,Cupra",
        )),
        MagicList(7, "Libros", split(
            "Don Quijote de la Mancha,Cien años de soledad,1984,Rebelión en la granja,El Principito," +
            "La sombra del viento,Los pilares de la Tierra,El nombre de la rosa,Orgullo y prejuicio,Jane Eyre," +
            "Cumbres borrascosas,Crimen y castigo,Los hermanos Karamázov,Ana Karénina,Guerra y paz,Madame Bovary," +
            "Los miserables,El conde de Montecristo,Veinte mil leguas de viaje submarino,La isla del tesoro," +
            "Frankenstein,Drácula,El retrato de Dorian Gray,Fahrenheit 451,Un mundo feliz," +
            "El guardián entre el centeno,Matar a un ruiseñor,El gran Gatsby,Ensayo sobre la ceguera," +
            "La casa de los espíritus,Pedro Páramo,Rayuela,El túnel,La ciudad y los perros,Platero y yo,La Regenta," +
            "El Lazarillo de Tormes,La colmena,Los renglones torcidos de Dios,Patria,El Hobbit",
        )),
        MagicList(8, "Videojuegos", split(
            "Super Mario Bros,The Legend of Zelda,Tetris,Pac-Man,Sonic,Pokémon Rojo,Minecraft,Fortnite,FIFA," +
            "Grand Theft Auto V,Red Dead Redemption 2,The Witcher 3,Skyrim,Fallout 4,Dark Souls,Elden Ring,God of War," +
            "The Last of Us,Uncharted,Resident Evil 4,Metal Gear Solid,Final Fantasy VII,Kingdom Hearts," +
            "Street Fighter II,Mortal Kombat,Doom,Half-Life,Portal,Counter-Strike,League of Legends,World of Warcraft," +
            "StarCraft,Age of Empires,Civilization,Los Sims,Animal Crossing,Stardew Valley,Celeste,Hollow Knight," +
            "Hades,Among Us,Rocket League,Overwatch,Assassin's Creed,Mario Kart",
        )),
    )

    private fun split(s: String) = s.split(",")
}
