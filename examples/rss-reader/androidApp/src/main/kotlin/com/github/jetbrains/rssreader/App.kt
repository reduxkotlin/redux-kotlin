package com.github.jetbrains.rssreader

import android.app.Application
import android.content.Context
import com.github.jetbrains.rssreader.app.FeedStore
import com.github.jetbrains.rssreader.core.buildRssReader
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin()
    }

    private val appModule = module {
        single { buildRssReader(get<Context>(), BuildConfig.DEBUG) }
        single { FeedStore(get()) }
    }

    private fun initKoin() {
        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(appModule)
        }
    }
}
