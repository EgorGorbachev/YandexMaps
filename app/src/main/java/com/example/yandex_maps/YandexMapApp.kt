package com.example.yandex_maps

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class YandexMapApp:Application() {
	override fun onCreate() {
		super.onCreate()
		MapKitFactory.setApiKey(resources.getString(R.string.yandex_maps_api_key))
	}
}