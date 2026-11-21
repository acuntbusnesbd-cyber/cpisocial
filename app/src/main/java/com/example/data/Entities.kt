package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String = "",
    val passwordHash: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val coverUrl: String = "",
    val nodeId: String = "",
    val bluetoothAddress: String = "",
    val publicKeyB64: String = "",
    val isLocalAccount: Boolean = false,
    val isPaired: Boolean = false,
    val isVerified: Boolean = false,
    val isPrivate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "posts")
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val caption: String,
    val mediaUrls: List<String> = emptyList(),
    val type: String = "image", // "image", "text", "video"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class Comment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val postId: Int,
    val userId: Int,
    val parentCommentId: Int? = null, // Threaded comment replies
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "likes")
data class Like(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val postId: Int? = null,
    val commentId: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "follows")
data class Follow(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val followerId: Int,
    val followingId: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientId: Int,
    val actorId: Int,
    val actorName: String,
    val actorAvatarUrl: String = "",
    val type: String, // "like", "comment", "follow", "message", "call"
    val referenceId: Int, // id of the post, comment, message or call
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val participantIds: List<Int>,
    val lastMessageAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conversationId: Int,
    val senderId: Int,
    val content: String,
    val mediaUrl: String? = null,
    val messageType: String = "text", // "text", "image", "voice_note", "call_summary"
    val localFilePath: String? = null,
    val audioDurationSec: Int = 0,
    val waveformJson: String = "",
    val hopCount: Int = 1,
    val meshStatus: String = "delivered", // "encrypting", "relaying", "delivered"
    val packetId: String = "",
    val relayRoute: String = "",
    val isEncrypted: Boolean = true,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "calls")
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val callerId: Int,
    val calleeId: Int,
    val callerName: String,
    val calleeName: String,
    val callerAvatarUrl: String = "",
    val calleeAvatarUrl: String = "",
    val type: String = "voice", // "voice", "ptt", "video"
    val status: String = "completed", // "missed", "completed", "declined", "ringing_outgoing", "ringing_incoming"
    val hopCount: Int = 1,
    val relayRoute: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0
)

@Entity(tableName = "user_notes")
data class UserNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val content: String,
    val musicTrack: String? = null,
    val audioUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reels")
data class Reel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val caption: String,
    val videoUrl: String,
    val audioTrackName: String = "Original Audio - Echo Social 🎵",
    val audioUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reel_likes")
data class ReelLike(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val reelId: Int,
    val createdAt: Long = System.currentTimeMillis()
)


