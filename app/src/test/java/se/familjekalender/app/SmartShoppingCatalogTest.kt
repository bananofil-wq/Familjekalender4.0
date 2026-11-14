package se.familjekalender.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartShoppingCatalogTest {
    @Test
    fun screenshotProductsReceiveCorrectCategories() {
        assertEquals("Bygg & renovering", SmartShoppingCatalog.categoryFor("spackel"))
        assertEquals("Bygg & renovering", SmartShoppingCatalog.categoryFor("takfärg"))
        assertEquals("Bygg & renovering", SmartShoppingCatalog.categoryFor("sandpapper"))
        assertEquals("Flytt & förvaring", SmartShoppingCatalog.categoryFor("flyttlådor"))
        assertEquals("Flytt & förvaring", SmartShoppingCatalog.categoryFor("Förvaringspåsar Ikea"))
        assertEquals("Hushåll", SmartShoppingCatalog.categoryFor("klister borttagning"))
    }

    @Test
    fun normalGroceriesStayInFoodDepartments() {
        assertEquals("Mejeri", SmartShoppingCatalog.categoryFor("2 st mjölk"))
        assertEquals("Skafferi", SmartShoppingCatalog.categoryFor("kaffe"))
        assertEquals("Skafferi", SmartShoppingCatalog.categoryFor("Te"))
        assertEquals("Mejeri", SmartShoppingCatalog.categoryFor("Fil"))
        assertEquals("Frukt & grönt", SmartShoppingCatalog.categoryFor("tomater"))
        assertEquals("Övrigt", SmartShoppingCatalog.categoryFor("helt okänd vara"))
    }

    @Test
    fun suggestionsFilterByPrefixAndIncludeFamilyItems() {
        val results = SmartShoppingCatalog.suggestions(
            "spack",
            listOf("Spackel extra fin", "Glass", "mjölk"),
        )
        assertTrue(results.contains("Spackel"))
        assertTrue(results.contains("Spackel extra fin"))
        assertFalse(results.contains("Glass"))
        assertTrue(results.size <= 4)
    }

    @Test
    fun suggestionsIgnoreCaseAndDoNotRepeatDuplicates() {
        val results = SmartShoppingCatalog.suggestions(
            "flytt",
            listOf("Flyttlådor", "flyttlådor"),
        )
        assertEquals(1, results.count { it.equals("flyttlådor", ignoreCase = true) })
    }

    @Test
    fun imageUsesProductSpecificSymbolForRenovation() {
        assertEquals("📦", SmartShoppingCatalog.emojiFor("Flyttlådor"))
        assertEquals("🎨", SmartShoppingCatalog.emojiFor("Takfärg"))
    }
}
