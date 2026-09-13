package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class ShoppingPriceMatch(
    val store: String,
    val productName: String,
    val brand: String?,
    val packageText: String?,
    val price: Double,
    val regularPrice: Double?,
    val memberPrice: Double?,
    val comparisonPrice: Double?,
    val comparisonUnit: String?,
    val confirmedAt: String?,
    val sourceUrl: String?
)

data class ShoppingPriceResult(
    val query: String,
    val matches: List<ShoppingPriceMatch>,
    val error: String?
) {
    val cheapest: ShoppingPriceMatch? get() = matches.minByOrNull { it.price }
}

data class StoreBasketSummary(
    val store: String,
    val total: Double,
    val matchedItems: Int,
    val totalItems: Int
) {
    val complete: Boolean get() = totalItems > 0 && matchedItems == totalItems
}

data class ShoppingPriceComparison(
    val items: List<ShoppingPriceResult>,
    val fetchedAt: String,
    val attributionText: String,
    val attributionUrl: String,
    val pendingStores: List<String>,
    val note: String
) {
    val pricedItems: List<ShoppingPriceResult> get() = items.filter { it.cheapest != null }
    val splitBasketTotal: Double? get() = pricedItems.takeIf { it.isNotEmpty() }?.sumOf { it.cheapest!!.price }

    fun storeSummaries(): List<StoreBasketSummary> {
        val stores = items.flatMap { it.matches }.map { it.store }.distinct()
        return stores.map { store ->
            val selected = items.mapNotNull { item -> item.matches.filter { it.store == store }.minByOrNull { it.price } }
            StoreBasketSummary(
                store = store,
                total = selected.sumOf { it.price },
                matchedItems = selected.size,
                totalItems = items.size
            )
        }.sortedWith(compareByDescending<StoreBasketSummary> { it.complete }.thenBy { it.total })
    }

    val bestCompleteStore: StoreBasketSummary? get() = storeSummaries().filter { it.complete }.minByOrNull { it.total }
    val splitSavingsAgainstBestCompleteStore: Double? get() {
        val split = splitBasketTotal ?: return null
        val single = bestCompleteStore?.total ?: return null
        return (single - split).takeIf { it > 0.005 }
    }
}

object ShoppingPriceService {
    private const val ENDPOINT = "https://zigychfkpgypjuovgyqq.supabase.co/functions/v1/compare-prices"

    suspend fun compare(names: List<String>): ShoppingPriceComparison = withContext(Dispatchers.IO) {
        val clean = names.map(String::trim).filter(String::isNotBlank).distinct().take(20)
        require(clean.isNotEmpty()) { "Inga varor att jämföra" }

        val body = JSONObject().put("queries", JSONArray(clean)).toString()
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        require(code in 200..299) { "Prisjämförelsen svarade med fel $code" }

        val root = JSONObject(text)
        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        val results = buildList {
            repeat(itemsArray.length()) { index ->
                val item = itemsArray.getJSONObject(index)
                val matchesArray = item.optJSONArray("matches") ?: JSONArray()
                val matches = buildList {
                    repeat(matchesArray.length()) { matchIndex ->
                        val row = matchesArray.getJSONObject(matchIndex)
                        add(
                            ShoppingPriceMatch(
                                store = row.optString("chainLabel", row.optString("chain")),
                                productName = row.optString("name"),
                                brand = row.optString("brand").takeIf { it.isNotBlank() && it != "null" },
                                packageText = row.optString("package").takeIf { it.isNotBlank() && it != "null" },
                                price = row.optDouble("effectivePrice"),
                                regularPrice = row.optDouble("regularPrice").takeIf { !it.isNaN() },
                                memberPrice = row.optDouble("memberPrice").takeIf { !it.isNaN() },
                                comparisonPrice = row.optDouble("comparisonPrice").takeIf { !it.isNaN() },
                                comparisonUnit = row.optString("comparisonUnit").takeIf { it.isNotBlank() && it != "null" },
                                confirmedAt = row.optString("confirmedAt").takeIf { it.isNotBlank() && it != "null" },
                                sourceUrl = row.optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    }
                }
                add(
                    ShoppingPriceResult(
                        query = item.optString("query"),
                        matches = matches,
                        error = item.optString("error").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        val coverage = root.optJSONArray("storeCoverage") ?: JSONArray()
        val pending = buildList {
            repeat(coverage.length()) { index ->
                val row = coverage.getJSONObject(index)
                if (row.optString("status") != "live") add(row.optString("store"))
            }
        }
        val attribution = root.optJSONObject("attribution") ?: JSONObject()
        ShoppingPriceComparison(
            items = results,
            fetchedAt = root.optString("fetchedAt"),
            attributionText = attribution.optString("text", "Prisdata från extern källa"),
            attributionUrl = attribution.optString("url"),
            pendingStores = pending,
            note = root.optString("note")
        )
    }
}
