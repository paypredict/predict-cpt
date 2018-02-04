package net.paypredict.r.connection

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import kotlin.concurrent.withLock

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 2/3/2018.
 */
class RConnection(
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val env: Map<String, String> = emptyMap(),
    private val sourceDir: File = File("."),
    private val source: String
) : Closeable {

    private val logger: Logger = Logger.getLogger(javaClass.name)
    private val server: String get() = "http://$host:$port"
    private val lock = ReentrantLock()
    private var process: Process? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun call(path: String, builder: Request.Builder.() -> Unit = {}): Call {
        autoStartProcess()
        return call0(path, builder)
    }

    private fun call0(path: String, builder: Request.Builder.() -> Unit = {}): Call = okHttpClient.newCall(
        Request.Builder()
            .url("$server/$path")
            .apply(builder)
            .build()
    )

    private fun autoStartProcess() {
        lock.withLock {
            val isAlive = process?.isAlive ?: false
            if (!isAlive) {
                exitProcess()
                startProcess()
            }
        }
    }

    private fun startProcess() {
        assert(process == null)
        val args = arrayOf("-e", "source('$source')", "--no-save")
        logger.info("staring R: R " + args.joinToString(separator = " ") { "\"$it\"" })
        process = R.start(*args, directory = sourceDir, env = env)

        for (i in 1..10) {
            try {
                val code = call0("").execute().code()
                if (code == 404) return
            } catch (e: java.net.ConnectException) {
            }
            Thread.sleep(1000)
        }
        exitProcess()
        throw IOException("Unable to start R process")
    }

    private fun exitProcess() {
        logger.info("exiting R")
        process?.let {
            process = null
            try {
                call0("exit").execute()
                Thread.sleep(500)
                if (it.isAlive) {
                    logger.warning("exiting R: exit command has failed -> killing process")
                    it.destroy()
                }
            } catch (e: java.net.ConnectException) {
            }
            logger.info("exiting R: DONE")
        }
    }

    override fun close() {
        lock.withLock { exitProcess() }
    }
}

private object R {
    val binPath: String? by lazy {
        System.getProperty("R.binPath", null) ?: installPath?.let {
            File(it).resolve("bin").absolutePath
        }
    }

    val installPath: String? by lazy {
        fun windowsInstallPath(): String? = System.getProperty("R.installPath", null) ?: ProcessBuilder().run {
            command("reg", "query", "HKLM\\Software\\R-core\\R")
            val outFile = File.createTempFile("R.installPath.", ".tmp")
            try {
                redirectOutput(outFile)
                start().also { process ->
                    if (!process.waitFor(30, TimeUnit.SECONDS)) {
                        process.destroy()
                        throw IOException("${command()} timed out")
                    }
                }
                outFile.useLines {
                    for (line in it) {
                        val entry = line.trim().split("    REG_SZ    ")
                        if (entry.size == 2 && entry[0] == "InstallPath") return entry[1]
                    }
                }
            } finally {
                outFile.delete()
            }
            null
        }

        windowsInstallPath()
    }

    private val pathSeparator: String = File.pathSeparator

    val libsUser: Set<String> by lazy {
        System.getProperty("R.libsUser", null)
            ?.split(pathSeparator)
            ?.toSet()
                ?: emptySet()
    }

    fun start(
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        directory: File = File(".").absoluteFile,
        rLibsUser: Set<String> = libsUser
    ): Process {
        val rExe = binPath?.let {
            File(it)
                .resolve("R.exe")
                .absoluteFile
                .normalize()
        } ?: throw IOException("R not found")

        return ProcessBuilder()
            .apply {
                directory(directory)
                command(rExe.absolutePath)
                command() += args
                if (rLibsUser.isNotEmpty()) {
                    environment()["R_LIBS_USER"] = rLibsUser.joinToString(separator = pathSeparator)
                }
                environment().putAll(env)
                redirectOutput(ProcessBuilder.Redirect.INHERIT)
                redirectError(ProcessBuilder.Redirect.INHERIT)
            }
            .start()
    }
}