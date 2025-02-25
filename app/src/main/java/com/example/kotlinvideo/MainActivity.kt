package com.example.kotlinvideo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var videoRecyclerView: RecyclerView
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchLayout: TextInputLayout
    private lateinit var noVideosFoundText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var selectVideoFab: FloatingActionButton
    private lateinit var openVideoLauncher: ActivityResultLauncher<Intent>

    // İzin isteme launcher'ı
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadVideoList()
        } else {
            Toast.makeText(this, "Videolara erişmek için izin gerekli", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View bileşenlerini tanımla
        initializeViews()
        setupVideoAdapter()
        setupListeners()

        // Video seçme işlemi için launcher
        openVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { videoUri ->
                    playVideo(videoUri.toString())
                }
            }
        }

        // İzinleri kontrol et ve video listesini yükle
        checkAndRequestPermissions()
    }

    private fun initializeViews() {
        videoRecyclerView = findViewById(R.id.videoRecyclerView)
        searchInput = findViewById(R.id.searchInput)
        searchLayout = findViewById(R.id.searchLayout)
        noVideosFoundText = findViewById(R.id.noVideosFoundText)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        selectVideoFab = findViewById(R.id.selectVideoFab)
    }

    private fun setupVideoAdapter() {
        videoAdapter = VideoAdapter(emptyList()) { videoPath ->
            playVideo(videoPath)
        }

        videoRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = videoAdapter
        }
    }

    private fun setupListeners() {
        // Floating Action Button'a tıklandığında video seçme
        selectVideoFab.setOnClickListener {
            openVideoPicker()
        }

        // Arama işlevi
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                filterVideos(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun checkAndRequestPermissions() {
        val readStoragePermission = Manifest.permission.READ_EXTERNAL_STORAGE
        val readMediaVideoPermission = Manifest.permission.READ_MEDIA_VIDEO

        when {
            ContextCompat.checkSelfPermission(this, readStoragePermission) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, readMediaVideoPermission) == PackageManager.PERMISSION_GRANTED -> {
                loadVideoList()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, readStoragePermission) -> {
                Toast.makeText(this, "Video dosyalarını görüntülemek için depolama izni gerekli", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(readStoragePermission)
            }
            else -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(readMediaVideoPermission)  // Android 13 ve üzeri için
                } else {
                    requestPermissionLauncher.launch(readStoragePermission)  // Android 12 ve altı için
                }
            }
        }
    }


    private fun loadVideoList() {
        loadingProgressBar.visibility = View.VISIBLE
        noVideosFoundText.visibility = View.GONE

        // Arka planda video listesini yükle
        Thread {
            val videoList = getVideoList()
            runOnUiThread {
                loadingProgressBar.visibility = View.GONE

                if (videoList.isEmpty()) {
                    noVideosFoundText.visibility = View.VISIBLE
                    videoRecyclerView.visibility = View.GONE
                } else {
                    noVideosFoundText.visibility = View.GONE
                    videoRecyclerView.visibility = View.VISIBLE
                    videoAdapter.updateVideoList(videoList)
                }
            }
        }.start()
    }

    private fun getVideoList(): List<VideoItem> {
        val videoItems = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Video.Media.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val data = cursor.getString(dataColumn)

                val file = File(data)
                if (file.exists()) {
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    val videoItem = VideoItem(
                        id = id,
                        title = name,
                        duration = formatDuration(duration),
                        size = formatSize(size),
                        path = data,
                        thumbnailUri = contentUri.toString()
                    )
                    videoItems.add(videoItem)
                }
            }
        }

        return videoItems
    }

    private fun filterVideos(query: String) {
        videoAdapter.filter(query)

        if (videoAdapter.itemCount == 0) {
            noVideosFoundText.visibility = View.VISIBLE
            videoRecyclerView.visibility = View.GONE
        } else {
            noVideosFoundText.visibility = View.GONE
            videoRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun playVideo(videoPath: String) {
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("videoUri", videoPath)
        }
        startActivity(intent)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        openVideoLauncher.launch(intent)
    }

    // Yardımcı fonksiyonlar
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%d:%02d", minutes, seconds % 60)
        }
    }

    private fun formatSize(size: Long): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }
}

// Video öğesi veri sınıfı
data class VideoItem(
    val id: Long,
    val title: String,
    val duration: String,
    val size: String,
    val path: String,
    val thumbnailUri: String
)

// Video adaptörü
class VideoAdapter(
    private var videoList: List<VideoItem>,
    private val onVideoClick: (String) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var filteredList: List<VideoItem> = videoList

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.videoTitleText)
        val durationText: TextView = itemView.findViewById(R.id.videoDurationText)
        val sizeText: TextView = itemView.findViewById(R.id.videoSizeText)
        val thumbnailImage: ImageView = itemView.findViewById(R.id.videoThumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.video_item, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val videoItem = filteredList[position]

        holder.titleText.text = videoItem.title
        holder.durationText.text = videoItem.duration
        holder.sizeText.text = videoItem.size

        // Glide ile küçük resim yükleme
        Glide.with(holder.itemView.context)
            .load(videoItem.thumbnailUri)  // thumbnailUri zaten Uri olarak tanımlı
            .placeholder(R.drawable.placeholder_image) // Yüklenirken gösterilecek görsel
            .error(R.drawable.error_image) // Hata durumunda gösterilecek görsel
            .into(holder.thumbnailImage)
        holder.itemView.setOnClickListener {
            onVideoClick(videoItem.path)
        }
    }

    override fun getItemCount() = filteredList.size

    fun updateVideoList(newList: List<VideoItem>) {
        videoList = newList
        filteredList = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            videoList
        } else {
            videoList.filter {
                it.title.lowercase().contains(query.lowercase())
            }
        }
        notifyDataSetChanged()
    }

}