package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class EchoRepository(private val db: AppDatabase) {

    private val userDao = db.userDao()
    private val postDao = db.postDao()
    private val commentDao = db.commentDao()
    private val likeDao = db.likeDao()
    private val followDao = db.followDao()
    private val notificationDao = db.notificationDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val callDao = db.callDao()
    private val noteDao = db.noteDao()
    private val reelDao = db.reelDao()

    // Users
    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsersFlow()
    fun getLocalAccounts(): Flow<List<User>> = userDao.getLocalAccountsFlow()
    fun searchUsers(query: String): Flow<List<User>> = userDao.searchUsers(query)
    fun getUserByIdFlow(userId: Int): Flow<User?> = userDao.getUserByIdFlow(userId)
    suspend fun getUserById(userId: Int): User? = userDao.getUserById(userId)
    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
    suspend fun getUserByEmail(email: String): User? = userDao.getUserByEmail(email)
    suspend fun getUserByNodeId(nodeId: String): User? = userDao.getUserByNodeId(nodeId)
    suspend fun getUserByBluetoothAddress(address: String): User? = userDao.getUserByBluetoothAddress(address)
    suspend fun insertUser(user: User): Int = userDao.insertUser(user).toInt()
    suspend fun updateUser(user: User) = userDao.updateUser(user)
    suspend fun deleteUser(userId: Int) = userDao.deleteUserById(userId)

    suspend fun purgeLegacyDemoAccounts() {
        userDao.deleteUsersByUsernames(
            listOf("sky_wanderer", "pixel_artisan", "tech_scribe", "nature_lens")
        )
    }

    suspend fun upsertBluetoothPeerUser(
        nodeId: String,
        username: String,
        bio: String,
        publicKeyB64: String,
        bluetoothAddress: String,
        isPaired: Boolean
    ): User {
        val existing = (if (nodeId.isNotBlank()) userDao.getUserByNodeId(nodeId) else null)
            ?: (if (bluetoothAddress.isNotBlank()) userDao.getUserByBluetoothAddress(bluetoothAddress) else null)
            ?: userDao.getUserByUsername(username)?.takeIf { !it.isLocalAccount }

        return if (existing != null) {
            val updated = existing.copy(
                username = username.ifBlank { existing.username },
                bio = bio.ifBlank { existing.bio },
                nodeId = nodeId.ifBlank { existing.nodeId },
                bluetoothAddress = bluetoothAddress.ifBlank { existing.bluetoothAddress },
                publicKeyB64 = publicKeyB64.ifBlank { existing.publicKeyB64 },
                isPaired = isPaired || existing.isPaired
            )
            userDao.updateUser(updated)
            updated
        } else {
            val peerUser = User(
                username = username,
                bio = bio.ifBlank { "Bluetooth Mesh Peer ($nodeId)" },
                nodeId = nodeId,
                bluetoothAddress = bluetoothAddress,
                publicKeyB64 = publicKeyB64,
                isLocalAccount = false,
                isPaired = isPaired
            )
            val id = userDao.insertUser(peerUser).toInt()
            peerUser.copy(id = id)
        }
    }

    fun getFollowing(userId: Int): Flow<List<User>> = userDao.getFollowing(userId)
    fun getFollowers(userId: Int): Flow<List<User>> = userDao.getFollowers(userId)

    // Posts
    fun getFeedPosts(userId: Int): Flow<List<Post>> = postDao.getFeedPosts(userId)
    fun getAllPosts(): Flow<List<Post>> = postDao.getAllPosts()
    fun getPostsByUserId(userId: Int): Flow<List<Post>> = postDao.getPostsByUserId(userId)
    fun searchPosts(query: String): Flow<List<Post>> = postDao.searchPosts(query)
    suspend fun getPostById(postId: Int): Post? = postDao.getPostById(postId)
    suspend fun insertPost(post: Post): Int = postDao.insertPost(post).toInt()
    suspend fun updatePost(post: Post) = postDao.updatePost(post)
    suspend fun deletePost(post: Post) = postDao.deletePost(post)

    // Comments
    fun getCommentsForPost(postId: Int): Flow<List<Comment>> = commentDao.getCommentsForPost(postId)
    fun getCommentCountForPost(postId: Int): Flow<Int> = commentDao.getCommentCountForPost(postId)
    suspend fun insertComment(comment: Comment): Int {
        val id = commentDao.insertComment(comment).toInt()
        
        // Trigger notification to post owner if it's not the comment author
        val post = postDao.getPostById(comment.postId)
        if (post != null && post.userId != comment.userId) {
            val author = userDao.getUserById(comment.userId)
            if (author != null) {
                insertNotification(
                    Notification(
                        recipientId = post.userId,
                        actorId = comment.userId,
                        actorName = author.username,
                        actorAvatarUrl = author.avatarUrl,
                        type = "comment",
                        referenceId = post.id,
                        isRead = false
                    )
                )
            }
        }
        return id
    }
    suspend fun deleteComment(comment: Comment) = commentDao.deleteComment(comment)

    // Likes
    fun isPostLikedByUser(userId: Int, postId: Int): Flow<Boolean> = likeDao.isPostLikedByUser(userId, postId)
    fun isCommentLikedByUser(userId: Int, commentId: Int): Flow<Boolean> = likeDao.isCommentLikedByUser(userId, commentId)
    fun getLikeCountForPost(postId: Int): Flow<Int> = likeDao.getLikeCountForPost(postId)
    fun getLikeCountForComment(commentId: Int): Flow<Int> = likeDao.getLikeCountForComment(commentId)

    suspend fun toggleLikePost(userId: Int, postId: Int) {
        val liked = likeDao.isPostLikedByUser(userId, postId).first()
        if (liked) {
            likeDao.deleteLikeForPost(userId, postId)
        } else {
            likeDao.insertLike(Like(userId = userId, postId = postId))
            // Trigger notification
            val post = postDao.getPostById(postId)
            if (post != null && post.userId != userId) {
                val liker = userDao.getUserById(userId)
                if (liker != null) {
                    insertNotification(
                        Notification(
                            recipientId = post.userId,
                            actorId = userId,
                            actorName = liker.username,
                            actorAvatarUrl = liker.avatarUrl,
                            type = "like",
                            referenceId = postId,
                            isRead = false
                        )
                    )
                }
            }
        }
    }

    suspend fun toggleLikeComment(userId: Int, commentId: Int) {
        val liked = likeDao.isCommentLikedByUser(userId, commentId).first()
        if (liked) {
            likeDao.deleteLikeForComment(userId, commentId)
        } else {
            likeDao.insertLike(Like(userId = userId, commentId = commentId))
        }
    }

    // Follows
    fun isFollowing(followerId: Int, followingId: Int): Flow<Boolean> = followDao.isFollowing(followerId, followingId)
    fun getFollowingCount(userId: Int): Flow<Int> = followDao.getFollowingCount(userId)
    fun getFollowerCount(userId: Int): Flow<Int> = followDao.getFollowerCount(userId)

    suspend fun toggleFollow(followerId: Int, followingId: Int) {
        val following = followDao.isFollowing(followerId, followingId).first()
        if (following) {
            followDao.deleteFollow(followerId, followingId)
        } else {
            followDao.insertFollow(Follow(followerId = followerId, followingId = followingId))
            // Trigger notification
            val follower = userDao.getUserById(followerId)
            if (follower != null) {
                insertNotification(
                    Notification(
                        recipientId = followingId,
                        actorId = followerId,
                        actorName = follower.username,
                        actorAvatarUrl = follower.avatarUrl,
                        type = "follow",
                        referenceId = followerId,
                        isRead = false
                    )
                )
            }
        }
    }

    // Notifications
    fun getNotifications(userId: Int): Flow<List<Notification>> = notificationDao.getNotificationsForUser(userId)
    fun getUnreadNotificationCount(userId: Int): Flow<Int> = notificationDao.getUnreadCount(userId)
    suspend fun insertNotification(notification: Notification) = notificationDao.insertNotification(notification)
    suspend fun markAllNotificationsAsRead(userId: Int) = notificationDao.markAllAsRead(userId)
    suspend fun markNotificationAsRead(id: Int) = notificationDao.markAsRead(id)

    // Conversations & Chat Messages
    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversationsFlow()
    fun getMessages(conversationId: Int): Flow<List<Message>> = messageDao.getMessagesForConversation(conversationId)
    fun getLastMessage(conversationId: Int): Flow<Message?> = messageDao.getLastMessageForConversation(conversationId)
    suspend fun getConversationById(id: Int): Conversation? = conversationDao.getConversationById(id)

    suspend fun createConversation(participantIds: List<Int>): Int {
        val existing = conversationDao.getAllConversationsFlow().first().find {
            it.participantIds.sorted() == participantIds.sorted()
        }
        if (existing != null) return existing.id
        return conversationDao.insertConversation(Conversation(participantIds = participantIds)).toInt()
    }

    suspend fun sendMessage(message: Message): Int {
        val id = messageDao.insertMessage(message).toInt()
        val conv = conversationDao.getConversationById(message.conversationId)
        if (conv != null) {
            conversationDao.updateConversation(conv.copy(lastMessageAt = System.currentTimeMillis()))
            
            // Send in-app message notification to other participants
            val otherParticipants = conv.participantIds.filter { it != message.senderId }
            val sender = userDao.getUserById(message.senderId)
            if (sender != null) {
                otherParticipants.forEach { recipientId ->
                    insertNotification(
                        Notification(
                            recipientId = recipientId,
                            actorId = message.senderId,
                            actorName = sender.username,
                            actorAvatarUrl = sender.avatarUrl,
                            type = "message",
                            referenceId = message.conversationId,
                            isRead = false
                        )
                    )
                }
            }
        }
        return id
    }

    suspend fun markMessagesAsRead(conversationId: Int, userId: Int) = messageDao.markMessagesAsRead(conversationId, userId)

    suspend fun updateMessageDelivery(packetId: String, status: String, hopCount: Int, relayRoute: String) {
        if (packetId.isNotBlank()) {
            messageDao.updateMessageDeliveryByPacketId(packetId, status, hopCount, relayRoute)
        }
    }

    // Call logs
    fun getCallHistory(): Flow<List<CallLog>> = callDao.getCallHistory()
    suspend fun insertCallLog(callLog: CallLog) = callDao.insertCallLog(callLog)

    // User Notes
    fun getAllNotes(): Flow<List<UserNote>> = noteDao.getAllNotes()
    fun getNoteForUser(userId: Int): Flow<UserNote?> = noteDao.getNoteForUser(userId)
    suspend fun saveNote(userId: Int, content: String, musicTrack: String? = null, audioUrl: String? = null) {
        noteDao.deleteNotesForUser(userId)
        noteDao.insertNote(UserNote(userId = userId, content = content, musicTrack = musicTrack, audioUrl = audioUrl))
    }
    suspend fun deleteNote(userId: Int) {
        noteDao.deleteNotesForUser(userId)
    }

    // Reels System
    fun getAllReels(): Flow<List<Reel>> = reelDao.getAllReels()
    suspend fun insertReel(reel: Reel): Int = reelDao.insertReel(reel).toInt()
    suspend fun deleteReel(reelId: Int) = reelDao.deleteReel(reelId)
    fun getReelLikesCount(reelId: Int): Flow<Int> = reelDao.getReelLikesCount(reelId)
    suspend fun toggleLikeReel(userId: Int, reelId: Int) {
        val likes = reelDao.getReelLikes(reelId).first()
        val isLiked = likes.any { it.userId == userId }
        if (isLiked) {
            reelDao.unlikeReel(userId, reelId)
        } else {
            reelDao.likeReel(ReelLike(userId = userId, reelId = reelId))
        }
    }
    fun isReelLikedByUser(userId: Int, reelId: Int): Flow<Boolean> = reelDao.getReelLikes(reelId).map { likes ->
        likes.any { it.userId == userId }
    }


}


