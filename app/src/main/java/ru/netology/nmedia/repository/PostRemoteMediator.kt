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
    private val appDb: AppDb
): RemoteMediator<Int, PostEntity>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        return when (loadType) {
            LoadType.REFRESH -> {
                try {
                    val result = apiService.getLatest(state.config.pageSize)
                    
                    if (!result.isSuccessful){
                        throw ApiError(result.code(), result.message())
                    }

                    val data = result.body() ?: throw ApiError(
                        result.code(),
                        result.message()
                    )

                    val isDbEmpty = appDb.postDao().isEmpty()

                    if (isDbEmpty) {
                        appDb.withTransaction {
                            postRemoteKeyDao.insert(
                                listOf(
                                    PostRemoteKeyEntity(
                                        PostRemoteKeyEntity.KeyType.AFTER,
                                        data.lastOrNull()?.id ?: 0L
                                    ),
                                    PostRemoteKeyEntity(
                                        PostRemoteKeyEntity.KeyType.BEFORE,
                                        data.first().id
                                    )
                                )
                            )
                            postDao.insert(data.map { PostEntity.fromDto(it) })
                        }
                    }
                    else {
                        val currentMinId = postRemoteKeyDao.min() ?: 0L

                        if (data.isNotEmpty() && data.first().id < currentMinId) {
                            appDb.withTransaction {
                                postRemoteKeyDao.insert(
                                    PostRemoteKeyEntity(
                                        PostRemoteKeyEntity.KeyType.BEFORE,
                                        data.first().id
                                    )
                                )
                                postDao.insert(data.map { PostEntity.fromDto(it) })
                            }
                        }
                        else if (data.isEmpty()) {
                            appDb.withTransaction {
                                postRemoteKeyDao.insert(
                                    listOf(
                                        PostRemoteKeyEntity(
                                            PostRemoteKeyEntity.KeyType.AFTER,
                                            currentMinId
                                        ),
                                        PostRemoteKeyEntity(
                                            PostRemoteKeyEntity.KeyType.BEFORE,
                                            currentMinId
                                        )
                                    )
                                )
                            }
                        }
                    }

                    MediatorResult.Success(endOfPaginationReached = data.isEmpty())
                } catch (e: IOException) {
                    MediatorResult.Error(e)
                } catch (e: Exception) {
                    MediatorResult.Error(e)
                }
            }
            
            LoadType.PREPEND -> {
                MediatorResult.Success(endOfPaginationReached = true)
            }
            
            LoadType.APPEND -> {
                try {
                    val isDbEmpty = appDb.postDao().isEmpty()

                    val beforeKey = if (!isDbEmpty) {
                        postRemoteKeyDao.min() ?: 0L
                    } else {
                        0L
                    }
                    
                    val result = apiService.getBefore(
                        beforeKey,
                        state.config.pageSize
                    )
                    
                    if (!result.isSuccessful){
                        throw ApiError(result.code(), result.message())
                    }

                    val data = result.body() ?: throw ApiError(
                        result.code(),
                        result.message()
                    )

                    if (data.isNotEmpty()) {
                        appDb.withTransaction {
                            postRemoteKeyDao.insert(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.BEFORE,
                                    data.first().id
                                )
                            )
                            postDao.insert(data.map { PostEntity.fromDto(it) })
                        }
                    } else {
                        // Если постов нет, сохраняем текущий BEFORE ключ
                        appDb.withTransaction {
                            val currentBeforeKey = postRemoteKeyDao.min() ?: beforeKey
                            postRemoteKeyDao.insert(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.BEFORE,
                                    currentBeforeKey
                                )
                            )
                        }
                    }

                    MediatorResult.Success(endOfPaginationReached = data.isEmpty())
                } catch (e: IOException) {
                    MediatorResult.Error(e)
                } catch (e: Exception) {
                    MediatorResult.Error(e)
                }
            }
        }
    }
}