package self.sbjz.codec

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.navigation.ui.AppBarConfiguration
import self.sbjz.codec.databinding.ActivityMainBinding
import java.io.File


typealias CodecName = String

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var browseFileLauncher: ActivityResultLauncher<Intent>
    private var codecs = mutableMapOf<CodecName, CodecInfo>()
    private var codecSpinnerIndex: Int = 0
    private var targetVideoSpinnerIndex: Int = 0
    private var targetAudioSpinnerIndex: Int = 0
    private var selectedVideoData: Intent? = null
    var selectedVideoCodec: String? = null
    var selectedAudioCodec: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        browseFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(), this::handleLocalFile
        )

        setSupportActionBar(binding.toolbar)

        binding.browseBtn.setOnClickListener { browseFile() }
        binding.transcodeBtn.setOnClickListener { transcodeMedia() }

        setAvailableCodec()
        setTargetVideoSpinner()
        setTargetAudioSpinner()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun browseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.setType("*/*")
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        browseFileLauncher.launch(intent)
    }

    fun handleLocalFile(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                Log.i("Codec", "Selected file URI: ${data.data}")
                val mimeType = contentResolver.getType(data.data!!)
                Log.i("Codec", "Selected file MIME type: $mimeType")
                if (mimeType?.contains("video") == true) {
                    getVideoMetaData(data)
                }
            }
        }
    }

    private fun getVideoMetaData(data: Intent) {
        val mediaExtractor = MediaExtractor()
        val runMediaExtractor =
            runCatching { mediaExtractor.setDataSource(this, data.data!!, null) }
        var extractedMime: String? = null
        runMediaExtractor.onSuccess {
            selectedVideoData = data
            var videoMime: String? = null
            var audioMime: String? = null
            var textMime: String? = null
            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                Log.i("Codec", "Media track $i MIME type: $mime")
                if (mime?.startsWith("video/") == true) videoMime = mime
                if (mime?.startsWith("audio/") == true) audioMime = mime
                if (mime?.startsWith("text/") == true) textMime = mime
            }
            extractedMime = buildString {
                if (videoMime != null) append("Video MIME: $videoMime")
                if (audioMime != null) {
                    if (isNotEmpty()) append(", ")
                    append("Audio MIME: $audioMime")
                }
                if (textMime != null) {
                    if (isNotEmpty()) append(", ")
                    append("Text MIME: $textMime")
                }
            }
        }
        runMediaExtractor.onFailure {
            selectedVideoData = null
            extractedMime = "Failed: ${it.message}"
            buildString {
                append("Failed")
                if (it.message != null && it.message!!.isNotEmpty()) {
                    if (it.message!!.length > 50) append(": ${it.message!!.substring(0, 50)}...")
                    else append(": ${it.message}")
                } else append(" to extract MIME type")
            }
        }
        binding.selectedVideoText.text = extractedMime
    }

    @OptIn(UnstableApi::class)
    private fun transcodeMedia() {
        val data = selectedVideoData
        if (data == null) {
            Toast.makeText(this, "No media selected for transcoding", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedVideoCodec == null || selectedAudioCodec == null) {
            Toast.makeText(
                this,
                "Please select both video and audio codecs for transcoding",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        Log.i("Codec", "Transcoding media: ${data.data}")
        val inputMediaItem = MediaItem.fromUri(selectedVideoData?.data!!)
        var transformer: Transformer? = null
        val transformerRun = runCatching {
            transformer = Transformer.Builder(this)
                .setAudioMimeType("audio/${selectedAudioCodec}")
                .setVideoMimeType("video/${selectedVideoCodec}")
                .addListener(transcodeListener)
                .build()
        }
        transformerRun.onSuccess {
            checkExternalStoragePermissions()
//            val outFile = getExternalFilesDir(null)?.absolutePath + "/transcoded_${System.currentTimeMillis()}.mp4"
            val fileDir =
                File("/storage/emulated/0/Download/Transcoded")
            if (!fileDir.exists()) fileDir.mkdirs()
            val outFile =
                fileDir.absolutePath + "/transcoded_${System.currentTimeMillis()}.mp4"
            transformer!!.start(inputMediaItem, outFile)
            Log.i("Codec", "File saved: $outFile")
        }
        transformerRun.onFailure {
            Log.e("Codec", "Failed to create transformer: ${it.message}", it)
            Toast.makeText(
                this,
                "Failed to create transformer: ${it.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val transcodeListener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            Log.i("Codec", "Transcoding completed successfully - $exportResult - $composition")
            Log.i("Codec", "Export Exception: ${exportResult.exportException}")
            Toast.makeText(
                this@MainActivity,
                "Transcoding completed successfully",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            Log.e(
                "Codec",
                "Transcoding failed: ${exportException.message}",
                exportException
            )
            Toast.makeText(
                this@MainActivity,
                "Transcoding failed: ${exportException.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @OptIn(UnstableApi::class)
    private fun setAvailableCodec() {
        val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        mediaCodecList.codecInfos.forEach { codecInfo ->
            codecInfo.getCapabilitiesForType(codecInfo.supportedTypes.firstOrNull()).mimeType
            val actualCodecName =
                if (codecInfo.isAlias) codecInfo.canonicalName else codecInfo.name
            val mimeTypes = codecInfo.supportedTypes.map { MimeTypes.getMediaMimeType(it) }
            if (codecs[actualCodecName] == null) codecs[actualCodecName] = CodecInfo(
                codecInfo.isAlias,
                codecInfo.canonicalName,
                codecInfo.isEncoder,
                codecInfo.supportedTypes.toList(),
                codecInfo.getCapabilitiesForType(codecInfo.supportedTypes.firstOrNull()),
                codecInfo.isHardwareAccelerated,
                codecInfo.isSoftwareOnly,
                codecInfo.isVendor,
                mimeTypes
            )
        }
        val codecItems = codecs.keys.toTypedArray()
//        val codecItems = codecs.values.map { it.supportedTypes }.flatten().distinct()
//            .sortedBy { it }.toTypedArray()
        setupSpinner(
            spinner = binding.codecSpinner,
            textArrayResId = null,
            textArray = codecItems,
            selectionIndex = codecSpinnerIndex
        ) { parent, view, position, id ->
            val selectedCodec = parent?.getItemAtPosition(position) as CodecName
            val codecInfo = codecs[selectedCodec]
            if (codecInfo != null) {
                binding.isEncoder.text = if (codecInfo.isEncoder) "YES" else "NO"
                binding.isHardware.text = if (codecInfo.isHardwareAccelerated) "YES" else "NO"
                binding.supportedTypes.text = codecInfo.supportedTypes.joinToString(", ")
                binding.software.text = if (codecInfo.isSoftwareOnly) "YES" else "NO"
                binding.provider.text =
                    if (codecInfo.isVendor) "Device Manufacturer" else "Android Platform"
            } else Log.i("Codec", "No codec info found for: $selectedCodec")
        }
    }

    private fun setTargetAudioSpinner() {
        val allAudioCodecs = codecs.filter {
            it.value.isEncoder && it.value.supportedTypes.any { type ->
                type.startsWith("audio/")
            }
        }.values.flatMap { it.supportedTypes.map { it.removePrefix("audio/") } }.distinct()
            .toTypedArray()
        setupSpinner(
            spinner = binding.targetAudioSpinner,
            textArrayResId = null,
            textArray = allAudioCodecs,
            selectionIndex = targetAudioSpinnerIndex
        ) { parent, view, position, id ->
            selectedAudioCodec = parent?.getItemAtPosition(position) as CodecName
            Log.i("Codec", "Selected audio codec: $selectedAudioCodec")
        }
    }

    private fun setTargetVideoSpinner() {
        val allVideoCodecs = codecs.filter {
            it.value.isEncoder && it.value.supportedTypes.any { type ->
                type.startsWith("video/")
            }
        }.values.flatMap { it.supportedTypes.map { it.removePrefix("video/") } }.distinct()
            .toTypedArray()
        setupSpinner(
            spinner = binding.targetVideoSpinner,
            textArrayResId = null,
            textArray = allVideoCodecs,
            selectionIndex = targetVideoSpinnerIndex
        ) { parent, view, position, id ->
            selectedVideoCodec = parent?.getItemAtPosition(position) as CodecName
            Log.i("Codec", "Selected video codec: $selectedVideoCodec")
        }
    }

    private fun setupSpinner(
        spinner: Spinner,
        textArrayResId: Int?,
        textArray: Array<String>?,
        selectionIndex: Int,
        onItemSelected: (AdapterView<*>?, View?, Int, Long) -> Unit
    ) {
        if (textArrayResId != null) {
            spinner.adapter = ArrayAdapter.createFromResource(
                this, textArrayResId, android.R.layout.simple_spinner_dropdown_item
            )
        } else if (textArray != null && textArray.isNotEmpty()) {
            spinner.adapter = CustomArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item, textArray
            )
        } else IllegalArgumentException("Either textArrayResId or textArray must be provided")

        spinner.setSelection(selectionIndex)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                onItemSelected(parent, view, position, id)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun checkExternalStoragePermissions() {
        val REQUEST_PERMISSION_CODE = 0
        if (!Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    ("package:$packageName").toUri()
                )
                startActivityForResult(intent, REQUEST_PERMISSION_CODE); // Define a request code
            } catch (ex: Exception) {
                val intent = Intent()
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQUEST_PERMISSION_CODE)
            }
        }
    }
}


enum class CodecType {
    HARDWARE, SOFTWARE
}

data class CodecInfo(
    val isAlias: Boolean,
    val canonicalName: String,
    val isEncoder: Boolean,
    val supportedTypes: List<String>,
    val capabilities: MediaCodecInfo.CodecCapabilities?,
    val isHardwareAccelerated: Boolean,
    val isSoftwareOnly: Boolean,
    val isVendor: Boolean,
    val mimeType: List<String?>
)

class CustomArrayAdapter(context: Context, resource: Int, items: Array<String>) :
    ArrayAdapter<String>(context, resource, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val selectedItemText = view.findViewById<View?>(android.R.id.text1) as TextView?
        selectedItemText?.setTextColor(Color.BLACK)
        return view
    }

    override fun getDropDownView(
        position: Int, convertView: View?, parent: ViewGroup
    ): View {
        val view = super.getDropDownView(position, convertView, parent)
        view.setBackgroundColor("#7f9c96".toColorInt())
        val selectedItemText = view.findViewById<View?>(android.R.id.text1) as TextView?
        selectedItemText?.setTextColor(Color.BLACK)
        return view
    }
}