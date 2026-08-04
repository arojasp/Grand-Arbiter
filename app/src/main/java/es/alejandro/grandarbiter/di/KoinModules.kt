package es.alejandro.grandarbiter.di

import androidx.room.Room
import es.alejandro.grandarbiter.data.connectivity.NetworkConnectivityObserver
import es.alejandro.grandarbiter.data.local.AppDatabase
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.remote.AcademyRuinsService
import es.alejandro.grandarbiter.data.remote.ScryfallService
import es.alejandro.grandarbiter.data.repository.CRRepositoryImpl
import es.alejandro.grandarbiter.data.repository.CardRepositoryImpl
import es.alejandro.grandarbiter.data.timer.TimerManagerImpl
import es.alejandro.grandarbiter.domain.connectivity.ConnectivityObserver
import es.alejandro.grandarbiter.domain.repository.CRRepository
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import es.alejandro.grandarbiter.ui.comprehensiverules.ComprehensiveRulesViewModel
import es.alejandro.grandarbiter.ui.home.HomeViewModel
import es.alejandro.grandarbiter.ui.main.MainViewModel
import es.alejandro.grandarbiter.ui.setup.SetUpViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val networkModule = module {
    single {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    single {
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }

    single<AcademyRuinsService> {
        Retrofit.Builder()
            .baseUrl("https://api.academyruins.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AcademyRuinsService::class.java)
    }

    single<ScryfallService> {
        Retrofit.Builder()
            .baseUrl("https://api.scryfall.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScryfallService::class.java)
    }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "judge_companion_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    single { get<AppDatabase>().crDao() }
    single { get<AppDatabase>().cardDao() }
    
    single { SettingsDataStore(androidContext()) }
}

val repositoryModule = module {
    single<CRRepository> { CRRepositoryImpl(get(), get(), get()) }
    single<CardRepository> { CardRepositoryImpl(get(), get()) }
}

val appModule = module {
    single<ConnectivityObserver> { NetworkConnectivityObserver(androidContext()) }
    single<TimerManager> { TimerManagerImpl(androidContext()) }
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::SetUpViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ComprehensiveRulesViewModel)
}
