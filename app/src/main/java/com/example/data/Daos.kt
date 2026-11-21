package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserByIdFlow(userId: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getUserByNodeId(nodeId: String): User?

    @Query("SELECT * FROM users WHERE bluetoothAddress = :address LIMIT 1")
    suspend fun getUserByBluetoothAddress(address: String): User?

    @Query("SELECT * FROM users WHERE isLocalAccount = 1 ORDER BY createdAt DESC")
    fun getLocalAccountsFlow(): Flow<List<User>>

    @Query("DELETE FROM users WHERE username IN (:usernames)")
    suspend fun deleteUsersByUsernames(usernames: List<String>)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Int)

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%'")
    fun searchUsers(query: String): Flow<List<User>>

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id IN (SELECT followingId FROM follows WHERE followerId = :userId)")
    fun getFollowing(userId: Int): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id IN (SELECT followerId FROM follows WHERE followingId = :userId)")
    fun getFollowers(userId: Int): Flow<List<User>>
}

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: Post): Long

    @Update
    suspend fun updatePost(post: Post)

    @Delete
    suspend fun deletePost(post: Post)

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Int): Post?

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getPostsByUserId(userId: Int): Flow<List<Post>>

    // Feed of followed users' posts + own posts
    @Query("SELECT * FROM posts WHERE userId = :userId OR userId IN (SELECT followingId FROM follows WHERE followerId = :userId) ORDER BY createdAt DESC")
    fun getFeedPosts(userId: Int): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE caption LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchPosts(query: String): Flow<List<Post>>
}

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment): Long

    @Delete
    suspend fun deleteComment(comment: Comment)

    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsForPost(postId: Int): Flow<List<Comment>>

    @Query("SELECT COUNT(*) FROM comments WHERE postId = :postId")
    fun getCommentCountForPost(postId: Int): Flow<Int>
}

@Dao
interface LikeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: Like): Long

    @Query("DELETE FROM likes WHERE userId = :userId AND postId = :postId")
    suspend fun deleteLikeForPost(userId: Int, postId: Int)

    @Query("DELETE FROM likes WHERE userId = :userId AND commentId = :commentId")
    suspend fun deleteLikeForComment(userId: Int, commentId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE userId = :userId AND postId = :postId)")
    fun isPostLikedByUser(userId: Int, postId: Int): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE userId = :userId AND commentId = :commentId)")
    fun isCommentLikedByUser(userId: Int, commentId: Int): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM likes WHERE postId = :postId")
    fun getLikeCountForPost(postId: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM likes WHERE commentId = :commentId")
    fun getLikeCountForComment(commentId: Int): Flow<Int>
}

@Dao
interface FollowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: Follow): Long

    @Query("DELETE FROM follows WHERE followerId = :followerId AND followingId = :followingId")
    suspend fun deleteFollow(followerId: Int, followingId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM follows WHERE followerId = :followerId AND followingId = :followingId)")
    fun isFollowing(followerId: Int, followingId: Int): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM follows WHERE followerId = :userId")
    fun getFollowingCount(userId: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM follows WHERE followingId = :userId")
    fun getFollowerCount(userId: Int): Flow<Int>
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE recipientId = :userId")
    suspend fun markAllAsRead(userId: Int)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: Int)

    @Query("SELECT * FROM notifications WHERE recipientId = :userId ORDER BY createdAt DESC")
    fun getNotificationsForUser(userId: Int): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE recipientId = :userId AND isRead = 0")
    fun getUnreadCount(userId: Int): Flow<Int>
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Int): Conversation?

    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC")
    fun getAllConversationsFlow(): Flow<List<Conversation>>
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT 1")
    fun getLastMessageForConversation(conversationId: Int): Flow<Message?>

    @Query("UPDATE messages SET isRead = 1 WHERE conversationId = :conversationId AND senderId != :userId")
    suspend fun markMessagesAsRead(conversationId: Int, userId: Int)

    @Query("UPDATE messages SET meshStatus = :status, hopCount = :hopCount, relayRoute = :relayRoute WHERE packetId = :packetId")
    suspend fun updateMessageDeliveryByPacketId(packetId: String, status: String, hopCount: Int, relayRoute: String)
}

@Dao
interface CallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLog): Long

    @Query("SELECT * FROM calls ORDER BY startedAt DESC")
    fun getCallHistory(): Flow<List<CallLog>>
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: UserNote): Long

    @Query("DELETE FROM user_notes WHERE userId = :userId")
    suspend fun deleteNotesForUser(userId: Int)

    @Query("SELECT * FROM user_notes WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    fun getNoteForUser(userId: Int): Flow<UserNote?>

    @Query("SELECT * FROM user_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<UserNote>>
}

@Dao
interface ReelDao {
    @Query("SELECT * FROM reels ORDER BY createdAt DESC")
    fun getAllReels(): Flow<List<Reel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReel(reel: Reel): Long

    @Query("DELETE FROM reels WHERE id = :reelId")
    suspend fun deleteReel(reelId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeReel(reelLike: ReelLike): Long

    @Query("DELETE FROM reel_likes WHERE userId = :userId AND reelId = :reelId")
    suspend fun unlikeReel(userId: Int, reelId: Int)

    @Query("SELECT * FROM reel_likes WHERE reelId = :reelId")
    fun getReelLikes(reelId: Int): Flow<List<ReelLike>>

    @Query("SELECT COUNT(*) FROM reel_likes WHERE reelId = :reelId")
    fun getReelLikesCount(reelId: Int): Flow<Int>
}

