package com.eight87.whisperboy.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.DefaultDataRepository
import com.eight87.whisperboy.theme.WhisperboyTheme

@Composable
fun HomeScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HomeScreenViewModel = viewModel { HomeScreenViewModel(DefaultDataRepository()) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (state) {
    HomeScreenUiState.Loading -> {
      // Blank
    }
    is HomeScreenUiState.Success -> {
      HomeScreen(data = (state as HomeScreenUiState.Success).data, modifier = modifier)
    }
    is HomeScreenUiState.Error -> {
      Text(stringResource(R.string.home_error_loading, (state as HomeScreenUiState.Error).throwable.message ?: ""))
    }
  }
}

@Composable
internal fun HomeScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier) { data.forEach { Greeting(it) } }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = stringResource(R.string.home_greeting, name), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  WhisperboyTheme { HomeScreen(listOf("Android")) }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun HomeScreenPortraitPreview() {
  WhisperboyTheme { HomeScreen(listOf("Android")) }
}
