package com.example.yandex_maps

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yandex.mapkit.*
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.geometry.SubpolylineHelper
import com.yandex.mapkit.layers.GeoObjectTapEvent
import com.yandex.mapkit.layers.GeoObjectTapListener
import com.yandex.mapkit.location.*
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.*
import com.yandex.mapkit.transport.masstransit.SectionMetadata.SectionData
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider
import com.yandex.runtime.network.NetworkError
import com.yandex.runtime.network.RemoteError
import java.util.*
import com.yandex.mapkit.map.Map


const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 101
const val TAG = "MyApp"

private const val DESIRED_ACCURACY = 0.0
private const val MINIMAL_TIME: Long = 10000
private const val MINIMAL_DISTANCE = 1.0
private const val USE_IN_BACKGROUND = false

class MainActivity : AppCompatActivity(), GeoObjectTapListener, InputListener,
	Session.RouteListener {
	
	private val viewModel by viewModels<MyViewModel>()
	
	private var locationManager: LocationManager? = null
	private var myLocationListener: LocationListener? = null
	private var myLocation: Point? = null
	private lateinit var geocoder: Geocoder
	
	private var mapObjects: MapObjectCollection? = null
	
	private lateinit var mtRouter: MasstransitRouter
	
	private lateinit var mapView: MapView
	
	var polylineMapObject: PolylineMapObject? = null
	
	private var timer: Timer? = null
	
	private lateinit var mark: PlacemarkMapObject
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		MapKitFactory.initialize(this)
		setContentView(R.layout.activity_main)
		
		getLocationPermission()
		
		geocoder = Geocoder(this, Locale.getDefault())
		mtRouter = TransportFactory.getInstance().createMasstransitRouter()
		
		setContentView(R.layout.activity_main)
		mapView = findViewById(R.id.mapview)
		
		mapObjects = mapView.map.mapObjects.addCollection()
		
		if (viewModel.mark != null){
			mark = viewModel.mark!!
			placeMarkInit(Point(mark.geometry.latitude, mark.geometry.longitude))
			moveCamera(Point(mark.geometry.latitude, mark.geometry.longitude))
		}
		
		locationManager = MapKitFactory.getInstance().createLocationManager()
		myLocationListener = object : LocationListener {
			override fun onLocationUpdated(location: Location) {
				if (myLocation == null) {
					if (viewModel.mark == null) {
						Log.v("lol", "lol")
						placeMarkInit(location.position)
						moveCamera(location.position)
					} else {
						mark = viewModel.mark!!
						placeMarkInit(Point(mark.geometry.latitude, mark.geometry.longitude))
						moveCamera(Point(mark.geometry.latitude, mark.geometry.longitude))
					}
					viewModel.startLat = location.position.latitude
					Log.v(TAG, "${viewModel.startLat}")
					viewModel.startLng = location.position.longitude
					Log.v(TAG, "${viewModel.startLng}")
				}
				myLocation = location.position //this user point
			}
			
			override fun onLocationStatusUpdated(locationStatus: LocationStatus) {
				if (locationStatus == LocationStatus.NOT_AVAILABLE) {
					Log.d(TAG, "LocationStatusUpdated")
				}
			}
		}
		
		findViewById<ImageButton>(R.id.curLocButton).setOnClickListener {
			if (myLocation == null) {
				Toast.makeText(this, "Waiting...", Toast.LENGTH_SHORT).show()
			} else {
//				placeMarkInit(myLocation!!)
				moveCamera(myLocation!!)
			}
		}
		
		findViewById<ImageButton>(R.id.zoomPlusButton).setOnClickListener {
			zoom(1)
		}
		
		findViewById<ImageButton>(R.id.zoomMinusButton).setOnClickListener {
			zoom(-1)
		}
		
		findViewById<ImageButton>(R.id.azimuthButton).setOnClickListener {
			azimuth()
		}
		
		findViewById<FloatingActionButton>(R.id.routingBtn).setOnClickListener {
			mapObjects?.clear()
			if (viewModel.startLat != null && viewModel.startLng != null && viewModel.endLat != null && viewModel.endLng != null) {
				routing(
					viewModel.startLat!!,
					viewModel.startLng!!,
					viewModel.endLat!!,
					viewModel.endLng!!
				)
			} else {
				Toast.makeText(this, "You wrote incorrect address", Toast.LENGTH_SHORT).show()
			}
		}
		
		searchLocationViewWatcher()
		
		mapView.map.addTapListener(this)
		mapView.map.addInputListener(this)
	}
	
	private fun placeMarkInit(point: Point) {
		val bitmapMarker = this.getBitmapFromVectorDrawable(R.drawable.ic_point)
		mark = mapView.map.mapObjects.addPlacemark(point, ImageProvider.fromBitmap(bitmapMarker))
		viewModel.mark = mark
	}
	
	private fun searchLocationViewWatcher() {
		val searchView = findViewById<SearchView>(R.id.searchLocationView)
		searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
			override fun onQueryTextSubmit(loc: String?): Boolean {
				timer?.cancel()
				Log.v(TAG, "lol")
				if (loc != null) {
					val queryLoc = geocoder.getFromLocationName(searchView.query.toString(), 1)
					if (queryLoc.isNotEmpty()) {
						moveCamera(Point(queryLoc[0].latitude, queryLoc[0].longitude))
						viewModel.endLat = queryLoc[0].latitude
						viewModel.endLng = queryLoc[0].longitude
					}
					hideKeyboard()
				}
				return true
			}
			
			override fun onQueryTextChange(newText: String?): Boolean {
				if (timer != null) {
					timer?.cancel()
				}
				if (!newText.isNullOrEmpty()) {
					timer = Timer()
					timer?.schedule(object : TimerTask() {
						override fun run() {
							Handler(Looper.getMainLooper()).postDelayed({
								val queryLoc = geocoder.getFromLocationName(newText, 1)
								moveCamera(Point(queryLoc[0].latitude, queryLoc[0].longitude))
								viewModel.startLat = queryLoc[0].latitude
								viewModel.startLng = queryLoc[0].longitude
							}, 0)
						}
					}, 2000)
				}
				return true
			}
		})
	}
	
	
	// Taps
	override fun onObjectTap(geoObjectTapEvent: GeoObjectTapEvent): Boolean {
		val selectionMetadata = geoObjectTapEvent
			.geoObject
			.metadataContainer
			.getItem(GeoObjectSelectionMetadata::class.java)
		if (selectionMetadata != null) {
			mapView.map.selectGeoObject(selectionMetadata.id, selectionMetadata.layerId)
			val point = geoObjectTapEvent.geoObject.geometry[0].point
			if (point != null) {
				viewModel.endLat = point.latitude
				viewModel.endLng = point.longitude
				val address = viewModel.getAddressLine(point.latitude, point.longitude, geocoder)
				findViewById<FloatingActionButton>(R.id.pickLocBtn).setOnClickListener {
					Toast.makeText(this, address, Toast.LENGTH_SHORT).show()
				}
			} else {
				Log.e("lol", "This place has no address")
			}
		}
		return selectionMetadata != null
	}
	
	override fun onMapTap(map: Map, point: Point) {
		mapView.map.deselectGeoObject()
	}
	
	override fun onMapLongTap(p0: Map, p1: Point) {
		Log.d(TAG, "onMapLongTap")
	}
	
	//Routes
	private fun routing(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
		val options = MasstransitOptions(
			ArrayList(),
			ArrayList(),
			TimeOptions()
		)
		val points: MutableList<RequestPoint> = ArrayList()
		points.add(RequestPoint(Point(startLat, startLng), RequestPointType.WAYPOINT, null))
		points.add(RequestPoint(Point(endLat, endLng), RequestPointType.WAYPOINT, null))
		mtRouter.requestRoutes(points, options, this)
		Log.v("lol", ";p;")
	}
	
	//controllers
	private fun zoom(value: Int) {
		if (myLocation == null) {
			Toast.makeText(this, "Waiting...", Toast.LENGTH_SHORT).show()
		} else {
			mapView.map.move(
				CameraPosition(
					mapView.map.cameraPosition.target,
					mapView.map.cameraPosition.zoom + value, 0.0f, 0.0f
				),
				Animation(Animation.Type.SMOOTH, 1f),
				null
			)
		}
	}
	
	private fun azimuth() {
		mapView.map.move(
			CameraPosition(
				mapView.map.cameraPosition.target,
				mapView.map.cameraPosition.zoom, 0.0f, 0.0f
			),
			Animation(Animation.Type.SMOOTH, 1f),
			null
		)
	}
	
	private fun getLocationPermission() {
		if (ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.ACCESS_FINE_LOCATION
			)
			== PackageManager.PERMISSION_GRANTED
		) {
			Log.e(TAG, "Permission granted")
		} else {
			ActivityCompat.requestPermissions(
				this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
				PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
			)
		}
	}
	
	//Location Update
	private fun subscribeToLocationUpdate() {
		if (locationManager != null && myLocationListener != null) {
			locationManager!!.subscribeForLocationUpdates(
				DESIRED_ACCURACY,
				MINIMAL_TIME,
				MINIMAL_DISTANCE,
				USE_IN_BACKGROUND,
				FilteringMode.OFF,
				myLocationListener!!
			)
		}
	}
	
	private fun moveCamera(point: Point) {
		var zoom = mapView.map.cameraPosition.zoom
		if (zoom < 9f) {
			zoom = 17f
		}
		mapView.map.move(
			CameraPosition(point, zoom, 0.0f, 0.0f),
			Animation(Animation.Type.SMOOTH, 1f),
			null
		)
		val bitmapMarker = this.getBitmapFromVectorDrawable(R.drawable.ic_point)
		mark.geometry = point
		mark.setIcon(ImageProvider.fromBitmap(bitmapMarker))
	}
	
	//Drawing routes
	override fun onMasstransitRoutes(routes: MutableList<Route>) {
		// In this example we consider first alternative only
		if (routes.size > 0) {
			for (section in routes[0].sections) {
				drawSection(
					section.metadata.data,
					SubpolylineHelper.subpolyline(
						routes[0].geometry, section.geometry
					)
				)
			}
		} else {
			Toast.makeText(this, "Route can`t be build. Check address points", Toast.LENGTH_SHORT)
				.show()
		}
	}
	
	override fun onMasstransitRoutesError(error: Error) {
		var errorMessage = getString(R.string.unknown_error_message)
		if (error is RemoteError) {
			errorMessage = getString(R.string.remote_error_message)
		} else if (error is NetworkError) {
			errorMessage = getString(R.string.network_error_message)
		}
		Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
	}
	
	private fun drawSection(
		data: SectionData,
		geometry: Polyline
	) {
		polylineMapObject = mapObjects!!.addPolyline(geometry)
		if (data.transports != null) {
			for (transport in data.transports!!) {
				if (transport.line.style != null) {
					polylineMapObject?.strokeColor = transport.line.style!!.color!! or -0x1000000
					return
				}
			}
			val knownVehicleTypes = HashSet<String>()
			knownVehicleTypes.add("bus")
			knownVehicleTypes.add("tramway")
			knownVehicleTypes.add("car")
			for (transport in data.transports!!) {
				val sectionVehicleType: String? =
					viewModel.getVehicleType(transport, knownVehicleTypes)
				if (sectionVehicleType == "bus") {
					polylineMapObject?.strokeColor = -0xff0100 // Green
					return
				} else if (sectionVehicleType == "tramway") {
					polylineMapObject?.strokeColor = -0x10000 // Red
					return
				}
			}
			polylineMapObject?.strokeColor = -0xffff01 // Blue - metro
		} else {
			polylineMapObject?.strokeColor = -0x1000000 // Black - on foot
		}
	}
	
	private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap? {
		var drawable = ContextCompat.getDrawable(this, drawableId) ?: return null
		
		drawable = DrawableCompat.wrap(drawable).mutate()
		
		val bitmap = Bitmap.createBitmap(
			drawable.intrinsicWidth,
			drawable.intrinsicHeight,
			Bitmap.Config.ARGB_8888
		) ?: return null
		val canvas = Canvas(bitmap)
		drawable.setBounds(0, 0, canvas.width, canvas.height)
		drawable.draw(canvas)
		
		return bitmap
	}
	
	fun Activity.hideKeyboard() {
		hideKeyboard(currentFocus ?: View(this))
	}
	
	private fun Context.hideKeyboard(view: View) {
		val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
		inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
	}
	
	override fun onStart() {
		super.onStart()
		mapView.onStart();
		MapKitFactory.getInstance().onStart();
		
		subscribeToLocationUpdate();
	}
	
	override fun onStop() {
		super.onStop()
		MapKitFactory.getInstance().onStop();
		locationManager?.unsubscribe(myLocationListener!!);
		mapView.onStop();
	}
}