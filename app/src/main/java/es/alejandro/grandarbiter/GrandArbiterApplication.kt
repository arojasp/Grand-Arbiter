package es.alejandro.grandarbiter

import android.app.Application
import es.alejandro.grandarbiter.di.appModule
import es.alejandro.grandarbiter.di.databaseModule
import es.alejandro.grandarbiter.di.networkModule
import es.alejandro.grandarbiter.di.repositoryModule
import es.alejandro.grandarbiter.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class GrandArbiterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GrandArbiterApplication)
            modules(
                networkModule,
                databaseModule,
                repositoryModule,
                appModule,
                viewModelModule
            )
        }
    }
}
