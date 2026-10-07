package es.alejandro.grandarbiter

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import es.alejandro.grandarbiter.di.appModule
import es.alejandro.grandarbiter.di.cardsModule
import es.alejandro.grandarbiter.di.databaseModule
import es.alejandro.grandarbiter.di.networkModule
import es.alejandro.grandarbiter.di.repositoryModule
import es.alejandro.grandarbiter.di.rulesModule
import es.alejandro.grandarbiter.di.viewModelModule
import es.alejandro.grandarbiter.data.cards.local.AssetCardDataSource
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.rules.local.AssetRulesBundleSource
import es.alejandro.grandarbiter.data.rules.local.RulesBundleImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class GrandArbiterApplication : Application(), ImageLoaderFactory {

    /** App-lifetime work that must not be cancelled with a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Coil uses the app's OkHttpClient, which adds the headers Scryfall's image CDN requires. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { get<OkHttpClient>() }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GrandArbiterApplication)
            modules(
                networkModule,
                databaseModule,
                rulesModule,
                cardsModule,
                repositoryModule,
                appModule,
                viewModelModule
            )
        }

        // Load the bundled rules into rules.db when the APK ships a newer bundle (no-op otherwise).
        appScope.launch {
            try {
                val result = get<RulesBundleImporter>().import(get<AssetRulesBundleSource>())
                Log.i("RulesImport", result.toString())
            } catch (e: Exception) {
                // Never crash the app over this: the previous rules content stays in place.
                Log.e("RulesImport", "Bundled rules import failed", e)
            }
        }

        // Load the card seed on first run, or when the APK ships a seed newer than the installed
        // data (a Scryfall update that is already newer wins). Takes a few seconds the first time.
        appScope.launch {
            try {
                val result = get<CardImporter>().import(get<AssetCardDataSource>())
                Log.i("CardImport", result.toString())
            } catch (e: Exception) {
                Log.e("CardImport", "Card seed import failed", e)
            }
        }
    }
}
