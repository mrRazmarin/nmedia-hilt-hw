package ru.netology.nmedia.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import okio.IOException
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.PostRemoteKeyEntity
import ru.netology.nmedia.error.ApiError

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val apiService: ApiService,
    private val postDao: PostDao,
    private val postRemoteKeyDao: PostRemoteKeyDao,
    private val appDb: AppDb,
) : RemoteMediator<Int, PostEntity>() {

    override suspend fun initialize(): InitializeAction =
        if (postDao.isEmpty()) InitializeAction.LAUNCH_INITIAL_REFRESH
        else InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>,
    ): MediatorResult {
        try {
            val pageSize = state.config.pageSize

            val response = when (loadType) {
                LoadType.REFRESH -> apiService.getLatest(pageSize)

                LoadType.PREPEND -> {
                    val maxId = postRemoteKeyDao.max()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)
                    apiService.getAfter(maxId, pageSize)
                }

                LoadType.APPEND -> {
                    val minId = postRemoteKeyDao.min()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)
                    apiService.getBefore(minId, pageSize)
                }
            }

            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
            val posts = response.body() ?: emptyList()
            val endOfPagination = posts.isEmpty()

            appDb.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    postDao.clear()
                    postRemoteKeyDao.clear()
                }

                if (posts.isNotEmpty()) {
                    val newestId = posts.first().id
                    val oldestId = posts.last().id

                    val keys = buildList {
                        if (loadType == LoadType.REFRESH || loadType == LoadType.PREPEND) {
                            add(PostRemoteKeyEntity(
                                PostRemoteKeyEntity.KeyType.AFTER, newestId))
                        }
                        if (loadType == LoadType.REFRESH || loadType == LoadType.APPEND) {
                            add(PostRemoteKeyEntity(
                                PostRemoteKeyEntity.KeyType.BEFORE, oldestId))
                        }
                    }
                    postRemoteKeyDao.insert(keys)
                    postDao.insert(posts.map(PostEntity::fromDto))
                }
            }

            return MediatorResult.Success(endOfPagination)
        } catch (e: IOException) {
            return MediatorResult.Error(e)
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}