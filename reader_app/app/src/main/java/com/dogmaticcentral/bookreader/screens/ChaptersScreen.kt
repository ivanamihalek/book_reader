package  com.dogmaticcentral.bookreader.screens

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dogmaticcentral.bookreader.LocalBookRepository
import com.dogmaticcentral.bookreader.components.LargeButton
import com.dogmaticcentral.bookreader.components.ScreenLayout


@Composable
fun ChaptersScreen(navController: NavHostController, bookId: Int) {
    val repository = LocalBookRepository.current
    val bookChaptersState = repository.getChaptersByBookId(bookId).collectAsState(initial = emptyList())
    val mostRecentChapterState = repository.getMostRecentlyPlayedChapter(bookId).collectAsState(initial = null)
    ScreenLayout(
        navController = navController,
        showBackButton = true,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Use the items extension function for LazyColumn
            items(bookChaptersState.value) { chapter ->
                LargeButton(
                    text = chapter.title,
                    onClick = { navController.navigate("player/${bookId}/${chapter.id}") },
                    modifier = Modifier.fillMaxWidth()
                        .then(
                            if (chapter.id == mostRecentChapterState.value?.id) {
                                Modifier.border(
                                    width = 4.dp,
                                    color = Color.Red
                                )
                            } else if (chapter.finishedPlaying) {
                                Modifier.border(
                                    width = 4.dp,
                                    color = Color.Blue
                                )
                            } else {
                                Modifier
                            }
                        )
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
