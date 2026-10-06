package com.ninepointlabs.quill

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade

class QuillApplication : Application() {
    companion object {
        lateinit var imageLoader: ImageLoader
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        imageLoader = ImageLoader.Builder(this)
            .diskCache { 
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .build() 
            }
            .crossfade(true)
            .build()
            
        SingletonImageLoader.setSafe { imageLoader }
    }
}
