package net.paypredict.predict.cpt.web

import java.net.URI
import javax.json.Json
import javax.json.JsonObject
import javax.servlet.*
import javax.servlet.annotation.WebFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 2/12/2018.
 */
@WebFilter(
    value = ["*"],
    filterName = "Redirects"
)
class Redirects : Filter {
    private data class Rule(val port: String, val toPort: String, val toSchema: String)

    private val rules: Map<String, Rule> by lazy {
        mutableMapOf<String, Rule>().also { result ->
            PayPredict
                .homeDirectory
                .resolve(javaClass.name + ".json")
                .let { file ->
                    if (file.isFile)
                        Json.createReader(file.bufferedReader()).use { it.readObject() }
                    else
                        Json.createReader(// language=JSON
                            """{ "rules": [
                           {"port":"80", "toPort": "", "toSchema": "https"},
                           {"port":"8080", "toPort": "", "toSchema": "https"},
                           {"port":"8443", "toPort": "", "toSchema": "https"}
                         ] }""".reader()
                        ).readObject()
                }
                .getJsonArray("redirects")
                .filterIsInstance<JsonObject>()
                .forEach {
                    Rule(
                        port = it.getString("port", ""),
                        toPort = it.getString("toPort", ""),
                        toSchema = it.getString("toSchema", "")
                    ).also {
                        result[it.port] = it
                    }
                }
        }
    }

    override fun doFilter(request0: ServletRequest, response0: ServletResponse, chain: FilterChain) {
        val request = request0 as? HttpServletRequest
        val response = response0 as? HttpServletResponse
        if (request != null && response != null) {
            request.getHeader("Host")?.let { fromHost ->
                val fromPort = fromHost.substringAfter(":")
                val redirect = rules[fromPort]
                if (redirect != null) {
                    val uri = URI(request.requestURL.toString())
                    val server = uri.host
                    val path = uri.path
                    val queryString = request.queryString.let {
                        when (it) {
                            null -> ""
                            else -> "?$it"
                        }
                    }
                    val toSchema = redirect.toSchema.let {
                        when {
                            it.isNotEmpty() -> it
                            else -> uri.scheme
                        }
                    }
                    val toPort = redirect.toPort.let {
                        when {
                            it.isNotEmpty() -> ":$it"
                            else -> ""
                        }
                    }
                    response.sendRedirect(toSchema + "://" + server + toPort + path + queryString)
                } else {
                    chain.doFilter(request0, response0)
                }
            }
        } else {
            chain.doFilter(request0, response0)
        }
    }

    override fun init(filterConfig: FilterConfig) {
    }

    override fun destroy() {

    }

}