package com.example.pdr_zjj.ahrs

import com.example.pdr_zjj.data.model.AhrsPose
import com.example.pdr_zjj.preprocess.model.SyncedSensorFrame

interface IAhrsFilter {
    fun reset(initialQuaternion: Quaternion = Quaternion())
    fun setInitialQuaternion(q: Quaternion)
    fun update(frame: SyncedSensorFrame): AhrsPose?
    fun getQuaternion(): Quaternion
}