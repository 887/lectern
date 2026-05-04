package com.eight87.whisperboy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eight87.whisperboy.data.DataRepository
import com.eight87.whisperboy.ui.home.HomeScreenUiState.Success
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeScreenViewModel(dataRepository: DataRepository) : ViewModel() {
  val uiState: StateFlow<HomeScreenUiState> =
    dataRepository.data
      .map<List<String>, HomeScreenUiState>(::Success)
      .catch { emit(HomeScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeScreenUiState.Loading)
}

sealed interface HomeScreenUiState {
  object Loading : HomeScreenUiState

  data class Error(val throwable: Throwable) : HomeScreenUiState

  data class Success(val data: List<String>) : HomeScreenUiState
}
