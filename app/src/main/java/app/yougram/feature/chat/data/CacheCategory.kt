package app.yougram.feature.chat.data

import dev.g000sha256.tdl.dto.FileType
import dev.g000sha256.tdl.dto.FileTypeAnimation
import dev.g000sha256.tdl.dto.FileTypeAudio
import dev.g000sha256.tdl.dto.FileTypeDocument
import dev.g000sha256.tdl.dto.FileTypePhoto
import dev.g000sha256.tdl.dto.FileTypeVideo
import dev.g000sha256.tdl.dto.FileTypeVideoNote

/**
 * Категории кэша, которые пользователь может очистить по отдельности.
 * [title] — русский ключ строки интерфейса (переводится через tr).
 */
enum class CacheCategory(val title: String) {
    Photos("Фото"),
    Videos("Видео"),
    Documents("Документы"),
    Music("Музыка");

    /** Типы файлов TDLib, которые входят в категорию. */
    fun fileTypes(): List<FileType> = when (this) {
        Photos -> listOf(FileTypePhoto())
        Videos -> listOf(FileTypeVideo(), FileTypeVideoNote(), FileTypeAnimation())
        Documents -> listOf(FileTypeDocument())
        Music -> listOf(FileTypeAudio())
    }

    /** true, если [type] относится к этой категории. */
    fun contains(type: FileType): Boolean = when (this) {
        Photos -> type is FileTypePhoto
        Videos -> type is FileTypeVideo || type is FileTypeVideoNote || type is FileTypeAnimation
        Documents -> type is FileTypeDocument
        Music -> type is FileTypeAudio
    }

    companion object {
        /** Размер кэша по категориям. Типы вне списка (голосовые, стикеры и т.п.) сюда не попадают. */
        fun sizes(byType: List<Pair<FileType, Long>>): Map<CacheCategory, Long> =
            entries.associateWith { c -> byType.filter { (t, _) -> c.contains(t) }.sumOf { it.second } }
    }
}
