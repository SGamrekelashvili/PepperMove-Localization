package ge.gis.tbcbank

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
import com.aldebaran.qi.sdk.`object`.conversation.Phrase
import com.aldebaran.qi.sdk.`object`.conversation.Say
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.aldebaran.qi.sdk.builder.LocalizeBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoTo
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import kotlinx.android.synthetic.main.activity_localization.*
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : RobotActivity(), RobotLifecycleCallbacks {
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var selectedLocation: String? = null
    private val savedLocations = mutableMapOf<String, FreeFrame>()
    private var qiContext: QiContext? = null
    var explorationMap :ExplorationMap? = null
    var localizeAndMapFuture: Future<Void>? = null
    var localizeAndMap: LocalizeAndMap? =null
    private var goTo: StubbornGoTo? = null
    private var goToFuture: Future<Void>? = null
    private var actuation: Actuation? = null
    private var mapping: Mapping? = null
    var i:Int=0

    override fun onCreate(savedInstanceState: Bundle?) {
        QiSDK.register(this, this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        save_button.setOnClickListener {
            val location: String = add_item_edit.text.toString()
            add_item_edit.text.clear()
            // Save location only if new.
            if (location.isNotEmpty() && !savedLocations.containsKey(location)) {
                spinnerAdapter.add(location)
                saveLocation(location)
            }
        }

        goto_button.setOnClickListener {
            selectedLocation?.let {
                goto_button.isEnabled = false
                save_button.isEnabled = false
                val thread = Thread{
                    goToLocation(it)
                }
                thread.start()
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
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
        button.setOnClickListener {
            Thread {
                localizeAndMapFuture!!.requestCancellation()
                explorationMap = localizeAndMap!!.dumpMap()
                Log.i("explorationMap","explorationMap $explorationMap")
                Localization(explorationMap!!)
            }.start()
        }
    }



    fun saveLocation(location: String) {
        actuation = qiContext!!.actuation
        mapping = qiContext!!.mapping
        val robotFrameFuture = actuation?.async()?.robotFrame()
        robotFrameFuture?.andThenConsume { robotFrame ->
            val locationFrame: FreeFrame? = mapping?.makeFreeFrame()
            val transform: Transform = TransformBuilder.create().fromXTranslation(0.0)
            locationFrame!!.update(robotFrame, transform, 0L)
            Log.i("SaveLocation","Location $location and Frame $locationFrame")
            savedLocations.put(location, locationFrame);
        }
    }

    fun goToLocation(location: String) {
        val freeFrame: FreeFrame? = savedLocations[location]
        val frameFuture: Frame = freeFrame!!.frame()
        val goto = StubbornGoToBuilder.with(qiContext!!)
            .withFinalOrientationPolicy(OrientationPolicy.FREE_ORIENTATION)
            .withMaxRetry(20)
            .withMaxSpeed(0.5f)
            .withMaxDistanceFromTargetFrame(0.3)
            .withWalkingAnimationEnabled(true)
            .withFrame(frameFuture).build()
        goto.run()
        waitForInstructions()
    }


    fun waitForInstructions() {
        Log.i("TAG", "Waiting for instructions...")
        runOnUiThread {
            save_button.isEnabled = true
            goto_button.isEnabled = true
        }
    }


    fun LocalizeAndMap(){
            localizeAndMap = LocalizeAndMapBuilder.with(qiContext).build()
            localizeAndMapFuture = localizeAndMap!!.async().run()
    }

    fun Localization(explorationMap :ExplorationMap){
        runOnUiThread {
            save_button.isEnabled = false
            goto_button.isEnabled = false
        }
        Log.i("Localization", "Localization $explorationMap Started!!!!")
        val localize = LocalizeBuilder.with(qiContext).withMap(explorationMap).build()
        localize.async().run()
        localize.run {
            Log.i("Localization", "Localize $localize ENDED!!!!")
        }
        waitForInstructions()
    }
    override fun onRobotFocusGained(qiContext: QiContext?) {
        this.qiContext=qiContext
        actuation = qiContext!!.actuation
        mapping = qiContext.mapping
        val phrase: Phrase = Phrase("გამარჯობა")

// Build the action.
        val say: Say = SayBuilder.with(qiContext)
            .withPhrase(phrase).withLocale(Locale(Language.GEORGIAN, Region.GEORGIA))
            .build()
        Log.i("LOCATE",(Language.GEORGIAN.toString()))
        say.run()
        LocalizeAndMap()
    }


    override fun onRobotFocusLost() {
        qiContext = null

    }


    override fun onRobotFocusRefused(reason: String?) {

    }
}







