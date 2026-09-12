package se.familjekalender.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Local compatibility helper for state that only needs to survive recomposition.
 * This keeps the calendar add-menu request guard compile-safe without changing
 * the existing screen behavior elsewhere.
 */
@Composable
internal fun <T> rememberSaveable(calculation: () -> T): T = remember(calculation = calculation)
