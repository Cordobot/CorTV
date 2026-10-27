package cordobot.pacificrim.cortv_tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class CorTvApp : Application(), ImageLoaderFactory {

    // Wikimedia rejects generic User-Agents (e.g. "okhttp/x.y"), so identify the app explicitly.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", USER_AGENT)
                                .build()
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        const val USER_AGENT = "CorTV/1.0 (Android TV)"
    }
}
