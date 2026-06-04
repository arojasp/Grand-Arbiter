package es.alejandro.grandarbiter.di

import androidx.room.Room
import es.alejandro.grandarbiter.data.document.DocumentDownloader
import es.alejandro.grandarbiter.data.document.DocumentParser
import es.alejandro.grandarbiter.data.document.DocumentUpdateChecker
import es.alejandro.grandarbiter.data.local.AppDatabase
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.remote.AcademyRuinsService
import es.alejandro.grandarbiter.data.remote.ScryfallService
import es.alejandro.grandarbiter.data.repository.CardRepositoryImpl
import es.alejandro.grandarbiter.data.repository.DocumentRepositoryImpl
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.DocumentRepository
import es.alejandro.grandarbiter.ui.main.MainViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
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
        ).build()
    }

    single { get<AppDatabase>().documentDao() }
    single { get<AppDatabase>().cardDao() }
    single { get<AppDatabase>().quickReferenceDao() }
    
    single { SettingsDataStore(androidContext()) }
}

val repositoryModule = module {
    single<DocumentRepository> { DocumentRepositoryImpl(get(), get(), get(), get(), get()) }
    single<CardRepository> { CardRepositoryImpl(get(), get()) }
}

val appModule = module {
    single { DocumentUpdateChecker(get()) }
    single { DocumentDownloader(get()) }
    single { DocumentParser(androidContext()) }
}

val viewModelModule = module {
    viewModel { MainViewModel(get()) }
}
