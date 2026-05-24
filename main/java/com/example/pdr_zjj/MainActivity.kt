package com.example.pdr_zjj
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pdr_zjj.core.PdrConfig
import android.annotation.SuppressLint
import android.view.View
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView 
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.pdr_zjj.data.location.GpsCollector
import com.example.pdr_zjj.data.model.GpsEnuSample
import com.example.pdr_zjj.data.model.GpsSample
import com.example.pdr_zjj.data.model.PdrTrackPoint
import com.example.pdr_zjj.data.model.SensorSample
import com.example.pdr_zjj.data.sensor.SensorCollector
import com.example.pdr_zjj.data.storage.GpsEnuWriter
import com.example.pdr_zjj.data.storage.GpsTruthReader
import com.example.pdr_zjj.data.storage.GpsTruthWriter
import com.example.pdr_zjj.data.storage.RawDataWriter
import com.example.pdr_zjj.data.storage.SessionManager
import com.example.pdr_zjj.data.model.SensorType
import com.example.pdr_zjj.preprocess.core.PreprocessEngine
import com.example.pdr_zjj.preprocess.model.SyncedSensorFrame
import java.io.File
import java.util.Locale
import com.example.pdr_zjj.init.InitManager
import com.example.pdr_zjj.ahrs.Ahrs6DofMahony
import com.example.pdr_zjj.ahrs.IAhrsFilter
import com.example.pdr_zjj.ahrs.AhrsMode
import com.example.pdr_zjj.ahrs.Ahrs9DofMahony
import com.example.pdr_zjj.step.StepDetector
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.pdr_zjj.step.StepLengthEstimator
import com.example.pdr_zjj.position.PositionUpdater
import com.example.pdr_zjj.step.InterruptDetector
import com.example.pdr_zjj.position.HeadingCorrector
import com.example.pdr_zjj.position.GeoUtils
import com.example.pdr_zjj.UI.TrackView
import com.example.pdr_zjj.mode.CarryMode
import com.example.pdr_zjj.mode.PdrMode
import com.example.pdr_zjj.core.RealtimePdrEngine
import com.example.pdr_zjj.core.OfflinePdrProcessor
import com.example.pdr_zjj.core.OfflinePdrResult
import com.example.pdr_zjj.core.RealtimePdrResult
import com.example.pdr_zjj.core.RealtimeStepResult
import com.example.pdr_zjj.core.TrackComparisonMetrics
import com.example.pdr_zjj.core.TrackComparisonEvaluator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.OpenableColumns
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.example.pdr_zjj.data.storage.GpsEnuReader
import com.example.pdr_zjj.data.storage.PdrResultWriter
import com.example.pdr_zjj.data.storage.PdrResultReader
import com.example.pdr_zjj.data.storage.PdrParameterStore
import com.example.pdr_zjj.data.storage.PublicExportManager
import com.example.pdr_zjj.data.storage.TrackComparisonWriter
import kotlin.math.hypot



class MainActivity : AppCompatActivity() {


    private lateinit var tvMode: TextView
    private lateinit var tvInitState: TextView
    private lateinit var tvAhrs: TextView
    private lateinit var tvStepCount: TextView
    private lateinit var tvPdrDistance: TextView
    private lateinit var tvClosureError: TextView
    private lateinit var tvHeading: TextView
    private lateinit var tvStepLength: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvInterrupt: TextView


    private lateinit var sessionNameEdit: EditText
    private lateinit var startButton: TextView
    private lateinit var offlineButton: TextView
    private lateinit var settingsButton: TextView
    private lateinit var offlinePanel: View
    private lateinit var offlineHintText: TextView
    private lateinit var selectOfflineSensorButton: TextView
    private lateinit var offlineSensorPathText: TextView
    private lateinit var selectOfflineGpsButton: TextView
    private lateinit var offlineGpsPathText: TextView
    private lateinit var startOfflineProcessButton: TextView

    private lateinit var sessionManager: SessionManager
    private lateinit var publicExportManager: PublicExportManager
    private lateinit var parameterStore: PdrParameterStore
    private lateinit var sensorCollector: SensorCollector
    private lateinit var gpsCollector: GpsCollector

    private lateinit var coverLayout: View

    private var rawDataWriter: RawDataWriter? = null
    private var gpsTruthWriter: GpsTruthWriter? = null
    private var gpsEnuWriter: GpsEnuWriter? = null
    private var currentSessionDir: File? = null
    private var gpsOriginSample: GpsSample? = null

    private var isCollecting = false

    private var currentMode: PdrMode = PdrMode.REALTIME

    private val handler = Handler(Looper.getMainLooper())


    private lateinit var initManager: com.example.pdr_zjj.init.InitManager

    private val accInitBuffer = mutableListOf<FloatArray>()
    private val magInitBuffer = mutableListOf<FloatArray>()

    private var isInitializing = false
    private var isInitialized = false

    private var initRollRad = 0.0
    private var initPitchRad = 0.0
    private var initHeadingRad = 0.0

    private var initLatDeg: Double? = null
    private var initLonDeg: Double? = null

    private lateinit var preprocessEngine: PreprocessEngine


    private var latestSyncedTimeSec = 0.0
    private var latestSyncedAccX = 0.0
    private var latestSyncedAccY = 0.0
    private var latestSyncedAccZ = 0.0
    private var latestSyncedGyroX = 0.0
    private var latestSyncedGyroY = 0.0
    private var latestSyncedGyroZ = 0.0
    private var latestSyncedMagX = 0.0
    private var latestSyncedMagY = 0.0
    private var latestSyncedMagZ = 0.0

    private lateinit var ahrsFilter: IAhrsFilter
    private var currentAhrsMode = AhrsMode.DOF9

    private lateinit var radioGroupAhrsMode: RadioGroup
    private lateinit var radio6dof: RadioButton
    private lateinit var radio9dof: RadioButton
    private lateinit var radioGroupCarryMode: RadioGroup
    private lateinit var radioHandheld: RadioButton
    private lateinit var radioPocket: RadioButton



    private lateinit var stepDetector: StepDetector


    private lateinit var stepLengthEstimator: StepLengthEstimator



    private lateinit var positionUpdater: PositionUpdater


    private lateinit var interruptDetector: InterruptDetector
    private lateinit var headingCorrector: HeadingCorrector


    private lateinit var trackView: TrackView

    private var realtimeEngine: RealtimePdrEngine? = null
    private var offlineProcessor: OfflinePdrProcessor? = null

    private lateinit var currentConfig: PdrConfig

    private var latestRealtimeResult: RealtimePdrResult? = null
    private var latestOfflineResult: OfflinePdrResult? = null
    private var latestOfflineGpsTruthEnu = emptyList<GpsEnuSample>()

    private var selectedOfflineSensorUri: Uri? = null
    private var selectedOfflineSensorDisplayName: String? = null
    private var selectedOfflineGpsUri: Uri? = null
    private var selectedOfflineGpsDisplayName: String? = null
    private var lastSettingsSignature: String = ""

    private var pdrResultWriter: PdrResultWriter? = null
    private val realtimeStepHistory = mutableListOf<RealtimeStepResult>()
    private var currentCarryMode: CarryMode = CarryMode.HANDHELD

    private data class OfflineInputSelection(
        val rawSensorFile: File,
        val rawSensorDisplayName: String?,
        val gpsTruthEnuSamples: List<GpsEnuSample>
    )

    private val offlineSensorFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                selectedOfflineSensorUri = uri
                selectedOfflineSensorDisplayName = getSourceDisplayName(uri)
                offlineSensorPathText.text = buildSelectedFileText("传感器文件", selectedOfflineSensorDisplayName, uri)
                Toast.makeText(this, "已选择传感器文件", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未更改传感器文件", Toast.LENGTH_SHORT).show()
            }
        }

    private val offlineGpsFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                selectedOfflineGpsUri = uri
                selectedOfflineGpsDisplayName = getSourceDisplayName(uri)
                offlineGpsPathText.text = buildSelectedFileText("GPS 文件", selectedOfflineGpsDisplayName, uri)
                Toast.makeText(this, "已选择 GPS 真值文件", Toast.LENGTH_SHORT).show()
            } else {
                selectedOfflineGpsUri = null
                selectedOfflineGpsDisplayName = null
                offlineGpsPathText.text = "GPS 文件：未选择（可选）"
                Toast.makeText(this, "未选择 GPS 真值文件，将只显示 PDR", Toast.LENGTH_SHORT).show()
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                Toast.makeText(this, "定位权限已开启，可记录 GPS 真值", Toast.LENGTH_SHORT).show()
                if (isCollecting) {
                    gpsCollector.resetSessionTime()
                    gpsCollector.start()
                }
            } else {
                Toast.makeText(this, "未授予定位权限，无法记录 GPS 真值", Toast.LENGTH_LONG).show()
            }
        }



    private val uiRunnable = object : Runnable {
        override fun run() {
            updateUiText()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main_clean)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sessionManager = SessionManager(this)
        publicExportManager = PublicExportManager(this)
        parameterStore = PdrParameterStore(this)

        currentConfig = parameterStore.buildRealtimeConfig(currentAhrsMode)
        lastSettingsSignature = buildSettingsSignature()

        buildRealtimeComponents(currentConfig)


        initView()
        initCollector()
        initGpsCollector()
        handler.postDelayed({
            coverLayout.visibility = View.GONE
        }, 2200)

        handler.post(uiRunnable)
    }

    override fun onResume() {
        super.onResume()
        refreshConfigFromSettings()
    }

    private fun refreshConfigFromSettings() {
        val newSignature = buildSettingsSignature()
        if (newSignature == lastSettingsSignature) return

        currentConfig = parameterStore.buildRealtimeConfig(currentAhrsMode)
        lastSettingsSignature = newSignature

        if (isCollecting) {
            Toast.makeText(this, "参数已更新，停止并重新开始采集后生效", Toast.LENGTH_SHORT).show()
            return
        }

        buildRealtimeComponents(currentConfig)
    }

    private fun buildSettingsSignature(): String {
        val realtime6 = parameterStore.getRealtimeParameters(AhrsMode.DOF6)
        val realtime9 = parameterStore.getRealtimeParameters(AhrsMode.DOF9)
        val offline6 = parameterStore.getOfflineParameters(AhrsMode.DOF6)
        val offline9 = parameterStore.getOfflineParameters(AhrsMode.DOF9)
        return listOf(
            parameterStore.getHeightMeter(),
            parameterStore.getDeclinationDeg(),
            realtime6.baseLengthFactor,
            realtime6.cadenceFactor,
            realtime6.amplitudeFactor,
            realtime6.scaleFactor,
            realtime9.baseLengthFactor,
            realtime9.cadenceFactor,
            realtime9.amplitudeFactor,
            realtime9.scaleFactor,
            offline6.baseLengthFactor,
            offline6.cadenceFactor,
            offline6.amplitudeFactor,
            offline6.scaleFactor,
            offline9.baseLengthFactor,
            offline9.cadenceFactor,
            offline9.amplitudeFactor,
            offline9.scaleFactor
        ).joinToString("|")
    }

    private fun initView() {
        coverLayout = findViewById(R.id.coverLayout)

        sessionNameEdit = findViewById(R.id.etSessionName)

        radioGroupAhrsMode = findViewById(R.id.rgAhrsMode)
        radio6dof = findViewById(R.id.rb6Axis)
        radio9dof = findViewById(R.id.rb9Axis)
        radioGroupCarryMode = findViewById(requireViewId("rgCarryMode"))
        radioHandheld = findViewById(requireViewId("rbHandheld"))
        radioPocket = findViewById(requireViewId("rbPocket"))

        startButton = findViewById(R.id.btnRealtime)
        offlineButton = findViewById(R.id.btnOffline)
        settingsButton = findViewById(R.id.btnSettings)
        offlinePanel = findViewById(R.id.offlinePanel)
        offlineHintText = findViewById(R.id.tvOfflineHint)
        selectOfflineSensorButton = findViewById(R.id.btnSelectOfflineSensor)
        offlineSensorPathText = findViewById(R.id.tvOfflineSensorPath)
        selectOfflineGpsButton = findViewById(R.id.btnSelectOfflineGps)
        offlineGpsPathText = findViewById(R.id.tvOfflineGpsPath)
        startOfflineProcessButton = findViewById(R.id.btnStartOfflineProcess)

        trackView = findViewById(R.id.trackView)

        tvMode = findViewById(R.id.tvMode)
        tvInitState = findViewById(R.id.tvInitState)
        tvAhrs = findViewById(R.id.tvAhrs)
        tvStepCount = findViewById(R.id.tvStepCount)
        tvPdrDistance = findViewById(R.id.tvPdrDistance)
        tvClosureError = findViewById(R.id.tvClosureError)
        tvHeading = findViewById(R.id.tvHeading)
        tvStepLength = findViewById(R.id.tvStepLength)
        tvPosition = findViewById(R.id.tvPosition)
        tvTime = findViewById(R.id.tvTime)
        tvInterrupt = findViewById(R.id.tvInterrupt)

        radioGroupAhrsMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb6Axis -> switchAhrsMode(AhrsMode.DOF6)
                R.id.rb9Axis -> switchAhrsMode(AhrsMode.DOF9)
            }
        }

        radioGroupCarryMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                requireViewId("rbHandheld") -> switchCarryMode(CarryMode.HANDHELD)
                requireViewId("rbPocket") -> switchCarryMode(CarryMode.POCKET)
            }
        }

        startButton.setOnClickListener {
            toggleCollection()
        }

        offlineButton.setOnClickListener {
            toggleOfflinePanel()
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        selectOfflineSensorButton.setOnClickListener {
            openOfflineSensorFilePicker()
        }

        selectOfflineGpsButton.setOnClickListener {
            openOfflineGpsFilePicker()
        }

        startOfflineProcessButton.setOnClickListener {
            startOfflineProcessing()
        }


        sessionNameEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sessionNameEdit.clearFocus()

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(sessionNameEdit.windowToken, 0)

                true
            } else {
                false
            }
        }
    }

    private fun initCollector() {
        sensorCollector = SensorCollector(
            context = this,
            onSampleCollected = ::handleSample,
            onSnapshotUpdated = { _ -> }
        )
        sensorCollector.start()
    }

    private fun initGpsCollector() {
        gpsCollector = GpsCollector(this) { sample ->
            gpsTruthWriter?.write(sample)
            gpsTruthWriter?.flush()

            val origin = gpsOriginSample ?: sample.also {
                gpsOriginSample = it
            }
            val enuSample = GeoUtils.toLocalEnu(
                sample = sample,
                originLatitudeDeg = origin.latitudeDeg,
                originLongitudeDeg = origin.longitudeDeg
            )
            gpsEnuWriter?.write(enuSample)
            gpsEnuWriter?.flush()
            trackView.addGpsPoint(enuSample.eastMeter, enuSample.northMeter)
        }
    }

    private fun handleSample(sample: SensorSample) {
        if (!isCollecting) return

        rawDataWriter?.write(sample)

        if (isInitializing && !isInitialized) {
            when (sample.sensorType) {
                SensorType.ACCELEROMETER -> {
                    accInitBuffer.add(
                        floatArrayOf(
                            sample.x.toFloat(),
                            sample.y.toFloat(),
                            sample.z.toFloat()
                        )
                    )
                }

                SensorType.MAGNETOMETER -> {
                    magInitBuffer.add(
                        floatArrayOf(
                            sample.x.toFloat(),
                            sample.y.toFloat(),
                            sample.z.toFloat()
                        )
                    )
                }

                SensorType.GYROSCOPE -> {
                }
            }

            tryFinishInitialization()
        }

        val frame = preprocessEngine.accept(sample)
        if (frame != null) {
            handleSyncedFrame(frame)
        }
    }



    private fun handleSyncedFrame(frame: SyncedSensorFrame) {
        if (currentMode != PdrMode.REALTIME) return

        latestSyncedTimeSec = frame.relativeTimeSec

        latestSyncedAccX = frame.accX
        latestSyncedAccY = frame.accY
        latestSyncedAccZ = frame.accZ

        latestSyncedGyroX = frame.gyroX
        latestSyncedGyroY = frame.gyroY
        latestSyncedGyroZ = frame.gyroZ

        latestSyncedMagX = frame.magX
        latestSyncedMagY = frame.magY
        latestSyncedMagZ = frame.magZ

        android.util.Log.d(
            "PDR_FRAME",
            "t=${frame.relativeTimeSec}, init=$isInitialized, accZ=${frame.accZ}, gyroZ=${frame.gyroZ}"
        )

        val result = realtimeEngine?.processFrame(
            frame = frame,
            isInitialized = isInitialized
        ) ?: return

        latestRealtimeResult = result

        android.util.Log.d(
            "PDR_STEP",
            "stepResult=${result.stepResult}"
        )

        if (result.stepResult != null) {
            val step = result.stepResult
            val headingDeg = Math.toDegrees(step.yawRad)
            realtimeStepHistory.add(step)

            pdrResultWriter?.writeStep(
                stepIndex = step.stepIndex,
                timeSec = step.stepTimeSec,
                stepLengthMeter = step.stepLengthMeter,
                headingDeg = headingDeg,
                eastMeter = step.eastMeter,
                northMeter = step.northMeter
            )
            pdrResultWriter?.flush()

            renderRealtimeStateOnStep()
        }
    }

    private fun createAhrsFilter(mode: AhrsMode): IAhrsFilter {
        return when (mode) {
            AhrsMode.DOF6 -> {
                Ahrs6DofMahony(
                    kp = 1.0,
                    ki = 0.015
                )
            }

            AhrsMode.DOF9 -> {
                Ahrs9DofMahony(
                    kpAcc = 0.9,
                    kiAcc = 0.015,
                    kpMag = 0.35
                )
            }
        }
    }

    private fun switchAhrsMode(newMode: AhrsMode) {
        if (currentAhrsMode == newMode) return

        if (isCollecting) {
            stopRealtimeMode()
            latestRealtimeResult = null
            isInitializing = false
            isInitialized = false
        }

        currentAhrsMode = newMode

        currentConfig = parameterStore.buildRealtimeConfig(currentAhrsMode)
        lastSettingsSignature = buildSettingsSignature()

        buildRealtimeComponents(currentConfig)

        if (isInitialized) {
            realtimeEngine?.applyInitialization(
                rollRad = initRollRad,
                pitchRad = initPitchRad,
                headingRad = initHeadingRad
            )

        }

        val modeLabel = if (newMode == AhrsMode.DOF6) "6 轴" else "9 轴"
        val msg = if (isCollecting) {
            "已切换为 ${modeLabel}，采集已停止，请重新开始并完成初始化"
        } else {
            "已切换为 ${modeLabel}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun toggleCollection() {
        if (!isCollecting) {
            startRealtimeMode()
        } else {
            stopRealtimeMode()
        }
    }

    private fun startRealtimeMode() {

        currentMode = PdrMode.REALTIME
        val customName = sessionNameEdit.text?.toString()?.trim()
        currentSessionDir = sessionManager.createSessionDir(customName)
        val rawFile = sessionManager.createRawSensorFile(currentSessionDir!!)

        rawDataWriter = RawDataWriter(rawFile).also {
            it.open(clearIfExists = true)
        }
        val gpsTruthFile = sessionManager.createGpsTruthFile(currentSessionDir!!)
        gpsTruthWriter = GpsTruthWriter(gpsTruthFile).also {
            it.open(clearIfExists = true)
        }
        val gpsTruthEnuFile = sessionManager.createGpsTruthEnuFile(currentSessionDir!!)
        gpsEnuWriter = GpsEnuWriter(gpsTruthEnuFile).also {
            it.open(clearIfExists = true)
        }
        val resultFile = sessionManager.createRealtimePdrResultFile(currentSessionDir!!)
        pdrResultWriter = PdrResultWriter(resultFile).also {
            it.open(clearIfExists = true)
        }

        sensorCollector.resetSessionTime()
        gpsCollector.resetSessionTime()
        gpsOriginSample = null

        preprocessEngine.reset()
        realtimeEngine?.reset()
        trackView.reset()



        latestRealtimeResult = null
        latestOfflineResult = null
        realtimeStepHistory.clear()
        trackView.addPoint(0.0, 0.0)


        startInitialization()

        isCollecting = true
        startGpsTruthCollection()
        startButton.text = "停止采集"

        Toast.makeText(this, "开始采集，请先保持静止几秒完成初始化", Toast.LENGTH_SHORT).show()
    }

    private fun stopRealtimeMode() {
        isCollecting = false
        isInitializing = false

        gpsCollector.stop()

        rawDataWriter?.flush()
        rawDataWriter?.close()
        rawDataWriter = null

        gpsTruthWriter?.flush()
        gpsTruthWriter?.close()
        gpsTruthWriter = null

        gpsEnuWriter?.flush()
        gpsEnuWriter?.close()
        gpsEnuWriter = null

        pdrResultWriter?.flush()
        pdrResultWriter?.close()
        pdrResultWriter = null

        val trackMetrics = writeTrackComparisonSummary()
        saveCurrentTrackImage()

        exportCurrentSessionToPublicDownloads()

        startButton.text = "开始采集"

        showPdrSummaryToast(
            prefix = "实时处理完成",
            stepCount = realtimeStepHistory.size,
            pathLengthMeter = computeRealtimePathLengthMeter(),
            closureErrorMeter = computeRealtimeClosureErrorMeter(),
            comparisonMetrics = trackMetrics
        )
    }

    private fun writeTrackComparisonSummary(): TrackComparisonMetrics? {
        val sessionDir = currentSessionDir ?: return null
        val pdrFile = sessionManager.createRealtimePdrResultFile(sessionDir)
        val gpsEnuFile = sessionManager.createGpsTruthEnuFile(sessionDir)

        val pdrPoints = PdrResultReader().read(pdrFile)
        val gpsPoints = GpsEnuReader().read(gpsEnuFile)
        val metrics = TrackComparisonEvaluator().evaluate(pdrPoints, gpsPoints) ?: return null

        val summaryFile = sessionManager.createTrackComparisonFile(sessionDir)
        TrackComparisonWriter().writeSummary(summaryFile, metrics)
        return metrics
    }

    private fun startGpsTruthCollection() {
        if (hasLocationPermission()) {
            gpsCollector.start()
            return
        }

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun exportCurrentSessionToPublicDownloads() {
        val sessionDir = currentSessionDir ?: return
        val exportResult = publicExportManager.exportSession(sessionDir)
        if (!exportResult.success) return

        val exportPath = exportResult.relativePath ?: "Download/PDR_Zjj/${sessionDir.name}"
        Toast.makeText(
            this,
            "已导出 ${exportResult.exportedCount} 个文件到 $exportPath",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun renderRealtimeStateOnStep() {
        val step = latestRealtimeResult?.stepResult ?: return
        trackView.addPoint(step.eastMeter, step.northMeter)
    }


    private fun toggleOfflinePanel() {
        offlinePanel.visibility = if (offlinePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (offlinePanel.visibility == View.VISIBLE) {
            offlineHintText.text = "请先选择传感器文件，GPS 真值文件可选。全部选好后点击“开始事后处理”再生成结果。"
        }
    }

    private fun openOfflineSensorFilePicker() {
        offlineSensorFilePicker.launch(arrayOf("*/*"))
    }

    private fun openOfflineGpsFilePicker() {
        offlineGpsFilePicker.launch(arrayOf("*/*"))
    }

    private fun startOfflineProcessing() {
        val sensorUri = selectedOfflineSensorUri
        if (sensorUri == null) {
            Toast.makeText(this, "请先选择传感器文件", Toast.LENGTH_SHORT).show()
            return
        }

        handleOfflineFilesSelected(
            sensorUri = sensorUri,
            sensorDisplayName = selectedOfflineSensorDisplayName,
            gpsUri = selectedOfflineGpsUri,
            gpsDisplayName = selectedOfflineGpsDisplayName
        )
    }

    private fun handleOfflineFilesSelected(
        sensorUri: Uri,
        sensorDisplayName: String?,
        gpsUri: Uri?,
        gpsDisplayName: String?
    ) {
        currentMode = PdrMode.OFFLINE

        latestOfflineResult = null
        latestRealtimeResult = null
        realtimeStepHistory.clear()
        latestOfflineGpsTruthEnu = emptyList()

        val inputSelection = prepareOfflineInputSelection(
            sensorUri = sensorUri,
            sensorDisplayName = sensorDisplayName,
            gpsUri = gpsUri,
            gpsDisplayName = gpsDisplayName
        )
        if (inputSelection == null) {
            Toast.makeText(this, "文件读取失败，请重新选择传感器文件或 GPS 文件", Toast.LENGTH_LONG).show()
            return
        }

        val offlineConfig = parameterStore.buildOfflineConfig(currentAhrsMode)

        offlineProcessor = OfflinePdrProcessor(
            context = this,
            config = offlineConfig,
            carryMode = currentCarryMode
        )

        val result = offlineProcessor?.process(inputSelection.rawSensorFile)
        if (result == null) {
            Toast.makeText(this, "事后处理失败", Toast.LENGTH_SHORT).show()
            return
        }

        writeOfflineResult(result, inputSelection.rawSensorDisplayName)
        latestOfflineResult = result
        latestOfflineGpsTruthEnu = inputSelection.gpsTruthEnuSamples
        renderOfflineResult(result)
        val trackMetrics = writeOfflineTrackComparisonSummary(result, latestOfflineGpsTruthEnu)
        saveCurrentTrackImage()
        exportCurrentSessionToPublicDownloads()
        showPdrSummaryToast(
            prefix = "事后处理完成",
            stepCount = result.steps.size,
            pathLengthMeter = computeOfflinePathLengthMeter(),
            closureErrorMeter = computeOfflineClosureErrorMeter(),
            comparisonMetrics = trackMetrics
        )
    }

    private fun prepareOfflineInputSelection(
        sensorUri: Uri,
        sensorDisplayName: String?,
        gpsUri: Uri?,
        gpsDisplayName: String?
    ): OfflineInputSelection? {
        val rawSensorFile = copyUriToTempFile(
            uri = sensorUri,
            targetFileName = sensorDisplayName ?: "offline_raw_sensor.txt"
        ) ?: return null

        val gpsTruthEnuSamples = when {
            gpsUri == null -> {
                emptyList()
            }

            gpsDisplayName?.lowercase(Locale.US)?.contains("gps_truth_enu") == true -> {
                val gpsTruthEnuFile = copyUriToTempFile(
                    uri = gpsUri,
                    targetFileName = gpsDisplayName
                        ?: "offline_gps_truth_enu.csv"
                ) ?: return null
                GpsEnuReader().read(gpsTruthEnuFile)
            }

            else -> {
                val gpsTruthFile = copyUriToTempFile(
                    uri = gpsUri,
                    targetFileName = gpsDisplayName
                        ?: "offline_gps_truth.csv"
                ) ?: return null
                convertGpsTruthToEnu(gpsTruthFile)
            }
        }

        return OfflineInputSelection(
            rawSensorFile = rawSensorFile,
            rawSensorDisplayName = sensorDisplayName,
            gpsTruthEnuSamples = gpsTruthEnuSamples
        )
    }

    private fun copyUriToTempFile(uri: Uri, targetFileName: String): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val safeFileName = targetFileName.replace(Regex("[^A-Za-z0-9._\\-]+"), "_")
            val tempFile = File(cacheDir, safeFileName)

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun convertGpsTruthToEnu(file: File): List<GpsEnuSample> {
        val gpsSamples = GpsTruthReader().read(file)
        val origin = gpsSamples.firstOrNull() ?: return emptyList()

        return gpsSamples.map { sample ->
            GeoUtils.toLocalEnu(
                sample = sample,
                originLatitudeDeg = origin.latitudeDeg,
                originLongitudeDeg = origin.longitudeDeg
            )
        }
    }

    private fun writeOfflineResult(result: OfflinePdrResult, sourceName: String?) {
        currentSessionDir = sessionManager.createSessionDir(buildOfflineSessionName(sourceName))

        val sessionDir = currentSessionDir ?: return
        val outputFile = sessionManager.createOfflinePdrResultFile(sessionDir)
        PdrResultWriter(outputFile).also { writer ->
            writer.open(clearIfExists = true)
            for (step in result.steps) {
                writer.writeStep(
                    stepIndex = step.stepIndex,
                    timeSec = step.stepTimeSec,
                    stepLengthMeter = step.stepLengthMeter,
                    headingDeg = Math.toDegrees(step.yawRad),
                    eastMeter = step.eastMeter,
                    northMeter = step.northMeter
                )
            }
            writer.flush()
            writer.close()
        }
    }
    private fun getSourceDisplayName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
    }

    private fun buildSelectedFileText(title: String, displayName: String?, uri: Uri): String {
        val nameText = displayName ?: "未知文件"
        val pathText = uri.path ?: uri.toString()
        return "$title：$nameText\n$pathText"
    }

    private fun buildOfflineSessionName(sourceName: String?): String {
        val baseName = sourceName
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9_\\-]+"), "_")
            ?.trim('_')
            ?.takeIf { it.isNotBlank() }
            ?: "offline"
        return "${baseName}_post"
    }

    private fun renderOfflineResult(result: OfflinePdrResult) {
        latestOfflineResult = result

        trackView.reset()
        trackView.addPoint(0.0, 0.0)

        for (step in result.steps) {
            trackView.addPoint(step.eastMeter, step.northMeter)
        }

        for (gpsPoint in latestOfflineGpsTruthEnu) {
            trackView.addGpsPoint(gpsPoint.eastMeter, gpsPoint.northMeter)
        }
    }

    private fun writeOfflineTrackComparisonSummary(
        result: OfflinePdrResult,
        gpsTruthEnuSamples: List<GpsEnuSample>
    ): TrackComparisonMetrics? {
        if (gpsTruthEnuSamples.size < 2) return null

        val sessionDir = currentSessionDir ?: return null
        val pdrPoints = result.steps.map { step ->
            PdrTrackPoint(
                stepIndex = step.stepIndex,
                timeSec = step.stepTimeSec,
                eastMeter = step.eastMeter,
                northMeter = step.northMeter
            )
        }
        val metrics = TrackComparisonEvaluator().evaluate(pdrPoints, gpsTruthEnuSamples) ?: return null

        val summaryFile = sessionManager.createTrackComparisonFile(sessionDir)
        TrackComparisonWriter().writeSummary(summaryFile, metrics)
        return metrics
    }

    private fun saveCurrentTrackImage() {
        val sessionDir = currentSessionDir ?: return
        val bitmapWidth = trackView.width.takeIf { it > 0 } ?: trackView.measuredWidth
        val bitmapHeight = trackView.height.takeIf { it > 0 } ?: trackView.measuredHeight

        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            return
        }

        val outputFile = sessionManager.createTrackImageFile(sessionDir)

        try {
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            trackView.draw(canvas)

            outputFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }

            bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun buildRealtimeComponents(config: PdrConfig) {
        initManager = InitManager(
            context = this,
            declinationDeg = config.declinationDeg
        )

        preprocessEngine = PreprocessEngine(
            accSmoothWindow = config.accSmoothWindow,
            gyroSmoothWindow = config.gyroSmoothWindow,
            magSmoothWindow = config.magSmoothWindow
        )

        ahrsFilter = createAhrsFilter(config.ahrsMode)

        stepDetector = StepDetector(
            baseThreshold = config.stepBaseThreshold,
            dynamicThresholdFactor = config.stepDynamicThresholdFactor,
            minPeakToValley = config.stepMinPeakToValley,
            minStepIntervalSec = config.minStepIntervalSec
        )

        stepLengthEstimator = StepLengthEstimator(
            heightMeter = config.heightMeter,
            baseLengthFactor = config.stepBaseLengthFactor,
            cadenceFactor = config.stepCadenceFactor,
            amplitudeFactor = config.stepAmplitudeFactor,
            scaleFactor = config.stepLengthScaleFactor,
            minStepLengthMeter = config.minStepLengthMeter,
            maxStepLengthMeter = config.maxStepLengthMeter
        )

        positionUpdater = PositionUpdater()

        interruptDetector = InterruptDetector(
            interruptThresholdSec = config.interruptThresholdSec
        )

        headingCorrector = HeadingCorrector(
            declinationDeg = config.declinationDeg
        )

        realtimeEngine = RealtimePdrEngine(
            ahrsFilter = ahrsFilter,
            ahrsMode = config.ahrsMode,
            stepDetector = stepDetector,
            stepLengthEstimator = stepLengthEstimator,
            positionUpdater = positionUpdater,
            interruptDetector = interruptDetector,
            headingCorrector = headingCorrector,
            headingCorrectionBlendFactor = config.headingCorrectionBlendFactor,
            carryMode = currentCarryMode
        )
    }

    private fun requireViewId(name: String): Int {
        val id = resources.getIdentifier(name, "id", packageName)
        require(id != 0) { "Missing required view id: $name" }
        return id
    }

    private fun switchCarryMode(mode: CarryMode) {
        if (currentCarryMode == mode) return

        currentCarryMode = mode

        if (isCollecting) {
            Toast.makeText(this, "携带模式已更新，停止并重新开始采集后生效", Toast.LENGTH_SHORT).show()
            return
        }

        buildRealtimeComponents(currentConfig)
        updateUiText()
    }

    private fun getDisplayRollRad(): Double {
        return latestRealtimeResult?.rollRad ?: initRollRad
    }

    private fun getDisplayPitchRad(): Double {
        return latestRealtimeResult?.pitchRad ?: initPitchRad
    }

    private fun getDisplayYawRad(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.yawRad ?: initHeadingRad
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.yawRad ?: initHeadingRad
        }
    }

    private fun getDisplayStepCount(): Int {
        return when (currentMode) {
            PdrMode.REALTIME -> realtimeStepHistory.lastOrNull()?.stepIndex ?: 0
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.size ?: 0
        }
    }

    private fun getDisplayLastStepTimeSec(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.stepResult?.stepTimeSec ?: latestSyncedTimeSec
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.stepTimeSec ?: 0.0
        }
    }

    private fun getDisplayLastPeakValue(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.stepResult?.peakValue ?: 0.0
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.peakValue ?: 0.0
        }
    }

    private fun getDisplayStepLengthMeter(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.stepResult?.stepLengthMeter ?: 0.0
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.stepLengthMeter ?: 0.0
        }
    }

    private fun getDisplayEastMeter(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.stepResult?.eastMeter ?: 0.0
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.eastMeter ?: 0.0
        }
    }

    private fun getDisplayNorthMeter(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.stepResult?.northMeter ?: 0.0
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.northMeter ?: 0.0
        }
    }

    private fun getDisplayInterrupted(): Boolean {
        return when (currentMode) {
            PdrMode.REALTIME -> latestRealtimeResult?.stepResult?.interrupted ?: false
            PdrMode.OFFLINE -> latestOfflineResult?.steps?.lastOrNull()?.interrupted ?: false
        }
    }

    private fun getDisplayPdrDistanceMeter(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> computeRealtimePathLengthMeter()
            PdrMode.OFFLINE -> computeOfflinePathLengthMeter()
        }
    }

    private fun getDisplayClosureErrorMeter(): Double {
        return when (currentMode) {
            PdrMode.REALTIME -> computeRealtimeClosureErrorMeter()
            PdrMode.OFFLINE -> computeOfflineClosureErrorMeter()
        }
    }

    private fun computeRealtimePathLengthMeter(): Double {
        return realtimeStepHistory.sumOf { it.stepLengthMeter }
    }

    private fun computeOfflinePathLengthMeter(): Double {
        return latestOfflineResult?.steps?.sumOf { it.stepLengthMeter } ?: 0.0
    }

    private fun computeRealtimeClosureErrorMeter(): Double {
        val end = realtimeStepHistory.lastOrNull() ?: return 0.0
        return hypot(end.eastMeter, end.northMeter)
    }

    private fun computeOfflineClosureErrorMeter(): Double {
        val end = latestOfflineResult?.steps?.lastOrNull() ?: return 0.0
        return hypot(end.eastMeter, end.northMeter)
    }

    private fun showPdrSummaryToast(
        prefix: String,
        stepCount: Int,
        pathLengthMeter: Double,
        closureErrorMeter: Double,
        comparisonMetrics: TrackComparisonMetrics? = null
    ) {
        val baseText = String.format(
            Locale.US,
            "%s：步数 %d，PDR 距离 %.2f m，闭合差 %.2f m",
            prefix,
            stepCount,
            pathLengthMeter,
            closureErrorMeter
        )
        val finalText = if (comparisonMetrics != null) {
            String.format(
                Locale.US,
                "%s，终点误差 %.2f m",
                baseText,
                comparisonMetrics.endpointErrorMeter
            )
        } else {
            baseText
        }
        Toast.makeText(this, finalText, Toast.LENGTH_LONG).show()
    }





    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun updateUiText() {
        val displayYawRad = getDisplayYawRad()
        val displayStepCount = getDisplayStepCount()
        val displayPdrDistanceMeter = getDisplayPdrDistanceMeter()
        val displayClosureErrorMeter = getDisplayClosureErrorMeter()
        val displayStepLengthMeter = getDisplayStepLengthMeter()
        val displayEastMeter = getDisplayEastMeter()
        val displayNorthMeter = getDisplayNorthMeter()
        val displayInterrupted = getDisplayInterrupted()
        val displayTimeSec = getDisplayLastStepTimeSec()

        val headingDeg = Math.toDegrees(displayYawRad)

        tvMode.text = "当前模式：${if (currentMode == PdrMode.REALTIME) "实时 PDR" else "事后 PDR"}"

        tvInitState.text = when {
            isInitialized -> "初始化：已完成"
            isInitializing -> "初始化：进行中"
            else -> "初始化：等待开始"
        }

        val carryModeText = if (currentCarryMode == CarryMode.HANDHELD) "手持" else "口袋"
        tvAhrs.text = "AHRS：${if (currentAhrsMode == AhrsMode.DOF6) "6 轴" else "9 轴"}｜携带：$carryModeText"

        tvStepCount.text = "步数：$displayStepCount"
        tvPdrDistance.text = String.format(Locale.US, "PDR 行进距离：%.2f m", displayPdrDistanceMeter)
        tvClosureError.text = String.format(Locale.US, "闭合差：%.2f m", displayClosureErrorMeter)
        tvHeading.text = String.format(Locale.US, "航向：%.1f°", headingDeg)
        tvStepLength.text = String.format(Locale.US, "步长：%.2f m", displayStepLengthMeter)
        tvPosition.text = String.format(Locale.US, "位置：E=%.2f m, N=%.2f m", displayEastMeter, displayNorthMeter)
        tvTime.text = String.format(Locale.US, "时间：%.3f s", displayTimeSec)
        tvInterrupt.text = "中断状态：${if (displayInterrupted) "是" else "否"}"


    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(uiRunnable)
        sensorCollector.stop()
        gpsCollector.stop()
        rawDataWriter?.close()
        gpsTruthWriter?.close()
        gpsEnuWriter?.close()
        pdrResultWriter?.close()
    }

    private fun startInitialization() {
        accInitBuffer.clear()
        magInitBuffer.clear()

        isInitializing = true
        isInitialized = false

        initRollRad = 0.0
        initPitchRad = 0.0
        initHeadingRad = 0.0
        initLatDeg = null
        initLonDeg = null
    }


    private fun tryFinishInitialization() {
        if (!isInitializing) return

        android.util.Log.d("PDR_INIT", "acc=${accInitBuffer.size}, mag=${magInitBuffer.size}")
        // 初始化需要累计足够数量的加速度和磁力计样本
        val accEnough = accInitBuffer.size >= currentConfig.initAccSampleCount
        val magEnough = magInitBuffer.size >= currentConfig.initMagSampleCount

        if (!accEnough || !magEnough) return

        val result = initManager.initializeAll(
            accSamples = accInitBuffer,
            magSamples = magInitBuffer,
            useGpsPosition = false
        )

        if (result.success) {
            initRollRad = result.attitude.rollRad
            initPitchRad = result.attitude.pitchRad
            initHeadingRad = result.attitude.headingTrueRad

            initLatDeg = result.position.latitudeDeg
            initLonDeg = result.position.longitudeDeg

            realtimeEngine?.applyInitialization(
                rollRad = initRollRad,
                pitchRad = initPitchRad,
                headingRad = initHeadingRad
            )


            isInitialized = true
            isInitializing = false

            Toast.makeText(this, "初始化完成", Toast.LENGTH_SHORT).show()

            android.util.Log.d("PDR_INIT", "initialization success, heading=$initHeadingRad")
        } else {
            isInitialized = false
            isInitializing = false
            Toast.makeText(this, "初始化失败", Toast.LENGTH_SHORT).show()
        }


    }



}

