package com.tenkultra.tv.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.datastore.SettingsDataStore
import com.tenkultra.tv.domain.model.AppLayout
import com.tenkultra.tv.domain.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settings: SettingsDataStore
) : ViewModel() {
    val theme = settings.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppTheme.DEFAULT
    )

    val layout = settings.layoutFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLayout.DEFAULT
    )
}
