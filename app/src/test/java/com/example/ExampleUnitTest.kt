package com.example

import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.io.File
import java.io.StringWriter
import java.io.PrintWriter

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    val filesToDownload = mapOf(
      "vidxgo.py" to "https://raw.githubusercontent.com/AstraeLabs/VibraVid/main/VibraVid/player/vidxgo.py"
    )
    for ((localName, url) in filesToDownload) {
      try {
        val text = URL(url).readText()
        File(localName).writeText(text)
      } catch (e: Exception) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        File("error_download_$localName.txt").writeText(sw.toString())
      }
    }
  }
}
