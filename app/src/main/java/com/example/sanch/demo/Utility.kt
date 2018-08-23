package com.example.sanch.demo

import android.graphics.Color
import android.net.wifi.WifiManager

import java.util.Random

object Utility {
    fun convertFrequencyToChannel(freq: Int): Int {
        return if (freq >= 2412 && freq <= 2484) {
            (freq - 2412) / 5 + 1
        } else if (freq >= 5170 && freq <= 5825) {
            (freq - 5170) / 5 + 34
        } else {
            -1
        }
    }


    fun convertRssiToQuality(dBm: Int): Int {

        return if (dBm <= -100) {
            0
        } else if (dBm == 0) {
            0
        } else if (dBm >= -50) {
            100
        } else {
            2 * (dBm + 100)
        }
    }

    fun convertRssiToQualityWithSub(dBm: Int, sub: Int): Int {
        var dBmFirst: Int

        if (dBm <= -100) {
            dBmFirst = 0
        } else if (dBm == 0) {
            dBmFirst = 0
        } else if (dBm >= -50) {
            dBmFirst = 100
        } else {
            dBmFirst = 2 * (dBm + 100)
        }

        dBmFirst -= sub

        return if (dBmFirst < 0)
            0
        else
            dBmFirst
    }


    fun randColor(): Int {
        val rand = Random()
        val hsv = FloatArray(3)
        Color.RGBToHSV(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255), hsv)

        return Color.HSVToColor(hsv)
    }

    fun enableWifi(wifiManager: WifiManager) {
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }
    }
}