package com.example.a211224_webrtc_socket_io_1

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import com.example.a211224_webrtc_socket_io_1.databinding.ActivityMainBinding
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.util.*


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    object API {
        const val PERMISSION_REQUEST = 2
        val VIDEO_RESOLUTION_WIDTH = 800//800
        val VIDEO_RESOLUTION_HEIGHT = 300//600
        val FPS = 30
    }

    var TAG = "MainActivity: "

    /*피어 커넥션 관련 & ICE 관련*/
    var peerConnection: PeerConnection? = null

    //스턴 서버
    var STUNList = Arrays.asList(
        "stun:stun.l.google.com:19302",

    )

    /*오디오 & 비디오 관련 */
    private val rootEglBase: EglBase = EglBase.create()

    var factory: PeerConnectionFactory? = null
    private val videoCapturer by lazy { getVideoCapturer(this) }
    private val audioManager by lazy { RTCAudioManager.create(this) }
    var audioConstraints: MediaConstraints? = null
    var localVideoTrack: VideoTrack? = null
    var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null

    // 위치 정보 dataChannel
    var localdataChannel: DataChannel? = null
    //오디오 스위치
    private var isMute = false

    //비디오 스위치
    private var isVideoPaused = false

    private var inSpeakerMode = false

    /*Socket.io 관련*/
    //클라이언트 소켓
    private var mSocket: Socket? = null
    private var isConnected = true

    var roomName = ""
    var name = ""


    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //인텐트에 저장한 입력한 방제목, 이름 갖고오기
        if (intent.hasExtra("roomName")) {
            roomName = intent.getStringExtra("roomName").toString()
            name = intent.getStringExtra("name").toString()
        } else {
            Toast.makeText(this, "인텐트 값 전달 Error!", Toast.LENGTH_SHORT).show()
        }

        //소켓연결 시작
        val entry: Boolean = connect_Sokcet()


        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        binding.switchCameraButton.setOnClickListener {
            switchCamera()
        }

        binding.audioOutputButton.setOnClickListener {
            if (inSpeakerMode) {
                inSpeakerMode = false
                binding.audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
            } else {
                inSpeakerMode = true
                binding.audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
            }
        }
        binding.videoButton.setOnClickListener {
            if (isVideoPaused) {
                isVideoPaused = false
                binding.videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            } else {
                isVideoPaused = true
                binding.videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
            }
            enableVideo(isVideoPaused)
        }

        binding.micButton.setOnClickListener {
            if (isMute) {
                isMute = false
                binding.micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
            } else {
                isMute = true
                binding.micButton.setImageResource(R.drawable.ic_baseline_mic_24)
            }
            enableAudio(isMute)
        }
        binding.endCallButton.setOnClickListener {
//            rtcClient.endCall(meetingID)
            binding.surfaceView2.isGone = false
            Constants.isCallEnded = true
            finish()
            startActivity(Intent(this@MainActivity, StartActivity::class.java))
        }


        //권한요청
        requestPermissions()
        //video view 초기화
        initializeSurfaceViews()
        //PeerConnectionFactory 객체 초기화
        initializePeerConnectionFactory()
        //Video 트랙 초기화
        createVideoTrackFromCameraAndShowIt()
        //ICE, SDP 관련 리스너 초기화
        initializePeerConnections()
        //비디오 스트리밍 초기화
        startStreamingVideo()
    }


    override fun onDestroy() {
        super.onDestroy()
        //소켓 연결 종료
        mSocket?.disconnect()
        mSocket?.off(Socket.EVENT_CONNECT, onConnect);
        mSocket?.off(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket?.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
    }

    /* 권한 관련 메서드 */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.e(TAG, "onPermissionsGranted")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.e(TAG, "onPermissionsDenied")
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Dynamic permissions are not required before Android M.
            //   onPermissionsGranted();
            return
        }
        methodRequiresTwoPermission()
        val missingPermissions = getMissingPermissions()
        if (missingPermissions.size != 0) {
            requestPermissions(missingPermissions, API.PERMISSION_REQUEST)
        } else {
            // onPermissionsGranted();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun getMissingPermissions(): Array<String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return arrayOfNulls(0)
        }
        val info: PackageInfo
        info = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to retrieve permissions.")
            return arrayOfNulls(0)
        }
        if (info.requestedPermissions == null) {
            Log.e(TAG, "No requested permissions.")
            return arrayOfNulls(0)
        }
        val missingPermissions = ArrayList<String?>()
        for (i in info.requestedPermissions.indices) {
            if (info.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0) {
                missingPermissions.add(info.requestedPermissions[i])
            }
        }
        Log.e(TAG, "Missing permissions: $missingPermissions")
        return missingPermissions.toTypedArray()
    }

    @AfterPermissionGranted(API.PERMISSION_REQUEST)
    private fun methodRequiresTwoPermission() {
        val perms = getMissingPermissions()
        if (EasyPermissions.hasPermissions(this, *perms)) {
            // Already have permission, do the thing
            // ...
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, "Requires Permission",
                API.PERMISSION_REQUEST, *perms
            )
        }
    }

    /* 비디오 관련 */
    private fun initializeSurfaceViews() {
        binding.surfaceView.init(rootEglBase.eglBaseContext, null)
        binding.surfaceView.setEnableHardwareScaler(true)
        binding.surfaceView.setMirror(true)
        binding.surfaceView2.init(rootEglBase.eglBaseContext, null)
        binding.surfaceView2.setEnableHardwareScaler(true)
        binding.surfaceView2.setMirror(true)
        //add one more
    }

    fun enableVideo(videoEnabled: Boolean) {
        if (localVideoTrack != null)
            localVideoTrack?.setEnabled(videoEnabled)
    }

    fun enableAudio(audioEnabled: Boolean) {
        if (localAudioTrack != null)
            localAudioTrack?.setEnabled(audioEnabled)
    }

    fun switchCamera() {
        videoCapturer.switchCamera(null)
    }

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }


    private fun startStreamingVideo() {
        val mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(localVideoTrack)
        mediaStream.addTrack(localAudioTrack)
        peerConnection!!.addStream(mediaStream)
        //   sendMessage("got user media");
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }


    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }


        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }


    private fun createVideoTrackFromCameraAndShowIt() {
        //VideoSource videoSource=null;
        //Create a VideoSource instance
        val videoSource: VideoSource
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        videoSource = factory!!.createVideoSource(videoCapturer.isScreencast)
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        val videoEncoderFactory: VideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext, true, true
        )
        for (i in videoEncoderFactory.supportedCodecs.indices) {
            Log.d(TAG, "Supported codecs: " + videoEncoderFactory.supportedCodecs[i].name)
        }
        localVideoTrack = factory!!.createVideoTrack("100", videoSource)

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = MediaConstraints()

        //create an AudioSource instance
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack("101", audioSource)
        //   videoCapturer.startCapture(1024, 720, 30);
        //비디오 캠 설정
        videoCapturer.startCapture(
            API.VIDEO_RESOLUTION_WIDTH,
            API.VIDEO_RESOLUTION_HEIGHT,
            API.FPS
        )


        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack?.setEnabled(true)
        localVideoTrack?.addSink(binding.surfaceView)
    }


    /* PeerConnection 관련 */

    //화상통화 시작 버튼
    //OfferSdp 생성 후 서버에 전송
    fun doCall(view: View) {
        Log.e("MainActivity", "doCall: ")

        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
//        sdpMediaConstraints.optional.add(
//            MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"))

        //Offer SDP 생성
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.e(TAG, "onCreateSuccess: ")
                //1)LocalDescription에 Seesion저장
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    //생성한 OfferSdp를 서버에 전송
                    mSocket?.emit("offer", message)
                    Log.e(TAG, "onCreateSuccess_offer: $message")
                } catch (e: JSONException) {
                    Log.e(TAG, "onCreateSuccess ERROR: $e")
                }
            }
        }, sdpMediaConstraints)
//        AnswerDBListner();
    }




    fun sendToDataChannel(view: View) {

        try {


            val sendData = "Hello, World!"
            val buffer : ByteBuffer = ByteBuffer.wrap(sendData.toByteArray())
//            localdataChannel!!.send(DataChannel.Buffer(buffer,false)) //.send(DataChannel.Buffer(buffer, false))
            Log.e(TAG, "DataChanel state :  "+ localdataChannel!!.state())
            val sendResult =  localdataChannel!!.send(DataChannel.Buffer(buffer,false))
//            localdataChannel!!.send(buffer)to
            Log.e(TAG, "sendToDataChannel invoked!! result : "+sendResult.toString())

        }catch (e : Exception){
            Log.e(TAG, "dataChannel Problem !! : "+e.message)
            runOnUiThread {
                Log.i(TAG, "dataChannel Problem !!")
                Toast.makeText(applicationContext, "dataChannel null 발생"
                    , Toast.LENGTH_SHORT).show()

            }
        }
    }
    //PeerConnectionFactory 초기화
    private fun initializePeerConnectionFactory() {
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        val options = PeerConnectionFactory.Options()
        encoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext, true /* enableIntelVp8Encoder */, false
        )
        decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(options)
            .createPeerConnectionFactory()
    }


    private fun initializePeerConnections() {
        peerConnection = factory?.let { createPeerConnection(it) }

        val dcInit = DataChannel.Init()
        dcInit.id=1
        dcInit.negotiated= true
        dcInit.ordered= true
        Log.d(TAG,"DC PROTOCAL : "+dcInit.protocol)
        localdataChannel = peerConnection?.createDataChannel("1", dcInit)

        localdataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                val bufferedAmount = localdataChannel?.bufferedAmount()
                Log.d(TAG, "DataChannel buffered amount: $bufferedAmount")
            }

            override fun onStateChange() {
                val state = localdataChannel?.state()
                Log.d(TAG, "DataChannel state: $state")
                if (state == DataChannel.State.OPEN) {
                    Log.d(TAG, "DataChannel is open!")
                } else if (state == DataChannel.State.CLOSED) {
                    Log.d(TAG, "DataChannel is closed!")
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes);
                val message = String(bytes);
                Log.e(TAG, "Datachannel message Received!!")
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Datachannel message Received!!", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

    }


    //피어커넥션을 생성 후 ICE를 교환하는 메서드
    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection? {
        Log.d(TAG, "createPeerConnection")
        // Add ICE Servers
        val iceServers = ArrayList<IceServer>()

        for (i in STUNList) {
            val iceServerBuilder = IceServer.builder(i)
            iceServerBuilder.setTlsCertPolicy(TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK) //this does the magic.
            val iceServer = iceServerBuilder.createIceServer()
            iceServers.add(iceServer)
        }

        val rtcConfig = RTCConfiguration(iceServers)


        val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                /*
                 * HAVE_LOCAL_OFFER
                 * HAVE_REMOTE_OFFER
                 */
                Log.e(TAG, "onSignalingChange: $signalingState")
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.e(TAG, "onIceConnectionChange: $iceConnectionState")
                ConnectionStatus(iceConnectionState.toString())
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.e(TAG, "onIceConnectionReceivingChange: $b")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.e(TAG, "onIceGatheringChange: $iceGatheringState")
            }

            //CreateOffer()를 한 이후에 콜백됨.
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.e(TAG, "onIceCandidate: $iceCandidate")
                val message = JSONObject()
                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    //생성한 ice후보를 서버에 전달
                    mSocket?.emit("candidate", message)
                    Log.e(TAG, "onIceCandidate: $message")
                } catch (e: JSONException) {
                    Log.e(TAG, "onIceCandidate ERROR: $e")
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.e(TAG, "onIceCandidatesRemoved: $iceCandidates")
            }

            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}

            override fun onAddStream(mediaStream: MediaStream) {
                Log.e(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                val remoteAudioTrack = mediaStream.audioTracks[0]
                remoteAudioTrack.setEnabled(true)
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addSink(binding.surfaceView2)
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.e(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
//                Log.e(TAG, "onDataChannel: Data Channel !!" )
//
//                dataChannel.registerObserver(object : DataChannel.Observer{
//                    override fun onBufferedAmountChange(previousAmount: Long) {
//                    }
//
//                    override fun onStateChange() {
//                        TODO("Not yet implemented")
//                    }
//
//                    override fun onMessage(buffer: DataChannel.Buffer) {
//                        val data  = String(buffer.data.array())
//                        Log.e(TAG , "DataChannel data arrived: ");
//                        runOnUiThread {
//                            Log.i(TAG, "DataChannel data arrived")
//                            Toast.makeText(applicationContext, data
//                                , Toast.LENGTH_SHORT).show()
//                        }
//
//                    }
//
//                })
            }

            override fun onRenegotiationNeeded() {
                Log.e(TAG, "onRenegotiationNeeded: ")
            }

            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}
            override fun onTrack(transceiver: RtpTransceiver) {}
            override fun onStandardizedIceConnectionChange(newState: IceConnectionState) {
                Log.e(TAG, "onStandardizedIceConnectionChange: $newState")
            }

            override fun onConnectionChange(newState: PeerConnectionState) {}
        }

//        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
        return  factory.createPeerConnection(rtcConfig, pcObserver)


    }

    fun ConnectionStatus(s: String) {
        runOnUiThread {
            try {
                if (s == "CONNECTED") {
                    Toast.makeText(this@MainActivity, "CONNECTED", Toast.LENGTH_SHORT).show()
                    binding.remoteViewLoading.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "ConnectionStatus: $e")
            }
        }
    }

    /*Socket.io 연결 관련 */
    private fun connect_Sokcet(): Boolean {
        try {
            // connection 반복시도 방지 위한 옵션 관련 자료 링크 -> https://velog.io/@tera_geniel/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9Ckotlin%EC%99%80-nodejs-socket.io%EB%A1%9C-%ED%86%B5%EC%8B%A0%ED%95%98%EA%B8%B0
            // 현재 코틀린의 socket-io 버전이 2.xx  이에 맞게 signal-server의  버전이 3.xx or 4.xx지정되있는지 확인 필수 (현재 3.xx버전)
            //이유:  connect 시도를 반복적으로 하는 상황 방지

            val socketOption:Array<String> = arrayOf("websocket") // 옵션 필수
            val webSocketOptions:IO.Options = IO.Options.builder().setTransports(socketOption).build()
            //클라이언트 소켓 생성
            mSocket = IO.socket("http://15.164.224.113:5001" , webSocketOptions)
//            mSocket = IO.socket("http://10.0.2.2:5001")
            //소켓 연결 관련 이벤트
            mSocket?.on(Socket.EVENT_CONNECT, onConnect);
            mSocket?.on(Socket.EVENT_DISCONNECT, onDisconnect);
            mSocket?.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            //서버와 소켓 연결 시작
            mSocket?.connect();

            //방참여시 서버에 보낼 참여한 유저 정보
            val data = JSONObject()
            try {
                //참여한 방 이름
                data.put("roomName", roomName)
                //유저이름
                data.put("name", name)
            } catch (e: JSONException) {
                Log.e(TAG, "onCreateSuccess ERROR: $e")
            }

            Log.e(TAG, "join_room_JsonData: $data")
            //방참여 이벤트 서버에 보내기
            mSocket?.emit("join_room", data)

            //소켓이벤트 초기화
            initSocketEvent()

            return true;
        } catch (e: URISyntaxException) {
            Log.e("MainActivity", e.reason)
            return false
        }
    }

    //소켓 통신시 사용하는 이벤트 초기화
    private fun initSocketEvent() {
        //방에 참여한 인원 수 받기
        mSocket?.on("all_users", onAllUsers)
        //caller가 보낸 OfferSdp 받기
        mSocket?.on("getOffer", onGetOffer)
        //callee가 보낸 answerSdp 받기
        mSocket?.on("getAnswer", onGetAnswer)
        //ice후보 받기
        mSocket?.on("getCandidate", onGetCandidate)
       // mSocket?.on("getDisconnectedPeer" onDisconnectedPeer)
    }

/*소켓 통신을 이벤트 리스너 */
    /**
     * 'all_users'이벤트를 서버에서 보냈을 때 작동
     *  현재 본인이 참여한 방의 객체를 반환(본인제외)
     */


    private val onAllUsers = Emitter.Listener { args ->
        try {
            val usersInThisRoom = args[0] as JSONArray
            //내가 참여한 방의 인원 수 (본인 제외)
            Log.e(TAG, "usersInThisRoom.length: " + usersInThisRoom.length())
            //ICE, SDP 생성


        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * 'getOffer'이벤트를 서버에서 보냈을 때 작동
     *  Caller가 보낸 OfferSDP를 받음
     */

    private val onGetOffer = Emitter.Listener { args ->
        try {
            Log.e(TAG, "Log_529")
            //Caller가 보낸 OfferSdp
            val ar_offerSdp = args[0] as JSONObject
            //Caller의 OfferSDP 등록
            Log.e(TAG, "Caller의 offer_등록완료")

            peerConnection!!.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(
                    SessionDescription.Type.OFFER,
                    ar_offerSdp.getString("sdp")
                )
            )
            //Caller에게 전달해줄 Answer Sdp 생성
            peerConnection!!.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    //Callee의 SetLocal에 answer 등록
                    peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                    val message = JSONObject()
                    message.put("type", "answer")
                    message.put("sdp", sessionDescription.description)
                    Log.e(TAG, "onCreateSuccess_Callee answer sdp 생성완료: $message")
                    //Caller에게 AnswerSdp 전달하기 위해
                    //서버에 AnswerSdp 보내기
                    mSocket?.emit("answer", message)
                }

            }, MediaConstraints()) // new MediaConstraints()

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }


    /**
     * 'getAnswer'이벤트를 서버에서 보냈을 때 작동
     *  Callee가 보낸 AnswerSDP를 받음
     */
    private val onGetAnswer = Emitter.Listener { args ->
        try {
            //Callee가 보낸 AnswerSdp
            val ar_AnswerSdp = args[0] as JSONObject
            //AnswerSDP 등록
            Log.e(TAG, "Callee의 Answer등록완료")
            peerConnection!!.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(
                    SessionDescription.Type.ANSWER,
                    ar_AnswerSdp.getString("sdp")
                )
            )
            Log.e(TAG, "onGetAnswer_Callee가 보낸 Answer  등록완료")

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * 서버에서 'candidate'이벤트를 보냈을 때 작동
     *  caller와 callee 간의 서로 iceCandidate을 받음
     */

    private val onGetCandidate = Emitter.Listener { args ->
        //서버에서 보낸 상대방의 ice 후보를 받아온다
        val ar_candidate = args[0] as JSONObject
        try {
            Log.e(TAG, "Log_596")
            //ice 후보를 파싱한다
            Log.e(TAG, "onGetCandidate_ice교환완료")
            val candidate = IceCandidate(
                ar_candidate.getString("id"),
                ar_candidate.getInt("label"),
                ar_candidate.getString("candidate")
            )
            //상대방의 ice후보를 추가한다
            peerConnection!!.addIceCandidate(candidate)
        } catch (err: JSONException) {
            Log.e("Error", err.toString())
        }
    }

//    private val onDisconnectedPeer = Emitter.Listener { args ->
//        val  = args[0] as JSONObject
//
//    }


/* 소켓연결, 종료, 에러 리스너*/
    /**
     * 서버와 소켓 연결 성공 시 리스너
     */
    private val onConnect =
        Emitter.Listener { args: Array<Any?>? ->
            runOnUiThread {
                Log.e(TAG, "connected")
            }
        }

    /**
     * 서버와 소켓 연결이 해제시 리스너
     */
    private val onDisconnect =
        Emitter.Listener { args: Array<Any?>? ->
            runOnUiThread {
                Log.i(TAG, "diconnected")
                isConnected = false
                Toast.makeText(applicationContext, "종료", Toast.LENGTH_SHORT).show()

            }
        }

    /**
     * 서버연결이 실패 했을 때 리스너
     */
    private val onConnectError =
        Emitter.Listener { args: Array<Any?>? ->
            runOnUiThread {
                val s: Any? = args
                Log.e(TAG, "Error connecting : 서버와 연결이 실패됬습니다.")
                Toast.makeText(
                    applicationContext,
                    "연결 실패", Toast.LENGTH_SHORT
                ).show()
            }
        }
}