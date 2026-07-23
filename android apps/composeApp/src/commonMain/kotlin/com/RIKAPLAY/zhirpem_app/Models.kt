package com.RIKAPLAY.zhirpem_app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import dev.gitlive.firebase.firestore.Timestamp

@Serializable
enum class MediaType {
    IMAGE, GIF, VIDEO, NONE
}

@Serializable
enum class ChatInputMode {
    AUDIO, VIDEO
}

@Serializable
data class PollData(
    val question: String = "",
    val options: List<String> = emptyList(),
    val anonymous: Boolean = true,
    val multipleChoice: Boolean = false,
    val votes: Map<String, List<String>> = emptyMap() // Map of option index to list of user IDs
)

@Immutable
@Serializable
data class Post(
    val id: String = "",
    val author: String = "",
    val date: String = "",
    val handle: String = "",
    @SerialName("isMedia")
    val isMedia: Boolean = false,
    val imageUrl: String? = null,
    val mediaUrl: String = "",
    val mediaType: MediaType = MediaType.NONE,
    val authorAvatarUrl: String? = null,
    val blueBadge: Boolean = false,
    val yellowBadge: Boolean = false,
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val text: String = "",
    val time: String = "",
    val views: Int = 0,
    val likedBy: List<String> = emptyList(),
    val bookmarkedBy: List<String> = emptyList(),
    val repostedBy: List<String> = emptyList(),
    val timestamp: Timestamp? = null,
    val isAuthorBanned: Boolean = false,
    val authorNameColor: String? = null,
    val communityId: String? = null,
    val poll: PollData? = null,
    val authorStatus: String? = null
)

@Serializable
data class Community(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val creatorId: String = "",
    val bannerColor: String = "#FF4CAF50",
    val avatarUrl: String = "",
    val members: List<String> = emptyList()
)

@Immutable
@Serializable
data class User(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String = "#808080",
    val status: String = "",
    val bio: String = "",
    val joinedCommunityId: String? = null,
    val joinedCommunityAvatar: String? = null,
    val isOnline: Boolean = false,
    val notificationSetting: String = "all"
)

@Immutable
@Serializable
data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L
)

@Immutable
@Serializable
data class Message(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: MediaType = MediaType.NONE,
    val timestamp: Long = 0L,
    val forwardedPostId: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val isRead: Boolean = false
)

@Immutable
@Serializable
data class Comment(
    val author: String = "",
    val authorUsername: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null,
    val postId: String = "",
    val id: String = "",
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val replyToCommentId: String? = null,
    val replyToUsername: String? = null
)

@Serializable
data class PostAnalytics(
    val postId: String = "",
    val titleOrText: String = "",
    val views: Int = 0,
    val likes: Int = 0,
    val reposts: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long = 0L
)

@Serializable
data class CommentAnalytics(
    val commentId: String = "",
    val postId: String = "",
    val authorName: String = "",
    val commentText: String = "",
    val likes: Int = 0
)
