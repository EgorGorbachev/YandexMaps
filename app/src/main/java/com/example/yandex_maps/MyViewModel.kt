package com.example.yandex_maps

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.AndroidViewModel
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.transport.masstransit.Transport
import java.util.HashSet

class MyViewModel(
	application: Application
) : AndroidViewModel(application) {
	
	var startLat: Double? = null
	var startLng: Double? = null
	var endLat: Double? = null
	var endLng: Double? = null
	
	var mark: PlacemarkMapObject? = null
	
	
	fun getAddressLine(lat: Double, lng: Double, geocoder: Geocoder): String {
		val address = geocoder.getFromLocation(lat, lng, 1)
		return if (address[0] != null) {
			address[0].getAddressLine(0).toString()
		} else {
			"This place have incorrect address"
		}
		
		
	}
	
	fun getVehicleType(transport: Transport, knownVehicleTypes: HashSet<String>): String? {
		for (type in transport.line.vehicleTypes) {
			if (knownVehicleTypes.contains(type)) {
				return type
			}
		}
		return null
	}
}

class C (val map:MutableMap<String, out String>)