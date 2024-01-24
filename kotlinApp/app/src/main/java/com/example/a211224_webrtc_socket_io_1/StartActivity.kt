package com.example.a211224_webrtc_socket_io_1

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.example.a211224_webrtc_socket_io_1.databinding.ActivityMainBinding
import com.example.a211224_webrtc_socket_io_1.databinding.ActivityStartBinding

class StartActivity : AppCompatActivity(), View.OnClickListener {
    val binding by lazy { ActivityStartBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        //버튼클릭 리스너 등록
        binding.btnCreateRoom.setOnClickListener(this)
        binding.btnRoomJoin.setOnClickListener(this)
    }


    //버튼 클릭 리스너 메서드
    override fun onClick(v: View?) {
        when (v?.id) {
            //방생성 버튼
            binding.btnCreateRoom.id -> {
                val intent = Intent(applicationContext, MainActivity::class.java)
                //방제목 입력 검사
                if (binding.edName.text.toString().isEmpty()) {
                    Toast.makeText(applicationContext, "방제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return
                }
                //이름 입력 검사
                if (binding.edName.text.toString().isEmpty()) {
                    Toast.makeText(applicationContext, "이름을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return
                }


                //방제목
                intent.putExtra("roomName", binding.edRoomName.text.toString())
                //이름
                intent.putExtra("name", binding.edName.text.toString())
                startActivity(intent)
                finish()
            }

            //방참여 버튼
            binding.btnRoomJoin.id -> {
                //방제목 입력 검사
                if (binding.edName.text.toString().isEmpty()) {
                    Toast.makeText(applicationContext, "방제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return
                }
                //이름 입력 검사
                if (binding.edName.text.toString().isEmpty()) {
                    Toast.makeText(applicationContext, "이름을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return
                }

                val intent = Intent(applicationContext, MainActivity::class.java)
                //방제목
                intent.putExtra("roomName", binding.edRoomName.text.toString())
                //이름
                intent.putExtra("name", binding.edName.text.toString())
                startActivity(intent)
                finish()
            }
            else -> {
            }
        }
    }


}