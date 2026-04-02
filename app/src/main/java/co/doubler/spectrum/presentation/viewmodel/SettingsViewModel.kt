package co.doubler.spectrum.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.doubler.spectrum.data.prefs.DemoPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val demoPreferences: DemoPreferences
) : ViewModel() {

    val isDemoEnabled: StateFlow<Boolean> = demoPreferences.isDemoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleDemoMode(enabled: Boolean) {
        viewModelScope.launch { demoPreferences.setDemoEnabled(enabled) }
    }
}
