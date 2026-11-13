package com.kokouliss.listanything.core.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.kokouliss.listanything.core.data.firebase.FirebaseAuthRepository
import com.kokouliss.listanything.core.data.firebase.FirestoreDrawingRepository
import com.kokouliss.listanything.core.data.firebase.FirestoreItemRepository
import com.kokouliss.listanything.core.data.firebase.FirestoreListRepository
import com.kokouliss.listanything.core.data.firebase.FirestoreSpaceRepository
import com.kokouliss.listanything.core.domain.repository.AuthRepository
import com.kokouliss.listanything.core.domain.repository.DrawingRepository
import com.kokouliss.listanything.core.domain.repository.ItemRepository
import com.kokouliss.listanything.core.domain.repository.ListRepository
import com.kokouliss.listanything.core.domain.repository.SpaceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSpaceRepository(impl: FirestoreSpaceRepository): SpaceRepository

    @Binds
    @Singleton
    abstract fun bindListRepository(impl: FirestoreListRepository): ListRepository

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: FirestoreItemRepository): ItemRepository

    @Binds
    @Singleton
    abstract fun bindDrawingRepository(impl: FirestoreDrawingRepository): DrawingRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides
        @Singleton
        fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
