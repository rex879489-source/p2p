package com.example.p2pchat

import android.content.Context
import org.webrtc.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wraps a single WebRTC PeerConnection: one data channel for text chat,
 * one audio track for voice calls. Signaling (SDP exchange) is done
 * manually via a plain-text code the user copies to the other person
 * through any channel they like (no signaling server required).
 *
 * Encryption (DTLS-SRTP) is handled automatically by WebRTC itself -
 * no custom crypto needed.
 */
class WebRtcClient(
    context: Context,
    private val onMessage: (String) -> Unit,
    private val onStateChange: (String) -> Unit
) {
    private val eglBase = EglBase.create()

    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var localAudioTrack: AudioTrack? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        createPeerConnection()
        setupAudio()
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                // Using non-trickle ICE: we wait for gathering to complete
                // then export the full SDP, so nothing to do per-candidate.
            }
            override fun onDataChannel(channel: DataChannel?) {
                channel?.let { setupDataChannel(it) }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                onStateChange(newState?.name ?: "UNKNOWN")
            }
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        })
    }

    private fun setupAudio() {
        val audioConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("AUDIO_TRACK", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack)
    }

    private fun setupDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer ?: return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                onMessage(String(bytes, Charsets.UTF_8))
            }
        })
    }

    fun sendMessage(text: String) {
        val buffer = DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false
        )
        dataChannel?.send(buffer)
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /** Caller side: create data channel + offer, wait for ICE gathering, return full SDP as text. */
    fun createOffer(callback: (String) -> Unit) {
        val init = DataChannel.Init()
        dataChannel = peerConnection?.createDataChannel("chat", init)
        dataChannel?.let { setupDataChannel(it) }

        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                waitForIceGatheringThenReturn(desc.type, callback)
            }
        }, constraints)
    }

    /** Callee side: consume remote offer text, create answer, return full SDP as text. */
    fun createAnswer(remoteOfferText: String, callback: (String) -> Unit) {
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, remoteOfferText)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), remoteDesc)

        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                waitForIceGatheringThenReturn(desc.type, callback)
            }
        }, constraints)
    }

    /** Caller side: consume the answer text pasted back from the other device. */
    fun setRemoteAnswer(remoteAnswerText: String) {
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, remoteAnswerText)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), remoteDesc)
    }

    /** Waits (briefly, async-safe) for ICE gathering to complete, then hands back local SDP text. */
    private fun waitForIceGatheringThenReturn(type: SessionDescription.Type, callback: (String) -> Unit) {
        Thread {
            var waited = 0
            while (peerConnection?.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE && waited < 8000) {
                Thread.sleep(200)
                waited += 200
            }
            val finalDesc = peerConnection?.localDescription
            callback(finalDesc?.description ?: "")
        }.start()
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
