package com.elik745i.isobotbt

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elik745i.isobotbt.databinding.ActivityPdfViewerBinding
import java.io.File
import java.io.IOException

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.closeButton.setOnClickListener { finish() }
        binding.previousPageButton.setOnClickListener { showPage(pageIndex - 1) }
        binding.nextPageButton.setOnClickListener { showPage(pageIndex + 1) }

        val assetPath = intent.getStringExtra(EXTRA_ASSET_PATH)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.pdfTitleText.text = title

        if (assetPath.isNullOrBlank()) {
            toast(getString(R.string.manual_copy_failed))
            finish()
            return
        }

        if (!openPdf(assetPath, title.ifBlank { "manual.pdf" })) {
            finish()
        }
    }

    override fun onDestroy() {
        currentPage?.close()
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
        super.onDestroy()
    }

    private fun openPdf(assetPath: String, outputName: String): Boolean {
        return try {
            val pdfDir = File(cacheDir, "manuals").apply { mkdirs() }
            val pdfFile = File(pdfDir, outputName)
            assets.open(assetPath).use { input ->
                pdfFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            parcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            showPage(0)
            true
        } catch (_: IOException) {
            toast(getString(R.string.manual_copy_failed))
            false
        }
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index !in 0 until renderer.pageCount) {
            return
        }

        currentPage?.close()
        currentPage = renderer.openPage(index)
        pageIndex = index

        val page = currentPage ?: return
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val bitmapWidth = (screenWidth - resources.displayMetrics.density * 32).toInt().coerceAtLeast(800)
        val bitmapHeight = (bitmapWidth.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        binding.pdfPageImage.setImageBitmap(bitmap)
        binding.pdfPageIndicator.text = getString(R.string.pdf_page_indicator, index + 1, renderer.pageCount)
        binding.previousPageButton.isEnabled = index > 0
        binding.nextPageButton.isEnabled = index < renderer.pageCount - 1
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ASSET_PATH = "extra_asset_path"
        const val EXTRA_TITLE = "extra_title"
    }
}
