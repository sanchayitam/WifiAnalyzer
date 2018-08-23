package com.example.sanch.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.net.wifi.WifiInfo

import org.achartengine.ChartFactory
import org.achartengine.GraphicalView
import org.achartengine.chart.PointStyle
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYMultipleSeriesRenderer
import org.achartengine.renderer.XYSeriesRenderer

import java.util.ArrayList
import java.util.HashMap


/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [WifiFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [WifiFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class WifiFragment : Fragment() {

    private var viewHolder: ViewHolder? = null
    private var mWifiReceiver: WifiScanReceiver? = null
    private var mWifiManager: WifiManager? = null
    private val mSsidColorMap = HashMap<String, Int>()
    var LOG_TAG = "WifiFragment"
    internal val FREQUENCY_CONNECTED_CHANNEL: Short = 0
    internal val NETWORKS_ON_CONNECTED_CHANNEL: Short = 1
    internal val SSID_CONNECTED_NETWORK: Short = 2

    private val sNumberOfChannels = 13
    private val sFirstChannel = 1
    private val sLastChannel = sNumberOfChannels

    var sAllChannels :Int = (sNumberOfChannels + 4)
    private val s1stFactor: Short = 80
    private val s2ndFactor: Short = 72
    private val s3rdFactor: Short = 64
    private val sDBmFactor = 0.2f
    private val sDBm2ndFactor: Short = 20
    private val sDBm3rdFactor: Short = 40

    private val sNN = "N/N"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val rootView = inflater.inflate(R.layout.fragment_wifi, container, false)

        viewHolder = ViewHolder(rootView)

        update()

        return rootView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mWifiManager = activity!!.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        mWifiReceiver = WifiScanReceiver()

        Utility.enableWifi(mWifiManager!!)



        super.onCreate(savedInstanceState)
    }

    override fun onPause() {
        activity!!.unregisterReceiver(mWifiReceiver)
        super.onPause()
    }

    override fun onResume() {
        activity!!.registerReceiver(mWifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        super.onResume()
    }

    //update all items in gui
    private fun update() {
        Utility.enableWifi(mWifiManager!!)


        val wifiScanList = mWifiManager!!.scanResults ?: return

        val wifiInfo = mWifiManager!!.connectionInfo

        val connectionInfo = getInfoAboutCurrentConnection(wifiInfo, wifiScanList)

        updateChart(wifiScanList, connectionInfo)

        updateValuationList(wifiScanList, connectionInfo)

        if (wifiInfo.bssid == null)
            return

        updateInfoBar(Integer.valueOf(connectionInfo[NETWORKS_ON_CONNECTED_CHANNEL].toString()),
                Integer.valueOf(connectionInfo[FREQUENCY_CONNECTED_CHANNEL].toString()),
                connectionInfo[SSID_CONNECTED_NETWORK].toString())
    }

    //draw graph with networks
    private fun updateChart(wifiScanList: List<ScanResult>, connectionInfo: HashMap<Short, String>) {
        val wifiChart = WifiChart(wifiScanList, sNumberOfChannels)
        wifiChart.init()
        if (connectionInfo[SSID_CONNECTED_NETWORK] != null) {
            wifiChart.setValues(connectionInfo[SSID_CONNECTED_NETWORK].toString())
        } else
            wifiChart.setValues("tmp")

        viewHolder!!.mChartChannels.addView(wifiChart.getmChartView(), 0)
    }

    //update info in bar that don't need valuation
    private fun updateInfoBar(size: Int, freq: Int, ssid: String) {
        val resources = resources

        viewHolder!!.connectedView.setText(java.lang.String.format(resources.getString(R.string.connected_bar), ssid))
        val channel = Utility.convertFrequencyToChannel(freq)
        viewHolder!!.channelView.setText(java.lang.String.format(resources.getString(R.string.ci_channel_bar), channel))
        viewHolder!!.networksOnThisChannelView.setText(java.lang.String.format(resources.getString(R.string.ci_number_of_networks_on_this_channel_bar), size))
    }

    //draw and find best channels
    private fun updateValuationList(wifiScanList: List<ScanResult>, connectionInfo: HashMap<Short, String>) {

        val valuation_percent_recommended = valuateChannelsWithoutConnected(wifiScanList)


        val recommendedChannels = getRecommendedChannels(valuation_percent_recommended)
        viewHolder!!.recommendedView.setText(java.lang.String.format(resources.getString(R.string.ci_recommended_bar),
                recommendedChannels[0], recommendedChannels[1], recommendedChannels[2]
        ))


        if (connectionInfo[SSID_CONNECTED_NETWORK] == sNN)
            return

        val index = Utility.convertFrequencyToChannel(Integer.valueOf(connectionInfo[FREQUENCY_CONNECTED_CHANNEL].toString())) - 1
        Log.d("tak", "updateValuationList: " + index + " / " + valuation_percent_recommended.size)

    }

    //vevaluation of all bandwidths on the basis of available networks
    private fun valuateChannels(wifiScanList: List<ScanResult>): IntArray {
        val result = IntArray(sAllChannels)

        for (i in 0..sAllChannels) {
            result[i] = 500
        }

        for (list in wifiScanList) {
            val channel = Utility.convertFrequencyToChannel(list.frequency)

            if (channel < sFirstChannel || channel > sLastChannel)
                continue

            val dBm = list.level

            result[channel + 1] -= (s1stFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, 0)).toInt()
            result[channel] -= (s2ndFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm2ndFactor.toInt())).toInt()
            result[channel - 1] -= (s3rdFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm3rdFactor.toInt())).toInt()
            result[channel + 2] -= (s2ndFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm2ndFactor.toInt())).toInt()
            result[channel + 3] -= (s3rdFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm3rdFactor.toInt())).toInt()
        }

        for (i in 0.. sAllChannels) {
            result[i] /= 5

            if (result[i] < 0)
                result[i] = 0
        }

        return result
    }

    //evaluation in way that connected network is not taken into account
    private fun valuateChannelsWithoutConnected(wifiScanList: List<ScanResult>): IntArray {
        val result = IntArray(sAllChannels)

        for (i in 0..sAllChannels) {
            result[i] = 500
        }

        val wifiInfo = mWifiManager!!.connectionInfo
        var bssid: String? = wifiInfo.bssid
        if (bssid == null)
            bssid = "tmp"

        for (list in wifiScanList) {
            if (list.BSSID != bssid) {
                val channel = Utility.convertFrequencyToChannel(list.frequency)
                val dBm = list.level

                if (channel < sFirstChannel || channel > sLastChannel)
                    continue

                result[channel + 1] -= (s1stFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, 0)).toInt()
                result[channel] -= (s2ndFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm2ndFactor.toInt())).toInt()
                result[channel - 1] -= (s3rdFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm3rdFactor.toInt())).toInt()
                result[channel + 2] -= (s2ndFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm2ndFactor.toInt())).toInt()
                result[channel + 3] -= (s3rdFactor + sDBmFactor * Utility.convertRssiToQualityWithSub(dBm, sDBm3rdFactor.toInt())).toInt()
            }
        }

        for (i in 0.. sAllChannels) {
            result[i] /= 5

            if (result[i] < 0)
                result[i] = 0
        }

        val averages = IntArray(sNumberOfChannels)

        for (i in 2 .. sAllChannels - 2) {
            val sum = result[i - 2] + result[i - 2] + result[i] + result[i + 1] + result[i + 2]
            averages[i - 2] = sum / 5
        }

        return averages
    }

    //get numbers of networks on each channel
    private fun getNumberOfNetworksOnChannels(wifiScanList: List<ScanResult>): IntArray {
        val result = IntArray(sNumberOfChannels)

        for (i in 0.. sNumberOfChannels) {
            result[i] = 0
        }

        for (list in wifiScanList) {
            val res = Utility.convertFrequencyToChannel(list.frequency) - 1


            if (res < sFirstChannel - 1 || res > sLastChannel - 1)
                continue

            result[res] += 1
        }

        return result
    }

    //get three best channels on the basis of valuateChannelsWithoutConnected
    private fun getRecommendedChannels(ints: IntArray): IntArray {
        var best1 = 0
        var best2 = 0
        var best3 = 0
        var ch1 = 0
        var ch2 = 0
        var ch3 = 0

        for (i in ints) {
            if (best1 < ints[i]) {
                best3 = best2
                best2 = best1
                best1 = ints[i]
                ch3 = ch2
                ch2 = ch1
                ch1 = i
            } else if (best2 < ints[i]) {
                best3 = best2
                best2 = ints[i]
                ch3 = ch2
                ch2 = i
            } else if (best3 < ints[i]) {
                best3 = ints[i]
                ch3 = i
            }
        }

        val rec = IntArray(3)

        rec[0] = ch1 + 1
        rec[1] = ch2 + 1
        rec[2] = ch3 + 1

        return rec
    }

    //get parameters about connected network
    private fun getInfoAboutCurrentConnection(wifiInfo: WifiInfo, wifiScanList: List<ScanResult>): HashMap<Short, String> {
        val hashMap = HashMap<Short, String>()

        var onConnectedChannel = 0
        var frequency = 0

        var bssid: String? = wifiInfo.bssid
        if (bssid == null)
            bssid = sNN

        for (list in wifiScanList) {
            if (bssid == list.BSSID) {
                frequency = list.frequency
            }
        }

        for (list in wifiScanList) {
            if (frequency == list.frequency) {
                ++onConnectedChannel
            }
        }

        hashMap.put(FREQUENCY_CONNECTED_CHANNEL, frequency.toString())
        hashMap.put(NETWORKS_ON_CONNECTED_CHANNEL, onConnectedChannel.toString())
        if (bssid == sNN)
            hashMap.put(SSID_CONNECTED_NETWORK, bssid)
        else
            hashMap.put(SSID_CONNECTED_NETWORK, wifiInfo.ssid)

        return hashMap
    }

    private inner class WifiChart(private val mWifiScanList: List<ScanResult>, private val numberOfChannels: Int) {
        private val LABEL_X = getString(R.string.ci_labelx)
        private val LABEL_Y = getString(R.string.ci_labely)
        private val mDataset: XYMultipleSeriesDataset
        private val mRenderer: XYMultipleSeriesRenderer
        private var mChartView: GraphicalView? = null

        init {
            mDataset = XYMultipleSeriesDataset()
            mRenderer = XYMultipleSeriesRenderer()

            mChartView = ChartFactory.getLineChartView(activity, mDataset, mRenderer)
        }

        fun init() {
            mRenderer.marginsColor = Color.argb(0x00, 0xff, 0x00, 0x00) // transparent margins
            mRenderer.setPanEnabled(false, false)
            mRenderer.yAxisMax = -40.0
            mRenderer.yAxisMin = -100.0
            mRenderer.yLabels = 6
            mRenderer.yTitle = LABEL_Y
            mRenderer.xAxisMin = -2.0
            mRenderer.xAxisMax = (numberOfChannels + 2).toDouble()
            mRenderer.xLabels = 0
            mRenderer.xTitle = LABEL_X
            mRenderer.setShowGrid(true)
            mRenderer.isShowLabels = true
            mRenderer.isFitLegend = true
            mRenderer.isShowCustomTextGrid = true
            mRenderer.legendTextSize = 30f
            // mRenderer.setBarSpacing(20);
            mRenderer.margins = intArrayOf(60, 35, 35, 60)
            mRenderer.legendHeight = 45
            mRenderer.isInScroll = true

            mRenderer.axisTitleTextSize = 20f

            mRenderer.labelsTextSize = 20f

            for (i in -2.. numberOfChannels + 2) {
                if (i > 0 && i < numberOfChannels + 1)
                    mRenderer.addXTextLabel(i.toDouble(), i.toString())
                else
                    mRenderer.addXTextLabel(i.toDouble(), "")
            }
        }

        fun setValues(currentSSID: String) {
            var index = 0

            for (list in mWifiScanList) {
                val renderer = XYSeriesRenderer()
                renderer.chartValuesTextSize = 30f
                renderer.color = getColorForConnection(mSsidColorMap, list.SSID)
                renderer.isDisplayBoundingPoints = true

                if (list.SSID == currentSSID) {
                    renderer.lineWidth = 5f
                    renderer.pointStyle = PointStyle.DIAMOND
                    renderer.isDisplayChartValues = true
                    renderer.pointStrokeWidth = 10f
                } else {
                    renderer.lineWidth = 2f
                    renderer.pointStyle = PointStyle.CIRCLE
                    //   renderer.setDisplayChartValues(true);
                    renderer.pointStrokeWidth = 3f
                }

                val series = XYSeries(list.SSID)

                val channel = Utility.convertFrequencyToChannel(list.frequency)

                if (channel < sFirstChannel || channel > sLastChannel)
                    continue

                series.add((channel - 2).toDouble(), -100.0)
                series.add(channel.toDouble(), list.level.toDouble())
                series.add((channel + 2).toDouble(), -100.0)

                mDataset.addSeries(index, series)
                mRenderer.addSeriesRenderer(index, renderer)
                index++

            }
        }

        private fun getColorForConnection(hashMap: HashMap<String, Int>, ssid: String): Int {
            if (!hashMap.containsKey(ssid)) {
                hashMap.put(ssid,  Utility.randColor())
            }

            return hashMap[ssid]!!
        }

        fun getmChartView(): View {
            mChartView = ChartFactory.getLineChartView(activity, mDataset, mRenderer)
            return mChartView!!
        }
    }


    private inner class WifiScanReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            update()
        }
    }

    inner class ViewHolder(rootView: View) {
        val connectedView: TextView
        val channelView: TextView
        val networksOnThisChannelView: TextView
        val recommendedView: TextView


        val mChartChannels: LinearLayout

        init {
            mChartChannels = rootView.findViewById<View>(R.id.ci_channel_chart) as LinearLayout

            connectedView = rootView.findViewById<View>(R.id.ci_connected_textview) as TextView
            channelView = rootView.findViewById<View>(R.id.ci_channel_textview) as TextView
            networksOnThisChannelView = rootView.findViewById<View>(R.id.ci_numbers_of_networks_on_this_ch_textview) as TextView
            recommendedView = rootView.findViewById<View>(R.id.ci_recommended_textview) as TextView


        }
    }


}