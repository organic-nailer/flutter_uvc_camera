package com.chenyeju

import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import android.os.Handler
import android.os.Looper
import com.jiangdg.ausbc.callback.IPreviewDataCallBack

/**
 * プレビューフレームデータ(NV21)のEventChannel処理クラス
 */
class PreviewStreamHandler : EventChannel.StreamHandler {
    private val TAG = "PreviewStreamHandler"
    private var eventSink: EventSink? = null

    // フレーム制御
    private var lastFrameTime = 0L
    var frameRateLimit = 30
    var frameSizeLimit = 0

    // プレビューストリーム制御
    private var isPreviewStreamActive = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * IPreviewDataCallBackの実装
     * CameraUVCのmPreviewDataCbListに追加される
     */
    val previewDataCallback = object : IPreviewDataCallBack {
        override fun onPreviewData(
            data: ByteArray?,
            width: Int,
            height: Int,
            format: IPreviewDataCallBack.DataFormat
        ) {
            if (data != null && format == IPreviewDataCallBack.DataFormat.NV21) {
                onPreviewFrame(data, width, height, System.currentTimeMillis())
            }
        }
    }

    override fun onListen(arguments: Any?, events: EventSink?) {
        eventSink = events
        lastFrameTime = 0L
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    /**
     * プレビューストリームを開始
     */
    fun startPreviewStream() {
        isPreviewStreamActive = true
        sendStreamState("PREVIEW_STREAM_STARTED")
    }

    /**
     * プレビューストリームを停止
     */
    fun stopPreviewStream() {
        isPreviewStreamActive = false
        sendStreamState("PREVIEW_STREAM_STOPPED")
    }

    /**
     * プレビューフレームデータ処理
     */
    private fun onPreviewFrame(data: ByteArray, width: Int, height: Int, timestamp: Long) {
        val sink = eventSink ?: return

        // プレビューストリームが有効でない場合はスキップ
        if (!isPreviewStreamActive) {
            return
        }

        // フレームレート制御
        val currentTime = System.currentTimeMillis()
        if (frameRateLimit > 0) {
            val minInterval = 1000 / frameRateLimit
            if (currentTime - lastFrameTime < minInterval) {
                return  // このフレームをスキップ
            }
            lastFrameTime = currentTime
        }

        // サイズ制御
        if (frameSizeLimit > 0 && data.size > frameSizeLimit) {
            return  // 大きなフレームをスキップ
        }

        // データのコピーを作成（並行アクセスの問題を避けるため）
        val dataCopy = data.copyOf()

        mainHandler.post {
            try {
                val event = HashMap<String, Any>()
                event["type"] = "NV21"
                event["data"] = dataCopy
                event["width"] = width
                event["height"] = height
                event["timestamp"] = timestamp
                event["size"] = dataCopy.size

                sink.success(event)
            } catch (e: Exception) {
                sink.error("PREVIEW_STREAM_ERROR", "Error processing preview frame: ${e.message}", null)
            }
        }
    }

    /**
     * ストリーム状態を送信
     */
    private fun sendStreamState(state: String) {
        val sink = eventSink ?: return

        val event = HashMap<String, Any>()
        event["type"] = "STATE"
        event["state"] = state

        mainHandler.post {
            sink.success(event)
        }
    }
}