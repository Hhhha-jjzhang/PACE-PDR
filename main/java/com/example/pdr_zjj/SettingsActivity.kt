package com.example.pdr_zjj

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pdr_zjj.ahrs.AhrsMode
import com.example.pdr_zjj.data.storage.PdrParameterStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var parameterStore: PdrParameterStore

    private lateinit var heightMeterEdit: EditText
    private lateinit var declinationDegEdit: EditText

    private lateinit var realtime6Group: ParameterGroupViews
    private lateinit var realtime9Group: ParameterGroupViews
    private lateinit var offline6Group: ParameterGroupViews
    private lateinit var offline9Group: ParameterGroupViews

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.title = "\u53C2\u6570\u8BBE\u7F6E"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        parameterStore = PdrParameterStore(this)

        heightMeterEdit = findViewById(R.id.etHeightMeter)
        declinationDegEdit = findViewById(R.id.etDeclinationDeg)

        realtime6Group = bindParameterGroup(findViewById(R.id.panelRealtime6), "\u5B9E\u65F6 6 \u8F74")
        realtime9Group = bindParameterGroup(findViewById(R.id.panelRealtime9), "\u5B9E\u65F6 9 \u8F74")
        offline6Group = bindParameterGroup(findViewById(R.id.panelOffline6), "\u4E8B\u540E 6 \u8F74")
        offline9Group = bindParameterGroup(findViewById(R.id.panelOffline9), "\u4E8B\u540E 9 \u8F74")

        findViewById<TextView>(R.id.btnResetDefaults).setOnClickListener {
            parameterStore.resetToDefaults()
            loadValues()
            Toast.makeText(this, "\u5DF2\u6062\u590D\u9ED8\u8BA4\u53C2\u6570", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnSaveSettings).setOnClickListener {
            saveValues()
        }

        loadValues()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindParameterGroup(root: View, title: String): ParameterGroupViews {
        root.findViewById<TextView>(R.id.tvGroupTitle).text = title
        return ParameterGroupViews(
            baseLengthFactorEdit = root.findViewById(R.id.etBaseLengthFactor),
            cadenceFactorEdit = root.findViewById(R.id.etCadenceFactor),
            amplitudeFactorEdit = root.findViewById(R.id.etAmplitudeFactor),
            scaleFactorEdit = root.findViewById(R.id.etScaleFactor),
            minStepIntervalSecEdit = root.findViewById(R.id.etMinStepIntervalSec),
            detectionThresholdScaleEdit = root.findViewById(R.id.etDetectionThresholdScale)
        )
    }

    private fun loadValues() {
        heightMeterEdit.setText(parameterStore.getHeightMeter().toString())
        declinationDegEdit.setText(parameterStore.getDeclinationDeg().toString())

        realtime6Group.setValues(parameterStore.getRealtimeParameters(AhrsMode.DOF6))
        realtime9Group.setValues(parameterStore.getRealtimeParameters(AhrsMode.DOF9))
        offline6Group.setValues(parameterStore.getOfflineParameters(AhrsMode.DOF6))
        offline9Group.setValues(parameterStore.getOfflineParameters(AhrsMode.DOF9))
    }

    private fun saveValues() {
        val heightMeter = parseDouble(heightMeterEdit, parameterStore.getHeightMeter())
        val declinationDeg = parseDouble(declinationDegEdit, parameterStore.getDeclinationDeg())

        parameterStore.saveGlobalParameters(
            heightMeter = heightMeter,
            declinationDeg = declinationDeg
        )
        parameterStore.saveRealtimeParameters(AhrsMode.DOF6, realtime6Group.readValues())
        parameterStore.saveRealtimeParameters(AhrsMode.DOF9, realtime9Group.readValues())
        parameterStore.saveOfflineParameters(AhrsMode.DOF6, offline6Group.readValues())
        parameterStore.saveOfflineParameters(AhrsMode.DOF9, offline9Group.readValues())

        Toast.makeText(this, "\u53C2\u6570\u5DF2\u4FDD\u5B58", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun parseDouble(editText: EditText, fallback: Double): Double {
        return editText.text?.toString()?.trim()?.toDoubleOrNull() ?: fallback
    }

    private data class ParameterGroupViews(
        val baseLengthFactorEdit: EditText,
        val cadenceFactorEdit: EditText,
        val amplitudeFactorEdit: EditText,
        val scaleFactorEdit: EditText,
        val minStepIntervalSecEdit: EditText,
        val detectionThresholdScaleEdit: EditText
    ) {
        fun setValues(values: PdrParameterStore.StepLengthParameters) {
            baseLengthFactorEdit.setText(values.baseLengthFactor.toString())
            cadenceFactorEdit.setText(values.cadenceFactor.toString())
            amplitudeFactorEdit.setText(values.amplitudeFactor.toString())
            scaleFactorEdit.setText(values.scaleFactor.toString())
            minStepIntervalSecEdit.setText(values.minStepIntervalSec.toString())
            detectionThresholdScaleEdit.setText(values.detectionThresholdScale.toString())
        }

        fun readValues(): PdrParameterStore.StepLengthParameters {
            return PdrParameterStore.StepLengthParameters(
                baseLengthFactor = baseLengthFactorEdit.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0,
                cadenceFactor = cadenceFactorEdit.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0,
                amplitudeFactor = amplitudeFactorEdit.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0,
                scaleFactor = scaleFactorEdit.text?.toString()?.trim()?.toDoubleOrNull() ?: 1.0,
                minStepIntervalSec = minStepIntervalSecEdit.text?.toString()?.trim()?.toDoubleOrNull()
                    ?: 0.30,
                detectionThresholdScale = detectionThresholdScaleEdit.text?.toString()?.trim()?.toDoubleOrNull()
                    ?: 1.0
            )
        }
    }
}
