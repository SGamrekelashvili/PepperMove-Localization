package ge.gis.tbcbank

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock

import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.builder.*
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoTo
import kotlinx.android.synthetic.main.activity_main.*
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import kotlinx.android.synthetic.main.activity_localization.*
import java.util.concurrent.CancellationException


class Localization : AppCompatActivity(), RobotLifecycleCallbacks {
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var selectedLocation: String? = null
    private val savedLocations = mutableMapOf<String, FreeFrame>()
    private var qiContext: QiContext? = null
    private var actuation: Actuation? = null
    private var mapping: Mapping? = null
    private var goTo: StubbornGoTo? = null
    private var goToFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        QiSDK.register(this,this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_localization)
//        save_button.setOnClickListener {
//            val location: String = add_item_edit.text.toString()
//            add_item_edit.text.clear()
//            // Save location only if new.
//            if (location.isNotEmpty() && !savedLocations.containsKey(location)) {
//                spinnerAdapter.add(location)
//                saveLocation(location)
//            }
//        }
//
//        goto_button.setOnClickListener {
//            selectedLocation?.let {
//                goto_button.isEnabled = false
//                save_button.isEnabled = false
//                val thread = Thread{
//                    goToLocation(it)
//                }
//                thread.start()
//            }
//        }
//
//        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
//                selectedLocation = parent.getItemAtPosition(position) as String
//                Log.i("TAG", "onItemSelected: $selectedLocation")
//            }
//
//            override fun onNothingSelected(parent: AdapterView<*>) {
//                selectedLocation = null
//                Log.i("TAG", "onNothingSelected")
//            }
//        }
//
//        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ArrayList())
//        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        spinner.adapter = spinnerAdapter
    }
//
//    fun saveLocation(location: String) {
//        val robotFrameFuture = actuation?.async()?.robotFrame()
//        robotFrameFuture?.andThenConsume { robotFrame ->
//            val locationFrame: FreeFrame? = mapping?.makeFreeFrame()
//            val transform: Transform = TransformBuilder.create().fromXTranslation(0.0)
//            locationFrame!!.update(robotFrame, transform, 0L)
//
//            savedLocations.put(location, locationFrame!!);
//        }
//    }
//
//    fun goToLocation(location: String) {
//        val freeFrame: FreeFrame? = savedLocations[location]
//        val frameFuture: Frame = freeFrame!!.frame()
//            val goto = StubbornGoToBuilder.with(qiContext!!)
//                .withFinalOrientationPolicy(OrientationPolicy.UNSUPPORTED_VALUE)
//                .withMaxRetry(20)
//                .withMaxSpeed(0.5f)
//                .withMaxDistanceFromTargetFrame(0.3)
//                .withWalkingAnimationEnabled(true)
//                .withFrame(frameFuture).build()
//            goto.run()
//        waitForInstructions()
//        }
//
//
//    fun waitForInstructions() {
//        Log.i("TAG", "Waiting for instructions...")
//        runOnUiThread {
//            save_button.isEnabled = true
//            goto_button.isEnabled = true
//        }
//    }
//

    override fun onRobotFocusGained(qiContext: QiContext?) {
        this.qiContext = qiContext
//        waitForInstructions()
    }
//
    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {

    }
}