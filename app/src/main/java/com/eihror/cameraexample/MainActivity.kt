package com.eihror.cameraexample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.btn_takepicture
import kotlinx.android.synthetic.main.activity_main.texture

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
  }

  override fun onResume() {
    super.onResume()
    texture.onResume()

    initView()
  }

  override fun onPause() {
    texture.onPause()
    super.onPause()
  }

  private fun initView() {
    btn_takepicture.setOnClickListener {
      texture.captureImage { imageByte: ByteArray ->
        Log.d(MainActivity::class.java.name, imageByte.toString())
      }
    }
  }

}