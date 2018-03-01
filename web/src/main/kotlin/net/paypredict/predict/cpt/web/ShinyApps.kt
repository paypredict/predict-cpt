package net.paypredict.predict.cpt.web

import net.paypredict.r.connection.R
import java.io.File
import java.io.IOException
import javax.json.Json
import javax.json.JsonObject
import javax.servlet.ServletContext
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 2/15/2018.
 */

private const val shinyAppJsonFileName = "shiny-app.json"

@WebServlet(urlPatterns = ["/shiny-apps/*"], name = "ShinyApps-Servlet", loadOnStartup = 42)
class ShinyAppsServlet : HttpServlet() {
    private val server: ShinyServer by lazy { ShinyServer(servletContext) }

    private val apps: List<ShinyApp> by lazy {
        servletContext.safeCall("scanning apps dir") {
            server.appsDir
                .walk()
                .filter { it.isFile && it.name == shinyAppJsonFileName }
                .map { ShinyApp(server, it.parentFile) }
                .toList()
        } ?: emptyList()

    }

    override fun init() {
        apps.forEach {
            servletContext.safeCall("starting app ${it.name}") {
                it.start()
            }
        }
    }

    override fun destroy() {
        apps.forEach {
            servletContext.safeCall("stopping app ${it.name}") {
                it.stop()
            }
        }
    }
}


private inline fun <reified T> ServletContext.safeCall(errorContext: String = "processing", function: () -> T): T? =
    try {
        function()
    } catch (e: Throwable) {
        log("Error on $errorContext", e)
        null
    }

private class ShinyServer(val logger: ServletContext) {
    val appsDir = PayPredict.homeDirectory.resolve("shiny-apps")
    val host: String get() = conf?.getString("host", null) ?: "0.0.0.0"
    val pandoc: String get() = conf?.getString("pandoc", null) ?: throw IOException("invalid pandoc directory")

    val conf: JsonObject? by lazy {
        logger.safeCall("loading $appsDir server json") {
            val confFile = appsDir.resolve("shiny-apps-server.json")
            when {
                confFile.isFile -> Json.createReader(confFile.reader()).use { it.readObject() }
                else -> Json.createObjectBuilder().apply {
                    logger.log("$confFile not found -> applying default configuration")
                    System.getenv("LOCALAPPDATA").let {
                        val pandocDir = when (it) {
                            null -> File("/usr/bin")
                            else -> File(it, "Pandoc")
                        }
                        logger.log("trying pandocDir -> $pandocDir")
                        if (pandocDir.isDirectory)
                            add("pandoc", pandocDir.absolutePath)
                    }
                }.build()
            }
        }
    }
}

private class ShinyApp(val server: ShinyServer, val dir: File) {
    val isAlive: Boolean get() = process?.isAlive ?: false
    val isRunnable: Boolean get() = port != null
    val mode: String get() = json?.getString("mode", null) ?: "shiny::runApp"
    val name: String get() = dir.name
    val port: String? get() = json?.getString("port", null)
    val host: String get() = json?.getString("host", null) ?: server.host
    val rmd: String get() = json?.getString("rmd", null) ?: "$name.Rmd"
    val appDir: String get() = dir.toSafePath()

    val json: JsonObject? by lazy {
        server.logger.safeCall("loading $name app json") {
            Json.createReader(dir.resolve(shinyAppJsonFileName).reader()).use { it.readObject() }
        }
    }


    private var process: Process? = null
    private var sourceFile: File? = null

    fun start() {
        if (isRunnable) {
            val env = mutableMapOf<String, String>()
            val source: File = File.createTempFile(name + ".", ".R")
                .apply {
                    when (mode) {
                        "shiny::runApp" -> {
                            writeText(
                                """ shiny::runApp(
                                        appDir = "$appDir",
                                        port = "$port",
                                        host = "$host"
                                    )
                                """
                            )
                        }
                        "rmarkdown::run" -> {
                            env["RSTUDIO_PANDOC"] = server.pandoc
                            writeText(
                                """ rmarkdown::run(
                                        "$rmd",
                                        shiny_args = list(
                                            host="$host",
                                            port=$port)
                                        )
                                """
                            )
                        }
                    }
                }
            sourceFile = source
            process = R.start(
                "-e", "source('${source.toSafePath()}')", "--no-save",
                directory = dir,
                env = env
            )
        }
    }

    fun stop() {
        try {
            sourceFile?.delete()
            process?.destroy()
        } finally {
            sourceFile = null
            process = null
        }
    }

}

private fun File.toSafePath(): String = absoluteFile.normalize().absolutePath.replace('\\', '/')
