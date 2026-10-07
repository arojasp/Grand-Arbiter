package es.alejandro.grandarbiter.di

import androidx.room.Room
import es.alejandro.grandarbiter.data.cards.CardRepositoryImpl
import es.alejandro.grandarbiter.data.cards.local.AssetCardDataSource
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.cards.local.CardsDatabase
import es.alejandro.grandarbiter.data.local.AppDatabase
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.rules.RulesRepositoryImpl
import es.alejandro.grandarbiter.data.rules.local.AssetRulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import es.alejandro.grandarbiter.data.rules.local.RulesDatabase
import es.alejandro.grandarbiter.data.timer.TimerManagerImpl
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import es.alejandro.grandarbiter.ui.home.HomeViewModel
import es.alejandro.grandarbiter.ui.main.MainViewModel
import es.alejandro.grandarbiter.ui.reader.ReaderContentsViewModel
import es.alejandro.grandarbiter.ui.reader.ReaderPageViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val networkModule = module {
    single {
        // HEADERS at most: BODY would buffer and log Scryfall's 25 MB bulk files.
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    }

    single {
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "judge_companion_db"
        )
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

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

/** Card data: a separate, disposable DB filled from Scryfall snapshots (see CardsDatabase.kt). */
val cardsModule = module {
    single {
        Room.databaseBuilder(androidContext(), CardsDatabase::class.java, "cards.db")
            .fallbackToDestructiveMigration(dropAllTables = true)   // safe: content is re-imported
            .build()
    }
    single { get<CardsDatabase>().cardsDao() }
    single { AssetCardDataSource(androidContext()) }
    single { CardImporter(get(), get()) }
}

val repositoryModule = module {
    single<CardRepository> { CardRepositoryImpl(get()) }
}

val appModule = module {
    single<TimerManager> { TimerManagerImpl(androidContext()) }
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ReaderContentsViewModel)
    viewModelOf(::ReaderPageViewModel)
}
