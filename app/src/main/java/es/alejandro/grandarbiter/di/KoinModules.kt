package es.alejandro.grandarbiter.di

import androidx.room.Room
import es.alejandro.grandarbiter.data.local.AppDatabase
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.remote.ScryfallService
import es.alejandro.grandarbiter.data.repository.CRRepositoryImpl
import es.alejandro.grandarbiter.data.rules.RulesRepositoryImpl
import es.alejandro.grandarbiter.data.rules.local.AssetRulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import es.alejandro.grandarbiter.data.rules.local.RulesDatabase
import es.alejandro.grandarbiter.data.repository.CardRepositoryImpl
import es.alejandro.grandarbiter.data.timer.TimerManagerImpl
import es.alejandro.grandarbiter.domain.repository.CRRepository
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import es.alejandro.grandarbiter.ui.comprehensiverules.ComprehensiveRulesViewModel
import es.alejandro.grandarbiter.ui.home.HomeViewModel
import es.alejandro.grandarbiter.ui.main.MainViewModel
import es.alejandro.grandarbiter.ui.reader.ReaderContentsViewModel
import es.alejandro.grandarbiter.ui.reader.ReaderPageViewModel
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

/** Rules content: a separate, disposable DB rebuilt from the bundle (see RulesDatabase.kt). */
val rulesModule = module {
    single {
        Room.databaseBuilder(androidContext(), RulesDatabase::class.java, "rules.db")
            .fallbackToDestructiveMigration(dropAllTables = true)   // safe: content is re-imported
            .build()
    }
    single { get<RulesDatabase>().rulesDao() }
    single { AssetRulesBundleSource(androidContext()) }
    single { RulesBundleImporter(get(), get()) }
    single<RulesRepository> { RulesRepositoryImpl(get()) }
}

val repositoryModule = module {
    single<CRRepository> { CRRepositoryImpl(get()) }
    single<CardRepository> { CardRepositoryImpl(get(), get()) }
}

val appModule = module {
    single<TimerManager> { TimerManagerImpl(androidContext()) }
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ComprehensiveRulesViewModel)
    viewModelOf(::ReaderContentsViewModel)
    viewModelOf(::ReaderPageViewModel)
}
