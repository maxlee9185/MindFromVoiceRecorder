package android.portfolio.mindfromvoicerecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.portfolio.mindfromvoicerecorder.databinding.ActivityMainBinding
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import java.io.IOException

class MainActivity : AppCompatActivity(), OnTimerTickListener {

    //static, const
    companion object {
        private const val REQUEST_RECORD_AUDIO_CODE = 200
    }

    // 릴리즈 -> 녹음 -> 릴리즈
    // 릴리즈 -> 재생 -> 릴리즈
    private enum class State {
        RELEASE, RECORDING, PLAYING
    }

    private lateinit var timer: Timer

    private lateinit var binding: ActivityMainBinding
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var fileName: String = ""
    private var state: State = State.RELEASE //초기상태 release

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileName = "${externalCacheDir?.absolutePath}/audiorecord.3gp"
        timer = Timer(this)

        binding.recordButton.setOnClickListener {
            when (state) {
                State.RELEASE -> {
                    record()
                }
                State.RECORDING -> {
                    onRecord(false)
                }
                State.PLAYING -> {

                }
            }
        }

        binding.playButton.setOnClickListener {
            when (state) {
                State.RELEASE -> { onPlay(true) }
                else -> { } // do nothing
            }
        }

        binding.stopButton.setOnClickListener {
            when (state) {
                State.PLAYING -> { onPlay(false) }
                else -> {  }// do nothing
            }
        }
    } //onCreate

    private fun record() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                onRecord(true)
            } //권한 동의 시 녹음

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            ) -> {
                showPermissionRationalDialog()
            } //왜 권한 필요한지 설명

            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_CODE
                ) //다시 요구
            }
        }
    }

    private fun onRecord(start: Boolean) = if (start) startRecording() else stopRecoding()

    private fun onPlay(start: Boolean) = if(start) startPlaying() else stopPlaying()

    private fun startRecording() {
        state = State.RECORDING //녹음상태 표시

        recorder = MediaRecorder().apply { //리코더 받아오기
            setAudioSource(MediaRecorder.AudioSource.MIC) //마이크
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP) //음성파일형식
            setOutputFile(fileName) //저장파일
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB) //엔코딩

            try {
                prepare() //start() 전에 시스템점검 기능
            } catch (e: IOException) {
                Log.e("APP", "prepare() failed $e")
            }

            start() //시작
        }

        binding.waveFormView.clearData() //녹음전 데이터 초기화
        timer.start() //타이머시작

        binding.recordButton.setImageDrawable( //녹음 버튼을 stop버튼으로 바꾸기
            ContextCompat.getDrawable(
                this,
                R.drawable.baseline_stop_24
            )
        )
        binding.recordButton.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black))
        binding.playButton.isEnabled = false
        binding.playButton.alpha = 0.3f //play버튼 투명처리
    }

    private fun stopRecoding() { //녹음정지
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        timer.stop()

        state = State.RELEASE

        binding.recordButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.baseline_fiber_manual_record_24
            )
        ) //다시 녹음 가능표시
        binding.recordButton.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
        binding.playButton.isEnabled = true
        binding.playButton.alpha = 1.0f //play버튼 투명화된것 다시 복구
    }

    private fun startPlaying() { //음성파일 재생
        state = State.PLAYING

        player = MediaPlayer().apply {
            try {
                setDataSource(fileName)
                prepare()
            } catch (e: IOException) {
                Log.e("APP", "media player prepare fail $e")
            }

            start()
        }

        binding.waveFormView.clearWave()
        timer.start()

        player?.setOnCompletionListener {
            stopPlaying()
        } //플레이 끝나면 stop

        binding.recordButton.isEnabled = false
        binding.recordButton.alpha = 0.3f //리코드 버튼 투명화
    }

    private fun stopPlaying() {
        state = State.RELEASE

        player?.release()
        player = null

        timer.stop()

        binding.recordButton.isEnabled = true
        binding.recordButton.alpha = 1.0f //리코드 버튼 다시 복구
    }

    private fun showPermissionRationalDialog() {
        AlertDialog.Builder(this)
            .setMessage("녹음 권한을 허용해야 앱을 정상적으로 사용할 수 있습니다.")
            .setPositiveButton("권한 허용하기") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_CODE
                )
            }.setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun showPermissionSettingDialog() {
        AlertDialog.Builder(this)
            .setMessage("녹음 권한을 허용해야 앱을 정상적으로 사용할 수 있습니다. 앱 설정 화면으로 진입해 권한을 켜주세요.")
            .setPositiveButton("권한 변경하러 가기") { _, _ ->
                navigateToAppSetting()
            }.setNegativeButton("취소") { dialogInterface, _ -> dialogInterface.cancel() }
            .show()

    }

    private fun navigateToAppSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val audioRecordPermissionGranted = requestCode == REQUEST_RECORD_AUDIO_CODE
                && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        //권한을 받은 상황이면
        if (audioRecordPermissionGranted) {
            onRecord(true)
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                showPermissionRationalDialog() //권한 이유 설명하는 팝업을 본 상태면 보여주기
            } else {
                showPermissionSettingDialog() //또 팝업 띄우지 않고 세팅화면으로 넘어가게 하기
            }
        }
    }

    override fun onTick(duration: Long) {
        val millisecond = duration % 1000
        val second = (duration / 1000) % 60
        val minute = (duration / 1000 / 60)

        binding.timerTextView.text = String.format("%02d:%02d.%02d", minute, second, millisecond / 10)

        if(state == State.PLAYING) {
            binding.waveFormView.replayAmplitude()
        } else if (state == State.RECORDING) {
            binding.waveFormView.addAmplitude(recorder?.maxAmplitude?.toFloat() ?: 0f)
        }
    }
}