package com.example.screencapture

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.io.FileFilter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream


@SpringBootApplication
class ScreencaptureApplication {

    class ScreenCaptureApplicationRunner(private val imgs: File, private val out: File) : ApplicationRunner {

        private val log = LogFactory.getLog(javaClass)

        override fun run(args: ApplicationArguments?) {

            fun transcode(files: List<File>, timeBetweenFramesMS: Long, targetGif: File) {
                log.debug("transcoding to ${targetGif.absolutePath}.")
                FileImageOutputStream(targetGif).use { output ->
                    log.debug("writing to ${GifSequenceWriter::class.java.name}.")
                    GifSequenceWriter(output, ImageIO.read(files[0]).type, timeBetweenFramesMS, false).use { writer ->
                        files.forEach {
                            log.debug("processing ${it.absolutePath}.")
                            writer.addToSequence(ImageIO.read(it))
                        }
                    }
                }
            }

            fun capture(out: File): Boolean {
                val robot = Robot()
                val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)
                val screenFullImage = robot.createScreenCapture(screenRect)
                return ImageIO.write(screenFullImage, "png", out)
            }

            fun captureUntil(fps: Int, imgDir: File, recordTest: () -> Boolean, executor: ExecutorService): Long {
                val intervalInMs: Long = ((1.0 / (fps * 1.0)) * 1000.0).toLong()
                val semaphore = Semaphore(0)
                val permits = AtomicLong()
                while (recordTest()) {
                    permits.incrementAndGet()
                    val id = permits.get()
                    log.debug("submitting task #${id}.")
                    executor.submit({
                        val fn = String.format("%05d", id)
                        val file = File(imgDir, "${fn}.png")
                        capture(file)
                        semaphore.release()
                        log.debug("processed ${file.absolutePath}.")
                    })
                    Thread.sleep(intervalInMs)
                }
                semaphore.acquire(permits.toInt())
                log.debug("finished submissions ${permits.toInt()}")
                return intervalInMs
            }

            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).also { executor ->
                val finish = Instant.now().plus(Duration.ofSeconds(2))
                log.debug("recording until ${finish}.")
                val intervalInMs = captureUntil(15, imgs, { Instant.now().isBefore(finish) }, executor)
                log.debug("interval in milliseconds: ${intervalInMs}.")
                val files = imgs
                        .listFiles(FileFilter {
                            it.extension == "png"
                        })
                        .sortedWith(Comparator { a, b -> a.path.compareTo(b.path) })

                transcode(files, intervalInMs, out)
                log.debug("wrote an animated gif to ${out.absolutePath}")
                executor.shutdown()
            }
        }
    }

    @Bean
    fun run(@Value("\${HOME}") home: File): ApplicationRunner {
        System.setProperty("java.awt.headless", "false")
        val target = File(File(home, "/Desktop"), "out").apply {
            deleteRecursively()
        }
        val imgs = File(target, "/captured/").apply {
            mkdirs()
        }
        val out = File(target, "/out.gif")
        return ScreenCaptureApplicationRunner(imgs, out)
    }
}

fun main(args: Array<String>) {
    runApplication<ScreencaptureApplication>(*args)
}
