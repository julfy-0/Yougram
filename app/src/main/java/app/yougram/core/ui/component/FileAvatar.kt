package app.yougram.core.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.feature.chat.data.FileState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Аватарка по id файла TDLib: путь подхватывается из состояния файла, когда загрузка завершится. */
@Composable
fun FileAvatar(
    title: String,
    fileId: Int?,
    fileState: (Int) -> Flow<FileState>,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
) {
    val pathFlow = remember(fileId) {
        if (fileId == null) flowOf<String?>(null) else fileState(fileId).map { it.path }
    }
    val path by pathFlow.collectAsState(initial = null)
    Avatar(title = title, path = path, size = size, shape = shape)
}