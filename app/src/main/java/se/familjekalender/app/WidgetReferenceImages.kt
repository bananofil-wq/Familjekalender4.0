package se.familjekalender.app

/**
 * Source-faithful widget images cropped from the approved reference.
 * Preview images are exactly the reference artwork; material images have
 * the sample labels removed and can sit beneath live RemoteViews.
 *
 * This never changes the existing "Nuvarande" or "Trä" themes.
 */
internal object WidgetReferenceImages {
    fun image(skin: WidgetThemeColors.Skin, part: String): Int? = when (skin) {
        WidgetThemeColors.Skin.FOREST_WOOD -> when (part) {
            "preview" -> R.drawable.widget_ref_forest_wood_preview
            "material" -> R.drawable.widget_ref_forest_wood_material
            "control" -> R.drawable.widget_ref_forest_wood_control
            "row" -> R.drawable.widget_ref_forest_wood_row
            "reminder_row" -> R.drawable.widget_ref_forest_wood_reminder_row
            "add" -> R.drawable.widget_ref_forest_wood_add
            "reminder" -> R.drawable.widget_ref_forest_wood_reminder
            "badge_day" -> R.drawable.widget_ref_forest_wood_badge_day
            "badge_number" -> R.drawable.widget_ref_forest_wood_badge_number
            "week_badge" -> R.drawable.widget_ref_forest_wood_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.OBSIDIAN -> when (part) {
            "preview" -> R.drawable.widget_ref_obsidian_preview
            "material" -> R.drawable.widget_ref_obsidian_material
            "control" -> R.drawable.widget_ref_obsidian_control
            "row" -> R.drawable.widget_ref_obsidian_row
            "reminder_row" -> R.drawable.widget_ref_obsidian_reminder_row
            "add" -> R.drawable.widget_ref_obsidian_add
            "reminder" -> R.drawable.widget_ref_obsidian_reminder
            "badge_day" -> R.drawable.widget_ref_obsidian_badge_day
            "badge_number" -> R.drawable.widget_ref_obsidian_badge_number
            "week_badge" -> R.drawable.widget_ref_obsidian_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.CARBON_RED -> when (part) {
            "preview" -> R.drawable.widget_ref_carbon_red_preview
            "material" -> R.drawable.widget_ref_carbon_red_material
            "control" -> R.drawable.widget_ref_carbon_red_control
            "row" -> R.drawable.widget_ref_carbon_red_row
            "reminder_row" -> R.drawable.widget_ref_carbon_red_reminder_row
            "add" -> R.drawable.widget_ref_carbon_red_add
            "reminder" -> R.drawable.widget_ref_carbon_red_reminder
            "badge_day" -> R.drawable.widget_ref_carbon_red_badge_day
            "badge_number" -> R.drawable.widget_ref_carbon_red_badge_number
            "week_badge" -> R.drawable.widget_ref_carbon_red_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.MIST_FOREST -> when (part) {
            "preview" -> R.drawable.widget_ref_mist_forest_preview
            "material" -> R.drawable.widget_ref_mist_forest_material
            "control" -> R.drawable.widget_ref_mist_forest_control
            "row" -> R.drawable.widget_ref_mist_forest_row
            "reminder_row" -> R.drawable.widget_ref_mist_forest_reminder_row
            "add" -> R.drawable.widget_ref_mist_forest_add
            "reminder" -> R.drawable.widget_ref_mist_forest_reminder
            "badge_day" -> R.drawable.widget_ref_mist_forest_badge_day
            "badge_number" -> R.drawable.widget_ref_mist_forest_badge_number
            "week_badge" -> R.drawable.widget_ref_mist_forest_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.NORDIC_DUSK -> when (part) {
            "preview" -> R.drawable.widget_ref_nordic_dusk_preview
            "material" -> R.drawable.widget_ref_nordic_dusk_material
            "control" -> R.drawable.widget_ref_nordic_dusk_control
            "row" -> R.drawable.widget_ref_nordic_dusk_row
            "reminder_row" -> R.drawable.widget_ref_nordic_dusk_reminder_row
            "add" -> R.drawable.widget_ref_nordic_dusk_add
            "reminder" -> R.drawable.widget_ref_nordic_dusk_reminder
            "badge_day" -> R.drawable.widget_ref_nordic_dusk_badge_day
            "badge_number" -> R.drawable.widget_ref_nordic_dusk_badge_number
            "week_badge" -> R.drawable.widget_ref_nordic_dusk_week_badge
            else -> null
        }
        else -> null
    }

    fun previewResource(choice: String): Int? =
        WidgetThemeColors.premiumLook(choice)?.skin?.let { image(it, "preview") }
}
