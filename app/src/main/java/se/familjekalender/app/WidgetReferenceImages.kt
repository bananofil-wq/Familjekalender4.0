package se.familjekalender.app

/**
 * The original five user-supplied images remain untouched reference assets.
 * Live widget and chooser preview parts only use clean, text-free photographic
 * surfaces, while dates, numbers and events are rendered as live text.
 * No calendar theme is changed.
 */
internal object WidgetReferenceImages {
    fun image(skin: WidgetThemeColors.Skin, part: String): Int? = when (skin) {
        WidgetThemeColors.Skin.FOREST_WOOD -> when (part) {
            "preview" -> R.drawable.widget_exact_forest_wood_reference
            "material" -> R.drawable.widget_exact_forest_wood_material
            "control" -> R.drawable.widget_photo_forest_wood_control
            "row" -> R.drawable.widget_photo_forest_wood_row
            "reminder_row" -> R.drawable.widget_clean_forest_wood_reminder_row
            "add" -> R.drawable.widget_photo_forest_wood_add
            "reminder" -> R.drawable.widget_photo_forest_wood_reminder
            "badge_day" -> R.drawable.widget_premium_forest_wood_badge_day
            "badge_number" -> R.drawable.widget_premium_forest_wood_badge_number
            "week_badge" -> R.drawable.widget_premium_forest_wood_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.OBSIDIAN -> when (part) {
            "preview" -> R.drawable.widget_exact_obsidian_reference
            "material" -> R.drawable.widget_exact_obsidian_material
            "control" -> R.drawable.widget_photo_obsidian_control
            "row" -> R.drawable.widget_photo_obsidian_row
            "reminder_row" -> R.drawable.widget_clean_obsidian_reminder_row
            "add" -> R.drawable.widget_photo_obsidian_add
            "reminder" -> R.drawable.widget_photo_obsidian_reminder
            "badge_day" -> R.drawable.widget_premium_obsidian_badge_day
            "badge_number" -> R.drawable.widget_premium_obsidian_badge_number
            "week_badge" -> R.drawable.widget_premium_obsidian_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.CARBON_RED -> when (part) {
            "preview" -> R.drawable.widget_exact_carbon_red_reference
            "material" -> R.drawable.widget_exact_carbon_red_material
            "control" -> R.drawable.widget_photo_carbon_red_control
            "row" -> R.drawable.widget_photo_carbon_red_row
            "reminder_row" -> R.drawable.widget_clean_carbon_red_reminder_row
            "add" -> R.drawable.widget_photo_carbon_red_add
            "reminder" -> R.drawable.widget_photo_carbon_red_reminder
            "badge_day" -> R.drawable.widget_premium_carbon_red_badge_day
            "badge_number" -> R.drawable.widget_premium_carbon_red_badge_number
            "week_badge" -> R.drawable.widget_premium_carbon_red_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.MIST_FOREST -> when (part) {
            "preview" -> R.drawable.widget_exact_mist_forest_reference
            "material" -> R.drawable.widget_exact_mist_forest_material
            "control" -> R.drawable.widget_photo_mist_forest_control
            "row" -> R.drawable.widget_photo_mist_forest_row
            "reminder_row" -> R.drawable.widget_clean_mist_forest_reminder_row
            "add" -> R.drawable.widget_photo_mist_forest_add
            "reminder" -> R.drawable.widget_photo_mist_forest_reminder
            "badge_day" -> R.drawable.widget_premium_mist_forest_badge_day
            "badge_number" -> R.drawable.widget_premium_mist_forest_badge_number
            "week_badge" -> R.drawable.widget_premium_mist_forest_week_badge
            else -> null
        }
        WidgetThemeColors.Skin.NORDIC_DUSK -> when (part) {
            "preview" -> R.drawable.widget_exact_nordic_dusk_reference
            "material" -> R.drawable.widget_exact_nordic_dusk_material
            "control" -> R.drawable.widget_photo_nordic_dusk_control
            "row" -> R.drawable.widget_photo_nordic_dusk_row
            "reminder_row" -> R.drawable.widget_clean_nordic_dusk_reminder_row
            "add" -> R.drawable.widget_photo_nordic_dusk_add
            "reminder" -> R.drawable.widget_photo_nordic_dusk_reminder
            "badge_day" -> R.drawable.widget_premium_nordic_dusk_badge_day
            "badge_number" -> R.drawable.widget_premium_nordic_dusk_badge_number
            "week_badge" -> R.drawable.widget_premium_nordic_dusk_week_badge
            else -> null
        }
        else -> null
    }

    fun previewResource(choice: String): Int? =
        WidgetThemeColors.premiumLook(choice)?.skin?.let { image(it, "preview") }
}
