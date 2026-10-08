package es.alejandro.grandarbiter.di

import androidx.room.Room
import es.alejandro.grandarbiter.data.cards.CardRepositoryImpl
import es.alejandro.grandarbiter.data.cards.local.AssetCardDataSource
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.cards.local.CardsDatabase
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallHeadersInterceptor
import es.alejandro.grandarbiter.data.cards.update.ScryfallBulkClient
import es.alejandro.grandarbiter.data.cards.update.ScryfallCardUpdater
import es.alejandro.grandarbiter.data.local.AppDatabase
import es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
import es.alejandro.grandarbiter.data.rules.RulesRepositoryImpl
import es.alejandro.grandarbiter.data.rules.local.AssetRulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import es.alejandro.grandarbiter.data.rules.local.RulesDatabase
import es.alejandro.grandarbiter.data.timer.TimerManagerImpl
import es.alejandro.grandarbiter.domain.repository.CardDataUpdater
import es.alejandro.grandarbiter.domain.repository.RulesDataUpdater
import es.alejandro.grandarbiter.data.rules.update.PublishedRulesClient
import es.alejandro.grandarbiter.data.rules.update.PublishedRulesUpdater
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import es.alejandro.grandarbiter.domain.timer.TimerManager
import es.alejandro.grandarbiter.ui.cardsearch.CardDetailViewModel
import es.alejandro.grandarbiter.ui.cardsearch.CardSearchViewModel
import es.alejandro.grandarbiter.ui.home.HomeViewModel
import es.alejandro.grandarbiter.ui.main.MainViewModel
import es.alejandro.grandarbiter.ui.reader.ReaderContentsViewModel
import es.alejandro.grandarbiter.ui.reader.ReaderPageViewModel
import es.alejandro.grandarbiter.ui.settings.SettingsViewModel
import es.alejandro.grandarbiter.ui.tools.banned.BannedListsViewModel
import es.alejandro.grandarbiter.ui.tools.announcement.AnnouncementViewModel
import es.alejandro.grandarbiter.ui.tools.deckcounter.DeckCounterViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import es.alejandro.grandarbiter.ui.reader.GlossaryProvider
import es.alejandro.grandarbiter.ui.reader.GlossaryViewModel
import es.alejandro.grandarbiter.data.releasenotes.ReleaseNotesRepository
import es.alejandro.grandarbiter.ui.tools.releasenotes.ReleaseNotesViewModel
import es.alejandro.grandarbiter.BuildConfig
import es.alejandro.grandarbiter.data.cards.update.RoomInstalledCards
import es.alejandro.grandarbiter.data.rules.update.RoomInstalledRules
import es.alejandro.grandarbiter.data.quickrefs.QuickReferenceRepositoryImpl
import es.alejandro.grandarbiter.domain.repository.QuickReferenceRepository
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferenceDetailViewModel
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferenceEditViewModel
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferencesViewModel

val networkModule = module {
    single {
        // HEADERS at most: BODY would buffer and log Scryfall's 25 MB bulk files.
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
    }

    single {
        OkHttpClient.Builder()
            .addInterceptor(ScryfallHeadersInterceptor())   // Scryfall answers 400 without them
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
            .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
            .addCallback(AppDatabase.SEED_PRESETS)
            // No destructive fallback: this database holds the judge's own data.
            .build()
    }

    single { get<AppDatabase>().cardDao() }
    single { get<AppDatabase>().quickReferenceDao() }
    
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
    single { GlossaryProvider(get()) }
    single { ReleaseNotesRepository(androidContext(), get(), PublishedRulesUpdater.PUBLISHED_RULES_URL) }
    single { PublishedRulesClient(get(), PublishedRulesUpdater.PUBLISHED_RULES_URL) }
    single<RulesDataUpdater> { PublishedRulesUpdater(get(), RoomInstalledRules(get(), get(), get()), BuildConfig.VERSION_CODE) }
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
    single { ScryfallBulkClient(get()) }
    single<CardDataUpdater> { ScryfallCardUpdater(get(), RoomInstalledCards(get(), get()), androidContext().cacheDir) }
}

val repositoryModule = module {
    single<CardRepository> { CardRepositoryImpl(get()) }
    single<QuickReferenceRepository> { QuickReferenceRepositoryImpl(get(), get(), get()) }
}

val appModule = module {
    single<TimerManager> { TimerManagerImpl(androidContext()) }
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ReaderContentsViewModel)
    viewModelOf(::ReaderPageViewModel)
    viewModelOf(::CardSearchViewModel)
    viewModelOf(::CardDetailViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::BannedListsViewModel)
    viewModelOf(::AnnouncementViewModel)
    viewModelOf(::DeckCounterViewModel)
    viewModelOf(::QuickReferencesViewModel)
    viewModelOf(::ReleaseNotesViewModel)
    viewModelOf(::GlossaryViewModel)
    viewModelOf(::QuickReferenceDetailViewModel)
    viewModelOf(::QuickReferenceEditViewModel)
}
