package se.familjekalender.app

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline-first Swedish grocery/product catalog. Never assigns a category based on a
 * single very short substring ("te", "fil", "ris") which misclassified unrelated items.
 * Existing Supabase shopping rows remain untouched; category is derived consistently
 * from the product name on every family device.
 */
internal object SmartShoppingCatalog {
    val categoryOrder = listOf(
        "Frukt & grönt", "Bröd & bageri", "Mejeri", "Kött & fisk",
        "Skafferi", "Frys", "Dryck", "Snacks & godis",
        "Hygien", "Hushåll", "Bygg & renovering", "Flytt & förvaring",
        "Hem & fritid", "Övrigt",
    )

    val categoryEmoji = mapOf(
        "Frukt & grönt" to "🍏", "Bröd & bageri" to "🥐", "Mejeri" to "🥛",
        "Kött & fisk" to "🥩", "Skafferi" to "🥫", "Frys" to "❄️",
        "Dryck" to "🧃", "Snacks & godis" to "🍫", "Hygien" to "🧴",
        "Hushåll" to "🧽", "Bygg & renovering" to "🪣",
        "Flytt & förvaring" to "📦", "Hem & fritid" to "🏠",
        "Övrigt" to "🛒",
    )

    private val quantityPrefix =
        Regex("""^\s*\d+(?:[.,]\d+)?\s*(?:kg|g|l|dl|cl|ml|st|förp|paket|pkt)?\s*""")

    fun normalized(name: String): String =
        name.lowercase(Locale.forLanguageTag("sv-SE"))
            .replace(quantityPrefix, "")
            .trim()
            .replace(Regex("""\s+"""), " ")

    private val rules = listOf(
        "Flytt & förvaring" to listOf(
            "flytt", "förvar", "vakuumpås", "packtejp", "kartong", "plastback",
            "förvaringspås", "flyttlåd", "ikeakass", "ikeapås", "packmaterial",
        ),
        "Bygg & renovering" to listOf(
            "spackel", "takfärg", "väggfärg", "målarfärg", "färgburk", "färgroller",
            "färgtråg", "sandpapper", "slippapper", "slipmask", "pensel", "roller",
            "tapet", "renover", "skruv", "spik", "plugg", "silikon", "fogmassa",
            "fogsvans", "kakel", "borr", "väggfärg", "maskeringstejp",
            "snickerifärg", "lackfärg", "målartejp", "spackelspade",
        ),
        "Hushåll" to listOf(
            "klisterborttag", "klister borttag", "rengör", "diskmedel", "disktrasa",
            "diskborste", "diskmaskin", "tvättmedel", "sköljmedel", "fläckborttag",
            "soppås", "hushållspapper", "toalettpapper", "städ", "sopborste",
            "dammsug", "tvättsvamp", "tvättlapp", "folie", "bakplåtspapper",
            "fryspås", "plastfolie", "rengöringsmedel", "golvmopp", "matlåda",
        ),
        "Hygien" to listOf(
            "schampo", "balsam", "duschkräm", "duschtvål", "handtvål", "tvål",
            "tandkräm", "tandborst", "tandtråd", "deo", "deodorant", "blöj",
            "våtservett", "hudkräm", "bindor", "tampong", "rakgel", "rakhyvel",
            "hygien", "bomull", "plåster", "shampoo",
        ),
        "Frys" to listOf(
            "fryst", "frysta", "frysmat", "glass", "pommes", "fiskpinnar",
            "frysta bär", "frysta grönsaker", "wokmix", "glasspinne",
        ),
        "Snacks & godis" to listOf(
            "chips", "popcorn", "godis", "choklad", "lakrits", "nötmix",
            "jordnöt", "cashew", "vingummi", "lösgodis", "ostbågar", "kexchoklad",
        ),
        "Frukt & grönt" to listOf(
            "äpp", "banan", "päron", "apelsin", "citron", "lime", "gurk", "tomat",
            "sallad", "rödlök", "gul lök", "vitlök", "salladslök", "potatis",
            "morot", "paprika", "avokado", "broccoli", "blomkål", "spenat",
            "vindruv", "jordgubb", "blåbär", "hallon", "kiwi", "melon",
            "mango", "champinjon", "zucchini", "aubergin", "frukt", "grönsak",
        ),
        "Bröd & bageri" to listOf(
            "bröd", "fralla", "bulle", "baguette", "knäckebröd", "kanelbulle",
            "croissant", "toast", "tortilla", "hamburgerbröd", "korvbröd",
        ),
        "Mejeri" to listOf(
            "mjölk", "yoghurt", "yogurt", "grädde", "vispgrädde", "matgrädde",
            "kvarg", "creme fraiche", "crème fraiche", "fetaost", "ost",
            "smör", "margarin", "ägg", "filmjölk", "keso", "kesella",
            "halloumi", "mozzarella",
        ),
        "Kött & fisk" to listOf(
            "kyckling", "nötfärs", "blandfärs", "köttfärs", "färs", "kött",
            "fläsk", "korv", "bacon", "skinka", "lax", "torsk", "räkor",
            "fisk", "tonfisk", "kassler", "kalkon", "salami",
        ),
        "Dryck" to listOf(
            "juice", "läsk", "cola", "pepsi", "saft", "mineralvatten",
            "bubbelvatten", "kolsyrat vatten", "energidryck", "läskedryck",
            "smoothie", "sportdryck", "festis",
        ),
        "Skafferi" to listOf(
            "kaffe", "tepås", "svart te", "grönt te", "pasta", "spaghetti",
            "makaron", "jasminris", "basmatiris", "risgryn", "ris",
            "havregryn", "mjöl", "socker", "flingor", "müsli", "konserv",
            "tomatpuré", "krossade tomater", "olja", "olivolja", "krydd",
            "buljong", "ketchup", "senap", "majonnäs", "sylt", "honung",
            "bönor", "linser", "nudlar", "sås", "bakpulver", "salt", "peppar",
        ),
        "Hem & fritid" to listOf(
            "batteri", "glödlampa", "ledlampa", "ljusslinga", "blomkruk",
            "ljus", "värmeljus", "kudde", "filt", "handduk", "lakan", "galge",
            "present", "leksak", "usb-kabel", "lampskärm",
        ),
    )

    fun categoryFor(name: String): String {
        val key = normalized(name)
        return rules.firstOrNull { (_, tokens) -> tokens.any { key.contains(it) } }?.first
            ?: "Övrigt"
    }

    fun emojiFor(name: String): String {
        val key = normalized(name)
        return when {
            "spackel" in key -> "🪣"
            "färg" in key || "pensel" in key || "roller" in key -> "🎨"
            "sandpapper" in key || "slippapper" in key -> "🟨"
            "flytt" in key || "kartong" in key -> "📦"
            "förvar" in key -> "🧳"
            "klister" in key || "rengör" in key -> "🧴"
            "banan" in key -> "🍌"
            "tomat" in key -> "🍅"
            "gurk" in key -> "🥒"
            "äpp" in key -> "🍎"
            "päron" in key -> "🍐"
            "apelsin" in key -> "🍊"
            "citron" in key -> "🍋"
            "avokado" in key -> "🥑"
            "morot" in key -> "🥕"
            "potatis" in key -> "🥔"
            "paprika" in key -> "🫑"
            "mjölk" in key -> "🥛"
            "ost" in key -> "🧀"
            "ägg" in key -> "🥚"
            "yoghurt" in key || "fil" in key -> "🥣"
            "kyckling" in key -> "🍗"
            "kött" in key || "färs" in key -> "🥩"
            "korv" in key -> "🌭"
            "lax" in key || "fisk" in key -> "🐟"
            "bröd" in key -> "🍞"
            "pasta" in key -> "🍝"
            key.startsWith("ris") -> "🍚"
            "kaffe" in key -> "☕"
            "glass" in key -> "🍨"
            "tvätt" in key -> "🧺"
            "disk" in key -> "🧽"
            "toalett" in key -> "🧻"
            "schampo" in key || "tvål" in key -> "🧴"
            "läsk" in key || "cola" in key -> "🥤"
            "juice" in key || "saft" in key -> "🧃"
            "chips" in key -> "🥔"
            "choklad" in key -> "🍫"
            else -> categoryEmoji[categoryFor(name)] ?: "🛍️"
        }
    }

    private val commonProducts = listOf(
        "Äpplen", "Bananer", "Päron", "Apelsiner", "Citroner", "Gurka",
        "Tomater", "Potatis", "Morötter", "Paprika", "Broccoli", "Avokado",
        "Jordgubbar", "Vindruvor", "Rödlök", "Gul lök", "Vitlök", "Sallad",
        "Bröd", "Rågbröd", "Knäckebröd", "Hamburgerbröd", "Korvbröd", "Frallor",
        "Mjölk", "Mellanmjölk", "Havremjölk", "Fil", "Yoghurt", "Kvarg",
        "Smör", "Ost", "Ägg", "Grädde", "Crème fraiche", "Keso",
        "Kyckling", "Köttfärs", "Blandfärs", "Korv", "Bacon", "Lax", "Torsk",
        "Skinka", "Räkor", "Fiskpinnar",
        "Kaffe", "Te", "Pasta", "Spaghetti", "Makaroner", "Ris", "Mjöl",
        "Socker", "Havregryn", "Müsli", "Olivolja", "Salt", "Peppar",
        "Ketchup", "Senap", "Krossade tomater", "Tomatpuré", "Nudlar",
        "Frysta grönsaker", "Pommes", "Glass", "Frysta bär", "Fryst pizza",
        "Juice", "Läsk", "Mineralvatten", "Saft", "Energidryck",
        "Chips", "Popcorn", "Godis", "Choklad", "Nötter",
        "Schampo", "Balsam", "Duschkräm", "Tvål", "Tandkräm", "Tandborste",
        "Blöjor", "Våtservetter", "Deodorant", "Plåster",
        "Diskmedel", "Diskborste", "Tvättmedel", "Sköljmedel",
        "Hushållspapper", "Toalettpapper", "Soppåsar", "Fryspåsar",
        "Klisterborttagning", "Rengöringsmedel",
        "Spackel", "Spackelspade", "Takfärg", "Väggfärg", "Sandpapper",
        "Pensel", "Färgroller", "Maskeringstejp", "Skruvar", "Pluggar",
        "Flyttlådor", "Flyttkartonger", "Packtejp", "Förvaringspåsar Ikea",
        "Förvaringslådor", "Vakuumpåsar", "Plastbackar", "Batterier", "Glödlampor",
    )

    /** Suggestions never use a remote API, so typing is instant and works offline. */
    fun suggestions(input: String, existingNames: List<String>): List<String> {
        val key = normalized(input)
        if (key.length < 2 || key.contains(',') || key.contains(';')) return emptyList()
        return (existingNames + commonProducts)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(::normalized)
            .filter { normalized(it).contains(key) }
            .sortedWith(
                compareBy<String> {
                    val n = normalized(it)
                    when {
                        n == key -> 0
                        n.startsWith(key) -> 1
                        n.split(' ').any { word -> word.startsWith(key) } -> 2
                        else -> 3
                    }
                }.thenBy { normalized(it).length }
                    .thenBy { normalized(it) }
            )
            .take(4)
    }

    fun allowsFoodPhoto(name: String): Boolean =
        categoryFor(name) in setOf(
            "Frukt & grönt", "Bröd & bageri", "Mejeri", "Kött & fisk",
            "Skafferi", "Frys", "Dryck", "Snacks & godis",
        )
}

/**
 * Actual product photos for food when available, with matching offline pictograms
 * everywhere else. Requests happen on selection/display, never for every keystroke.
 * Serialized requests are limited to <= 8/minute, below Open Food Facts' 10/min.
 */
internal object ShoppingProductPhotos {
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, ImageBitmap?>()
    private var lastRequestMs = 0L

    suspend fun photoFor(name: String): ImageBitmap? {
        if (!SmartShoppingCatalog.allowsFoodPhoto(name)) return null
        val key = SmartShoppingCatalog.normalized(name)
        if (key.length < 3) return null
        return mutex.withLock {
            if (memory.containsKey(key)) return@withLock memory[key]
            val elapsed = SystemClock.elapsedRealtime() - lastRequestMs
            if (lastRequestMs > 0 && elapsed < 8_000L) delay(8_000L - elapsed)
            lastRequestMs = SystemClock.elapsedRealtime()
            val photo = withContext(Dispatchers.IO) { fetchPhoto(key) }
            memory[key] = photo
            photo
        }
    }

    private fun fetchPhoto(query: String): ImageBitmap? = runCatching {
        val terms = URLEncoder.encode(query, "UTF-8")
        val endpoint = URL(
            "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" + terms +
                "&search_simple=1&action=process&json=1&page_size=5" +
                "&fields=product_name,product_name_sv,image_front_small_url"
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            connectTimeout = 3500
            readTimeout = 4500
            setRequestProperty(
                "User-Agent",
                "Familjekalendern/4.3 (https://github.com/bananofil-wq/Familjekalender4.0)"
            )
        }
        val products = try {
            connection.inputStream.bufferedReader().use {
                JSONObject(it.readText()).optJSONArray("products")
            }
        } finally {
            connection.disconnect()
        } ?: return@runCatching null

        val imageUrl = (0 until products.length()).asSequence()
            .map { products.getJSONObject(it) }
            .firstOrNull {
                val title = (it.optString("product_name_sv") + " " + it.optString("product_name"))
                    .lowercase(Locale.forLanguageTag("sv-SE"))
                title.contains(query)
                    && it.optString("image_front_small_url")
                        .startsWith("https://images.openfoodfacts.org/")
            }
            ?.optString("image_front_small_url")
            ?: return@runCatching null

        val imageConnection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3500
            readTimeout = 4500
            setRequestProperty(
                "User-Agent",
                "Familjekalendern/4.3 (https://github.com/bananofil-wq/Familjekalender4.0)"
            )
        }
        try {
            imageConnection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } finally {
            imageConnection.disconnect()
        }
    }.getOrNull()
}

/** The picture reacts to the entered product; never shows the same bag for every item. */
@Composable
internal fun ShoppingProductPicture(
    name: String,
    modifier: Modifier = Modifier,
    loadPhoto: Boolean = true,
) {
    val photo = produceState<ImageBitmap?>(initialValue = null, name, loadPhoto) {
        value = if (loadPhoto) ShoppingProductPhotos.photoFor(name) else null
    }
    val accent = when (SmartShoppingCatalog.categoryFor(name)) {
        "Frukt & grönt" -> Color(0xFF426F51)
        "Mejeri" -> Color(0xFF4D7592)
        "Bygg & renovering" -> Color(0xFF73664B)
        "Flytt & förvaring" -> Color(0xFF756050)
        "Hushåll" -> Color(0xFF4B7077)
        else -> Color(0xFF59506F)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.verticalGradient(listOf(accent, Color(0xFF272332)))),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = photo.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Exempelbild: " + name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = SmartShoppingCatalog.emojiFor(name),
                fontSize = 25.sp,
            )
        }
    }
}
