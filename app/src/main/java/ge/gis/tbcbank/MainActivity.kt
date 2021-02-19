package ge.gis.tbcbank

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Log.d
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.aldebaran.qi.Consumer
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.util.FutureUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoTo
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import kotlinx.android.synthetic.main.activity_localization.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList


class MainActivity : RobotActivity(), RobotLifecycleCallbacks {
    private var TAG = "DDDD"
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var selectedLocation: String? = null
    private val RECHARGE_PERMISSION =
        "com.softbankrobotics.permission.AUTO_RECHARGE" // recharge permission
    private val load_location_success = AtomicBoolean(false)
    private var ma: MainActivity? = null

    private val MULTIPLE_PERMISSIONS = 2
//    private val savedLocations = mutableMapOf<String, FreeFrame>()
    private var qiContext: QiContext? = null
    private var goToFuture: Future<Boolean>? = null
    private var actuation: Actuation? = null
    private var mapping: Mapping? = null
    private var initialExplorationMap: ExplorationMap? = null
    private var holder: Holder? = null
    var publishExplorationMapFuture: Future<Void>? = null
    var goto: StubbornGoTo? = null
    var future: Future<Unit>? =null
    var savedLocations = TreeMap<String, AttachedFrame>()
    val filesDirectoryPath = "/sdcard/Maps"
    val mapFileName = "mapData.txt"
    private val locationsFileName = "points.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        QiSDK.register(this, this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                RECHARGE_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            QiSDK.register(this, this)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    RECHARGE_PERMISSION
                ),
                MULTIPLE_PERMISSIONS
            )
        }


        save_button.setOnClickListener {
            val location: String = add_item_edit.text.toString()
            add_item_edit.text.clear()
            // Save location only if new.
            if (location.isNotEmpty()) {
                spinnerAdapter.add(location)
                saveLocation(location)
                backupLocations()
            }
        }

        canelGoTo.setOnClickListener{

        }

        goto_button.setOnClickListener {
            selectedLocation?.let {
                goto_button.isEnabled = false
                save_button.isEnabled = false
                val thread = Thread {
                    goToLocation(it)
                }
                thread.start()
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                selectedLocation = parent.getItemAtPosition(position) as String
                Log.i("TAG", "onItemSelected: $selectedLocation")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedLocation = null
                Log.i("TAG", "onNothingSelected")
            }
        }

        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ArrayList())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter


        sTopLocalization.setOnClickListener {
            loadLocations()
//            try {
//
//                goto!!.async().run().requestCancellation()
//            }catch(dd: Exception) {
//                Log.i("TAG", "dd")
//            } finally {
//                Thread {
//                    goto!!.async().run().requestCancellation()
//                }.start()
//            }


//            publishExplorationMapFuture?.cancel(true)
//            releaseAbilities(holder!!)
        }

        extendMapButton.setOnClickListener {
            // Check that an initial map is available.
            val initialExplorationMap = initialExplorationMap ?: return@setOnClickListener
            // Check that the Activity owns the focus.
            val qiContext = qiContext ?: return@setOnClickListener
            // Start the map extension step.
            startMapExtensionStep(initialExplorationMap, qiContext)
        }

        startMappingButton.setOnClickListener {
            // Check that the Activity owns the focus.
            val qiContext = qiContext ?: return@setOnClickListener
            // Start the mapping step.
            startMappingStep(qiContext)
        }

    }


//    fun saveLocation(location: String) {
//        val robotFrameFuture =  qiContext!!.actuation.async()?.robotFrame()
//        robotFrameFuture?.andThenConsume { robotFrame ->
//            val locationFrame: FreeFrame? = qiContext!!.mapping.makeFreeFrame()
//            val transform: Transform = TransformBuilder.create().fromXTranslation(0.0)
//            locationFrame!!.update(robotFrame, transform, 1L)
//            Log.i("SaveLocation", "Location $location and Frame $locationFrame")
//            savedLocations.put(location, locationFrame);
//        }
//    }

    fun saveLocation(location: String): Future<Void> {
        // Get the robot frame asynchronously.
        Log.d(TAG, "saveLocation: Start saving this location")
        return createAttachedFrameFromCurrentPosition()!!.andThenConsume(Consumer<AttachedFrame> { attachedFrame: AttachedFrame ->
            savedLocations[location] = attachedFrame
        })
    }
    private fun createAttachedFrameFromCurrentPosition(): Future<AttachedFrame>? {
        // Get the robot frame asynchronously.
        return actuation!!.async()
            .robotFrame()
            .andThenApply { robotFrame: Frame ->
                val mapFrame = getMapFrame()

                // Transform between the current robot location (robotFrame) and the mapFrame
                val transformTime = robotFrame.computeTransform(mapFrame)
                mapFrame!!.makeAttachedFrame(transformTime.transform)
            }
    }
    private fun getMapFrame(): Frame? {
        return mapping!!.async().mapFrame().value
    }

    private fun backupLocations() {

        val locationsToBackup = TreeMap<String?, Vector2theta?>()
        val mapFrame: Frame = getMapFrame()!!
        for ((key, destination) in savedLocations) {
            // get location of the frame
                d(
                    "sdsdsdsd", destination.toString()
                )
            val frame = destination.async().frame().value

            // create a serializable vector2theta
            val vector = Vector2theta.betweenFrames(mapFrame, frame)

            // add to backup list
            locationsToBackup[key] = vector
        }
        saveLocationsToFile(filesDirectoryPath, locationsFileName, locationsToBackup)
    }
    private fun saveLocationsToFile(
        filesDirectoryPath: String?,
        locationsFileName: String?,
        locationsToBackup: TreeMap<String?, Vector2theta?>?
    ) {
        val gson = Gson()
        val points = gson.toJson(locationsToBackup)
        var fos: FileOutputStream? = null
        var oos: ObjectOutputStream? = null

        // Backup list into a file
        try {
            val fileDirectory = File(filesDirectoryPath, "")
            if (!fileDirectory.exists()) {
                fileDirectory.mkdirs()
            }
            val file = File(fileDirectory, locationsFileName)
            fos = FileOutputStream(file)
            oos = ObjectOutputStream(fos)
            oos.writeObject(points)
            d(
                TAG,
                "backupLocations: Done"
            )
        } catch (e: FileNotFoundException) {
            Log.e(TAG, e.message, e)
        } catch (e: IOException) {
            Log.e( TAG, e.message, e)
        } finally {
            try {
                oos?.close()
                fos?.close()
            } catch (e: IOException) {
                Log.e(
                   TAG,
                    e.message,
                    e
                )
            }
        }
    }

    private fun getLocationsFromFile(filesDirectoryPath: String, LocationsFileName: String): Map<String?, Vector2theta?>? {
        var vectors: Map<String?, Vector2theta?>? = null
        var fis: FileInputStream? = null
        var ois: ObjectInputStream? = null
        var f: File? = null
        try {
            f = File(filesDirectoryPath, LocationsFileName!!)
            fis = FileInputStream(f)
            ois = ObjectInputStream(fis)
            val points = ois.readObject() as String
            val collectionType = object : TypeToken<Map<String?, Vector2theta?>?>() {}.type
            val gson = Gson()
            vectors = gson.fromJson(points, collectionType)
        } catch (e: IOException) {
            Log.e(TAG, e.message, e)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, e.message, e)
        } finally {
            try {
                ois?.close()
                fis?.close()
            } catch (e: IOException) {
                Log.e(TAG, e.message, e)
            }
        }
        return vectors
    }

    private fun loadLocations(): Future<Boolean>? {
        return FutureUtils.futureOf<Boolean> { f: Future<Void?>? ->
            // Read file into a temporary hashmap.
            val file =
                File(filesDirectoryPath, locationsFileName)
            if (file.exists()) {
                val vectors =
                    getLocationsFromFile(
                        filesDirectoryPath,
                        locationsFileName
                    )

                // Clear current savedLocations.
                savedLocations = TreeMap()
                val mapFrame: Frame = getMapFrame()!!


                // Build frames from the vectors.
                for ((key, value) in vectors!!) {
                    // Create a transform from the vector2theta.
                    val t =
                        value!!.createTransform()
                    d(TAG, "loadLocations: $key")
                    spinnerAdapter.add(key)

                    // Create an AttachedFrame representing the current robot frame relatively to the MapFrame.
                    val attachedFrame =
                        mapFrame.async().makeAttachedFrame(t).value
                    d(TAG, savedLocations[key.toString()].toString())
                    d(TAG, key.toString())
                    d(TAG, savedLocations.toString())

                    // Store the FreeFrame.
                    savedLocations[key.toString()] = attachedFrame
                    load_location_success.set(true)

                    d(TAG, savedLocations[key.toString()].toString())
                    d(TAG, key.toString())
                    d(TAG, savedLocations.toString())

                }
                d(TAG, "loadLocations: Done")
                if (load_location_success.get()) return@futureOf Future.of(
                    true
                ) else throw Exception("Empty file")
            } else {
                throw Exception("No file")
            }
        }
    }
    private fun goToLocation(location: String) {


        val freeFrame: AttachedFrame? = savedLocations[location]
        d("DDDDD1",savedLocations[location].toString())
        val frameFuture: Frame = freeFrame!!.frame()
        val appscope = SingleThread.newCoroutineScope()
        val future: Future<Unit> = appscope.asyncFuture {
            goto = StubbornGoToBuilder.with(qiContext!!)
                .withFinalOrientationPolicy(OrientationPolicy.FREE_ORIENTATION)
                .withMaxRetry(10)
                .withMaxSpeed(0.5f)
                .withMaxDistanceFromTargetFrame(0.0)
                .withWalkingAnimationEnabled(true)
                .withFrame(frameFuture).build()
            goto!!.async().run().await()
            }
//        runBlocking {
//            future.requestCancellation()
//            future.awaitOrNull()
//        }
        waitForInstructions()
    }


    private fun waitForInstructions() {
        Log.i("TAG", "Waiting for instructions...")
        runOnUiThread {
            save_button.isEnabled = true
            goto_button.isEnabled = true
        }
    }




    override fun onRobotFocusGained(qiContext: QiContext?) {
        this.qiContext = qiContext
        runOnUiThread {
            startMappingButton.isEnabled = true
        }
        actuation = qiContext!!.actuation
        mapping = qiContext.mapping

    }

    override fun onResume() {
        super.onResume()
        // Reset UI and variables state.
        startMappingButton.isEnabled = false
        extendMapButton.isEnabled = false
        initialExplorationMap = null
    }

    override fun onRobotFocusLost() {
        qiContext = null

    }


    override fun onRobotFocusRefused(reason: String?) {

    }

    private fun mapSurroundings(qiContext: QiContext): Future<ExplorationMap> {
        // Create a Promise to set the operation state later.
        val promise = Promise<ExplorationMap>().apply {
            // If something tries to cancel the associated Future, do cancel it.
            setOnCancel {
                if (!it.future.isDone) {
                    setCancelled()
                }
            }
        }

        // Create a LocalizeAndMap, run it, and keep the Future.
        val localizeAndMapFuture = LocalizeAndMapBuilder.with(qiContext)
            .buildAsync()
            .andThenCompose { localizeAndMap ->
                // Add an OnStatusChangedListener to know when the robot is localized.
                localizeAndMap.addOnStatusChangedListener { status ->
                    if (status == LocalizationStatus.LOCALIZED) {
                        // Retrieve the map.
                        val explorationMap = localizeAndMap.dumpMap()
                        // Set the Promise state in success, with the ExplorationMap.
                        if (!promise.future.isDone) {
                            promise.setValue(explorationMap)
                        }
                    }
                }

                // Run the LocalizeAndMap.
                localizeAndMap.async().run()

                    .thenConsume {
                    // Remove the OnStatusChangedListener.
                    localizeAndMap.removeAllOnStatusChangedListeners()
                    // In case of error, forward it to the Promise.
                    if (it.hasError() && !promise.future.isDone) {
                        promise.setError(it.errorMessage)
                    }
                }
            }

        // Return the Future associated to the Promise.
        return promise.future.thenCompose {
            // Stop the LocalizeAndMap.
            localizeAndMapFuture.cancel(true)
            return@thenCompose it
        }
    }




    private fun mapToBitmap(explorationMap: ExplorationMap) {
        explorationMapView.setExplorationMap(explorationMap.topGraphicalRepresentation)
    }


    private fun startMappingStep(qiContext: QiContext) {
        Log.i(TAG.toString(), "startMappingStep Class")
        startMappingButton.isEnabled = false
        // Map the surroundings and get the map.
        mapSurroundings(qiContext).thenConsume { future ->
            if (future.isSuccess) {
                Log.i(TAG, "FUTURE Success")
                val explorationMap = future.get()
                // Store the initial map.
                this.initialExplorationMap = explorationMap
                // Convert the map to a bitmap.
                mapToBitmap(explorationMap)
                // Display the bitmap and enable "extend map" button.
                runOnUiThread {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        extendMapButton.isEnabled = true
                    }
                }
            } else {
                // If the operation is not a success, re-enable "start mapping" button.
                runOnUiThread {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        startMappingButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun publishExplorationMap(
        localizeAndMap: LocalizeAndMap,
        updatedMapCallback: (ExplorationMap) -> Unit
    ): Future<Void> {
        Log.i(TAG.toString(), "Reecursively Function")
            return localizeAndMap.async().dumpMap().andThenCompose {
                Log.i(TAG, "$it Function")
                updatedMapCallback(it)
                FutureUtils.wait(1, TimeUnit.SECONDS)
            }.andThenCompose {
                publishExplorationMap(localizeAndMap, updatedMapCallback)
            }
    }
    private fun startMapExtensionStep(initialExplorationMap: ExplorationMap, qiContext: QiContext) {
        Log.i(TAG.toString(), "StartMapEXTENSION Class")
        extendMapButton.isEnabled = false
        holdAbilities(qiContext)
        extendMap(initialExplorationMap, qiContext) { updatedMap ->
            explorationMapView.setExplorationMap(initialExplorationMap.topGraphicalRepresentation)
            mapToBitmap(updatedMap)
            // Display the bitmap.
            runOnUiThread {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    robotOnMap(initialExplorationMap, qiContext)
                }
            }
        }.thenConsume { future ->
            // If the operation is not a success, re-enable "extend map" button.
            if (!future.isSuccess) {
                runOnUiThread {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        extendMapButton.isEnabled = true
                    }
                }
            }
        }
    }



    private fun robotOnMap(initialExplorationMap: ExplorationMap, qiContext: QiContext){
        Log.i(TAG.toString(), "$initialExplorationMap Class")

    }
    private fun holdAbilities(qiContext: QiContext) {
        // Build and store the holder for the abilities.
            holder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(
                AutonomousAbilitiesType.BACKGROUND_MOVEMENT,
                AutonomousAbilitiesType.BASIC_AWARENESS,
                AutonomousAbilitiesType.AUTONOMOUS_BLINKING
            )
            .build()

        // Hold the abilities asynchronously.
        val holdFuture: Future<Void> = holder!!.async().hold()
    }

    private fun releaseAbilities(holder: Holder) {
        // Release the holder asynchronously.
        val releaseFuture: Future<Void> = holder.async().release()
    }

    private fun extendMap(
        explorationMap: ExplorationMap,
        qiContext: QiContext,
        updatedMapCallback: (ExplorationMap) -> Unit
    ): Future<Void> {
        Log.i(TAG.toString(), "ExtandMap Class")
        val promise = Promise<Void>().apply {
            // If something tries to cancel the associated Future, do cancel it.
            setOnCancel {
                if (!it.future.isDone) {
                    setCancelled()
                }
            }
        }

        // Create a LocalizeAndMap with the initial map, run it, and keep the Future.
        val localizeAndMapFuture = LocalizeAndMapBuilder.with(qiContext)
            .withMap(explorationMap)
            .buildAsync()
            .andThenCompose { localizeAndMap ->
                Log.i(TAG.toString(), "localizeandmap Class")

                // Add an OnStatusChangedListener to know when the robot is localized.
                localizeAndMap.addOnStatusChangedListener { status ->
                    if (status == LocalizationStatus.LOCALIZED) {
                        // Start the map notification process.
                        publishExplorationMapFuture = publishExplorationMap(
                            localizeAndMap,
                            updatedMapCallback
                        )
                    }
                }

                // Run the LocalizeAndMap.
                localizeAndMap.async().run()
                    .thenConsume {
                    // Remove the OnStatusChangedListener.
                    localizeAndMap.removeAllOnStatusChangedListeners()
                    // Stop the map notification process.
                    publishExplorationMapFuture?.cancel(true)
                    // In case of error, forward it to the Promise.
                    if (it.hasError() && !promise.future.isDone) {
                        promise.setError(it.errorMessage)
                    }
                }
            }

        // Return the Future associated to the Promise.
        return promise.future.thenCompose {
            // Stop the LocalizeAndMap.
            localizeAndMapFuture.cancel(true)
            return@thenCompose it
        }
    }

}







