package se.familjekalender.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the old photo crops. Those files had rendered "00", day
 * numbers and reminder text baked into the picture, causing double text.
 * Keep every live premium widget on text-free source components.
 */
class WidgetPremiumAssetsTest {
    @Test
    fun allFivePremiumSkinsUseTextFreeLiveSurfaces() {
        data class Expected(
            val choice: String,
            val row: Int,
            val control: Int,
            val reminder: Int,
            val reminderRow: Int,
            val day: Int,
            val date: Int,
            val week: Int,
        )

        val examples = listOf(
            Expected("PREMIUM_FOREST_WOOD",
                R.drawable.widget_photo_forest_wood_row,
                R.drawable.widget_photo_forest_wood_control,
                R.drawable.widget_photo_forest_wood_reminder,
                R.drawable.widget_clean_forest_wood_reminder_row,
                R.drawable.widget_premium_forest_wood_badge_day,
                R.drawable.widget_premium_forest_wood_badge_number,
                R.drawable.widget_premium_forest_wood_week_badge),
            Expected("PREMIUM_OBSIDIAN",
                R.drawable.widget_photo_obsidian_row,
                R.drawable.widget_photo_obsidian_control,
                R.drawable.widget_photo_obsidian_reminder,
                R.drawable.widget_clean_obsidian_reminder_row,
                R.drawable.widget_premium_obsidian_badge_day,
                R.drawable.widget_premium_obsidian_badge_number,
                R.drawable.widget_premium_obsidian_week_badge),
            Expected("PREMIUM_CARBON_RED",
                R.drawable.widget_photo_carbon_red_row,
                R.drawable.widget_photo_carbon_red_control,
                R.drawable.widget_photo_carbon_red_reminder,
                R.drawable.widget_clean_carbon_red_reminder_row,
                R.drawable.widget_premium_carbon_red_badge_day,
                R.drawable.widget_premium_carbon_red_badge_number,
                R.drawable.widget_premium_carbon_red_week_badge),
            Expected("PREMIUM_MIST_FOREST",
                R.drawable.widget_photo_mist_forest_row,
                R.drawable.widget_photo_mist_forest_control,
                R.drawable.widget_photo_mist_forest_reminder,
                R.drawable.widget_clean_mist_forest_reminder_row,
                R.drawable.widget_premium_mist_forest_badge_day,
                R.drawable.widget_premium_mist_forest_badge_number,
                R.drawable.widget_premium_mist_forest_week_badge),
            Expected("PREMIUM_NORDIC_DUSK",
                R.drawable.widget_photo_nordic_dusk_row,
                R.drawable.widget_photo_nordic_dusk_control,
                R.drawable.widget_photo_nordic_dusk_reminder,
                R.drawable.widget_clean_nordic_dusk_reminder_row,
                R.drawable.widget_premium_nordic_dusk_badge_day,
                R.drawable.widget_premium_nordic_dusk_badge_number,
                R.drawable.widget_premium_nordic_dusk_week_badge),
        )

        examples.forEach { item ->
            val surfaces = WidgetThemeColors.previewMaterials(item.choice)
            assertEquals(item.choice + " row", item.row, surfaces.row)
            assertEquals(item.choice + " control", item.control, surfaces.todo)
            assertEquals(item.choice + " reminder", item.reminder, surfaces.reminder)
            assertEquals(item.choice + " reminderRow", item.reminderRow, surfaces.reminderRow)
            assertEquals(item.choice + " day", item.day, surfaces.badgeDay)
            assertEquals(item.choice + " date", item.date, surfaces.badgeNumber)
            assertEquals(item.choice + " week", item.week, surfaces.weekBadge)
        }
    }
}
