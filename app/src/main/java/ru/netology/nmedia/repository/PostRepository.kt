package ru.netology.nmedia.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.dto.Media
import ru.netology.nmedia.dto.MediaUpload
import ru.netology.nmedia.dto.Post

interface PostRepository {
    fun getData(): Flow<PagingData<FeedItem>>
    suspend fun save(post: Post, upload: MediaUpload?)
    suspend fun removeById(id: Long)
    suspend fun likeById(id: Long)
    suspend fun dislikeById(id: Long)
    suspend fun upload(upload: MediaUpload): Media
    suspend fun getPostById(id: Long): Post?
    suspend fun updateLikeState(id: Long, likedByMe: Boolean)
    suspend fun refreshData()
}

