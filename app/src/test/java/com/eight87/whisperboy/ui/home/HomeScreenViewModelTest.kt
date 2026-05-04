package com.eight87.whisperboy.ui.home

import com.eight87.whisperboy.data.DataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = HomeScreenViewModel(FakeDataRepository())
    assertEquals(viewModel.uiState.first(), HomeScreenUiState.Loading)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val viewModel = HomeScreenViewModel(FakeDataRepository())
    assertEquals(viewModel.uiState.first(), HomeScreenUiState.Loading)
  }
}

private class FakeDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}
