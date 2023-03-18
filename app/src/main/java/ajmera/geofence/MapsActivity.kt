package ajmera.geofence

import ajmera.geofence.Constants.ACTION_GEOFENCE_EVENT
import ajmera.geofence.databinding.ActivityMapsBinding
import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.Application
import android.app.PendingIntent
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.*
import android.provider.Settings
import android.util.Log
import android.util.Xml
import android.view.View
import android.view.View.OnClickListener
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, View.OnClickListener {

    private lateinit var googleMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var geofencingClient: GeofencingClient
    private val runningQOrLater =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private lateinit var currLocationMarker: Marker

    private lateinit var gpxLocList:List<GpxLoc>

    //A PendingIntent for the Broadcast Receiver that handles geofence transitions.
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeoFenceBroadCastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private val foreGroundReqPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ it ->
            var allPermissionsGranted = false
            it.entries.forEach {
                allPermissionsGranted = it.value
            }
            if(allPermissionsGranted){
                requestBackgroundLocationPermissions()
            }
        }

    private val backGroundReqPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if(it){

            }else{

            }
        }

    companion object {
        const val TAG_GPX = "gpx"
        const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
        const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        geofencingClient = LocationServices.getGeofencingClient(this)

        setOnClickListeners()

        requestMockLocationPerm()

        createNotificationChannel(this@MapsActivity)
    }

    private fun setOnClickListeners(){
        binding.btnAddGeoFence.setOnClickListener(this)
        binding.btnStartSimulator.setOnClickListener(this)
    }

    private fun requestMockLocationPerm(){
        if(!isMockLocationEnabled()){
            showMockLocationPermissionDialog()
        }else{
            initFusedLocationProvider()
        }
    }

    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                        &&
                        PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }

    @SuppressLint("MissingPermission")
    private fun initFusedLocationProvider(){
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MapsActivity)
        fusedLocationClient.setMockMode(true)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .setMaxUpdateDelayMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(p0: LocationResult) {
                val locationList = p0.locations
                if (locationList.isNotEmpty()) {
                    val location = locationList.last()
                    //Place current location marker
                    val latLng = LatLng(location.latitude, location.longitude)
                    val markerOptions = MarkerOptions()
                    markerOptions.position(latLng)
                    markerOptions.title("Current Position")
                    markerOptions.icon(
                        BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_MAGENTA
                        )
                    )
                    moveCamera(latLng)
                    animateCamera(latLng)

                    if (this@MapsActivity::currLocationMarker.isInitialized) {
                        animateMarker(location, currLocationMarker)
                    } else {
                        currLocationMarker = googleMap.addMarker(markerOptions)!!
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun isMockLocationEnabled(): Boolean {
        val isMockLocation: Boolean
        try {
            val opsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            isMockLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                opsManager.unsafeCheckOp(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    BuildConfig.APPLICATION_ID
                ) == AppOpsManager.MODE_ALLOWED
            } else {
                opsManager.checkOp(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    BuildConfig.APPLICATION_ID
                ) == AppOpsManager.MODE_ALLOWED
            }
        } catch (e: Exception) {
            return false
        }
        return isMockLocation
    }

    private fun setMockLocation(gpxLoc: GpxLoc) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val location = Location("network")
            location.latitude = gpxLoc.latLng.latitude
            location.longitude = gpxLoc.latLng.longitude
            location.time = System.currentTimeMillis()
            location.accuracy = 3.0f
            location.elapsedRealtimeNanos = System.nanoTime()

            fusedLocationClient.setMockLocation(location).addOnSuccessListener {
                //Toast.makeText(this@MapsActivity, "Success", Toast.LENGTH_LONG).show()
            }.addOnFailureListener {
                Toast.makeText(this@MapsActivity, "Failure", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun animateMarker(destination: Location, marker: Marker?) {
        if (marker != null) {
            val startPosition = marker.position
            val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
            valueAnimator.duration = 8000 // duration 1 second
            valueAnimator.interpolator = LinearInterpolator()
            valueAnimator.addUpdateListener { animation ->
                try {
                    val v = animation.animatedFraction
                    marker.position = LatLng(
                        destination.latitude * v + (1 - v) * startPosition.latitude,
                        destination.longitude * v + (1 - v) * startPosition.longitude
                    )
                } catch (ex: java.lang.Exception) {
                    // I don't care atm..
                }
            }
            valueAnimator.start()
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        addGpxFileToMap()
    }

    private fun addGpxFileToMap() {
        val input: InputStream = assets.open("locationsforsimulation.gpx")
        gpxLocList = mutableListOf()
        gpxLocList = parseGpxFile(input)

        //adding to application class, so that we can access in broadcast receiver to show location names...
        (application as GeoFenceApplication).setGpxLocList(gpxLocList)

        val latLongList: MutableList<LatLng> = mutableListOf()
        gpxLocList.forEach { gpxLoc ->
            //adding markers
            googleMap.addMarker(MarkerOptions().position(gpxLoc.latLng).title(gpxLoc.title))
            //adding circle
             googleMap.addCircle(
                 CircleOptions()
                     .center(gpxLoc.latLng)
                     .radius(100.0)
                     .strokeWidth(2.0F)
                     .strokeColor(ContextCompat.getColor(this@MapsActivity,R.color.polyline_color))
                     .fillColor(ContextCompat.getColor(this@MapsActivity,R.color.light_blue))
             )
            latLongList.add(gpxLoc.latLng)
        }
        //drawing polyline
        googleMap.addPolyline(
            PolylineOptions()
                .addAll(latLongList)
                .width(12.0F)
                .color(
                    ContextCompat.getColor(
                        this@MapsActivity,
                        R.color.polyline_color
                    )
                ) //Google maps blue color
                .geodesic(true)
        )
        moveCamera(gpxLocList[0].latLng)
        animateCamera(gpxLocList[0].latLng)
    }

    private fun moveCamera(latLng: LatLng) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: LatLng) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun requestForegroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved())
            return

        val permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS)

        foreGroundReqPermissionLauncher.launch(permissionsArray)
    }

    private fun requestBackgroundLocationPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backGroundReqPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.btnAddGeoFence -> checkPermissionsAndAddGeoFences()
            R.id.btnStartSimulator -> checkMockLocationAndStart()
        }
    }

    private fun checkMockLocationAndStart() {
        if(isMockLocationEnabled()){
            initFusedLocationProvider()
            startSimulator()
        }else{
            showMockLocationPermissionDialog()
        }
    }

    private fun showMockLocationPermissionDialog(){
        MaterialAlertDialogBuilder(this@MapsActivity)
            .setTitle("Allow mock locations")
            .setMessage("You have to allow mock location to run the app.(Settings->Developer Options->" +
                    "Select mock location app")
            .setPositiveButton("SETTINGS"
            ) { p0, p1 ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            }.setNegativeButton("CANCEL") { p0, p1 ->

            }.create().show()
    }

    private fun startSimulator() {
        var locIndex = 0
        val timer = Timer()
        Toast.makeText(this@MapsActivity,"Starting Simulation..",Toast.LENGTH_SHORT).show()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                setMockLocation(gpxLocList[locIndex])
                if (locIndex == gpxLocList.size - 1) {
                    timer.cancel()
                }
                locIndex++
            }
        }, 5000, 8000)
    }

    private fun checkPermissionsAndAddGeoFences(){
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            addGeoFences()
        } else {
            requestForegroundLocationPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeoFences() {
        val geoFenceList = mutableListOf<Geofence>()
        gpxLocList.forEach {
            val geofence = Geofence.Builder()
                .setRequestId(it.id)
                .setCircularRegion(
                    it.latLng.latitude,
                    it.latLng.longitude,
                    Constants.GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(Constants.GEOFENCE_EXPIRY_TIME)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
            geoFenceList.add(geofence)
        }
        // Build the geofence request
        val geofencingRequest = GeofencingRequest.Builder()
            // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
            // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
            // is already inside that geofence.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            // Add the geofences to be monitored by geofencing service.
            .addGeofences(geoFenceList)
            .build()

        // First, remove any existing geofences that use our pending intent
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            // Regardless of success/failure of the removal, add the new geofence
            addOnCompleteListener {
                // Add the new geofence request with the new geofence
                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                    addOnSuccessListener {
                        // Geofences added.
                        Toast.makeText(
                            this@MapsActivity, "GeoFencesAdded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    addOnFailureListener {
                        // Failed to add geofences.
                        Toast.makeText(
                            this@MapsActivity, "GeoFencesNotAdded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun parseGpxFile(inputStream: InputStream): List<GpxLoc> {
        inputStream.use { inputStream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag()
            parser.require(XmlPullParser.START_TAG, null, TAG_GPX)
            return GpxUtils().readGpxFile(parser)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}