package com.azamputraimanto.food

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.azamputraimanto.food.databinding.ActivityMainBinding
import com.azamputraimanto.food.ml.FoodModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutput: TextView
    private val GALLERY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.tvPictures
        button = binding.btnUploadImage
        tvOutput = binding.tvResult
        val buttonTake = binding.btnTakeImage

        button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
                takePicturePreview.launch(null)
            }
            else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
        buttonTake.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED){
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // to redirect user to google search for the scientific name
        tvOutput.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${tvOutput.text}"))
            startActivity(intent)
        }

        // to download image when longPress on ImageView
        imageView.setOnLongClickListener {
            requestPermissionLauncer.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener true
        }
    }

    // request camera permission
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if (granted){
            takePicturePreview.launch(null)
        }else {
            Toast.makeText(this, "Permission Denied !! Try again", Toast.LENGTH_SHORT).show()
        }
    }

    // launch camera and take picture
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->
        if (bitmap != null){
            imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
    }

    //to get image from gallery
    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        Log.i("TAG", "This is the result: ${result.data} ${result.resultCode}")
        onResultReceived(GALLERY_REQUEST_CODE, result)
    }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?){
        when(requestCode){
            GALLERY_REQUEST_CODE ->{
                if (result?.resultCode == Activity.RESULT_OK){
                    result.data?.data?.let{uri ->
                        Log.i("TAG", "onResultREceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap){
        //declearning tensorflow lite model variabel
        val foodModel = FoodModel.newInstance(this)

        // Creates inputs for reference.
        // Converting bitmap into tensorflow image
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888,true)
        val tfimagefood = TensorImage.fromBitmap(newBitmap)

        // Runs model inference and gets result.
        // Process the image using trained model and sort it in descending order
        val outputs = foodModel.process(tfimagefood)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }

        // Getting result having high probability
        val highProbabilityOutput = outputs[0]

        //Setting output text
        tvOutput.text = highProbabilityOutput.label
        Log.i("TAG", "outputGenerator: $highProbabilityOutput")
    }

    // to download image to device
    private val requestPermissionLauncer = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean ->
        if (isGranted) {
            AlertDialog.Builder(this).setTitle("Download Image?")
                .setMessage("Do you want to download this image to your device?")
                .setPositiveButton("Yes"){_, _ ->
                    val drawable:BitmapDrawable = imageView.drawable as BitmapDrawable
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)
                }
                .setNegativeButton("No") {dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            Toast.makeText(this, "Please allow permission to download image", Toast.LENGTH_LONG).show()
        }
    }

    // Fun that takes a bitmap and store to user's device
    private fun downloadImage(mBitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Food_Images"+System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null) {
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG,100, outputStream)) {
                        throw IOException("Couldn't save the bitmap")
                    } else {
                        Toast.makeText(applicationContext, "Image Saved", Toast.LENGTH_LONG).show()
                    }
                }
                return it
            }
        }
        return null
    }
}