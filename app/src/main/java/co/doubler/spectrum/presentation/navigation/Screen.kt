package co.doubler.spectrum.presentation.navigation

import co.doubler.spectrum.domain.model.ScanMode
import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable data object Ghost : Screen
    @Serializable data object Compete : Screen
    @Serializable data object Bluetooth : Screen
    @Serializable data object MagField : Screen
    @Serializable data object Settings : Screen
}

fun ScanMode.toScreen(): Screen = when (this) {
    ScanMode.GHOST -> Screen.Ghost
    ScanMode.COMPETE -> Screen.Compete
    ScanMode.BLUETOOTH -> Screen.Bluetooth
    ScanMode.MAGNETIC -> Screen.MagField
}

fun Screen.tabIndex(): Int? = when (this) {
    is Screen.Ghost     -> 0
    is Screen.Compete   -> 1
    is Screen.Bluetooth -> 2
    is Screen.MagField  -> 3
    is Screen.Settings  -> null
}
