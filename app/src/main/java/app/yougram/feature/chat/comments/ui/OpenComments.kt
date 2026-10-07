package app.yougram.feature.chat.comments.ui

import androidx.navigation.NavHostController

fun NavHostController.openComments(chatId: Long, postId: Long) {
    navigate("comments/$chatId/$postId")
}
