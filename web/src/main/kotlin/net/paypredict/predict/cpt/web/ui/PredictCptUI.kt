package net.paypredict.predict.cpt.web.ui

import com.vaadin.annotations.Push
import com.vaadin.annotations.Title
import com.vaadin.annotations.VaadinServletConfiguration
import com.vaadin.icons.VaadinIcons
import com.vaadin.server.VaadinRequest
import com.vaadin.server.VaadinServlet
import com.vaadin.shared.ui.ContentMode
import com.vaadin.ui.Alignment
import com.vaadin.ui.Notification
import com.vaadin.ui.UI
import com.vaadin.ui.themes.ValoTheme
import net.paypredict.predict.cpt.web.*
import net.paypredict.r.connection.RConnection
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger
import javax.json.*
import javax.servlet.annotation.WebServlet

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 2/3/2018.
 */
@Title("Predict CPT")
@Push
class PredictCptUI : UI() {
    private val cpt = comboBox<CPT>(placeholder = "CPT", width = "100%") {
        addValueChangeListener {
            doGetPayerList(value)
        }
    }

    private val payer = comboBox<Payer>(placeholder = "Payer", width = "100%") {
        addValueChangeListener {
            doGetPlanList(cpt.value, value)
        }
    }

    private val plan = comboBox<Plan>(placeholder = "Plan", width = "100%") {
        addValueChangeListener {
            doGetDxList(cpt.value, payer.value, value)
        }
    }

    private val dx = comboBox<DX>(placeholder = "DX", width = "100%")


    private val button = button("Predict") {
        addClickListener {
            doPredict(cpt.value, payer.value, plan.value, dx.value)
        }
    }

    private val predicted = horizontalLayout(height = "100%")

    private val predictor = Predictor {
        access {
            Notification.show(
                "Predictor error ${it.javaClass.simpleName}",
                it.message,
                Notification.Type.WARNING_MESSAGE
            )
        }
    }

    override fun init(request: VaadinRequest) {
        content = verticalLayout(width = "100%", height = "100%") {
            val body = this add panel(width = "100%", margin = false) {
                this add cpt
                this add payer
                this add plan
                this add dx
            }.apply {
                addStyleName(ValoTheme.PANEL_BORDERLESS)
            }
            this add horizontalLayout {
                this add button
                this add predicted
            }
            body.setSizeFull()
            setExpandRatio(body, 1f)
        }
        doGetCptList()
    }

    private fun doGetCptList() = predictor.asyncGetCptList {
        access {
            val old = cpt.value
            cpt.setItems(it)
            cpt.value = old
        }
    }

    private fun doGetPayerList(vararg items: Item?) {
        if (items.contains(null))
            payer.setItems(emptyList())
        else
            predictor.asyncGetPayerList(*items) {
                access {
                    val old = payer.value
                    payer.setItems(it)
                    payer.value = old
                }
            }
    }

    private fun doGetPlanList(vararg items: Item?) {
        if (items.contains(null))
            plan.setItems(emptyList())
        else
            predictor.asyncGetPlanList(*items) {
                access {
                    val old = plan.value
                    plan.setItems(it)
                    plan.value = old
                }
            }
    }

    private fun doGetDxList(vararg items: Item?) {
        if (items.contains(null))
            dx.setItems(emptyList())
        else
            predictor.asyncGetDxList(*items) {
                access {
                    val old = dx.value
                    dx.setItems(it)
                    dx.value = old
                }
            }
    }

    private fun doPredict(vararg items: Item?) {
        fun showPrediction(html: String, style: String) {
            val label = predicted replace label(html) {
                contentMode = ContentMode.HTML
                addStyleName(style)
            }
            predicted.setComponentAlignment(label, Alignment.MIDDLE_LEFT)
        }

        if (items.contains(null)) {
            val text = "Invalid Parameters"
            val style = ValoTheme.LABEL_FAILURE
            showPrediction(text, style)
        } else {
            showPrediction("Prediction...", ValoTheme.LABEL_COLORED)
            predictor.asyncPredict(*items) { risk ->
                access {
                    showPrediction(
                        when (risk.level) {
                            RiskLevel.High -> VaadinIcons.EXCLAMATION_CIRCLE.html + "&nbsp;" + (risk.reason ?: "High Risk")
                            RiskLevel.Low -> VaadinIcons.CHECK_CIRCLE.html + "&nbsp;" + (risk.reason ?: "Low Risk")
                        },
                        when (risk.level) {
                            RiskLevel.High -> ValoTheme.LABEL_FAILURE
                            RiskLevel.Low -> ValoTheme.LABEL_COLORED
                        }
                    )
                }
            }
        }
    }
}


@WebServlet(urlPatterns = ["/predict-cpt/*"], name = "PredictCptUI-Servlet", asyncSupported = true)
@VaadinServletConfiguration(ui = PredictCptUI::class, productionMode = false)
class PPUIServlet : VaadinServlet() {
    override fun destroy() {
        onDestroy.forEach { it() }
    }
}

private sealed class Item(val id: String, val name: String) {
    override fun toString(): String = id.padEnd(7, ' ') + "| $name"
}

private class CPT(id: String, name: String) : Item(id, name)
private class Payer(id: String, name: String) : Item(id, name)
private class Plan(id: String, name: String) : Item(id, name)
private class DX(id: String, name: String) : Item(id, name)

private enum class RiskLevel { High, Low }
private data class Risk(val level: RiskLevel, var reason: String?)


private val onDestroy: MutableList<() -> Unit> = CopyOnWriteArrayList()

private val predictorExecutor: ExecutorService by lazy {
    Executors.newSingleThreadExecutor().also { service ->
        onDestroy += { service.shutdown() }
    }
}

private val rConnection: RConnection by lazy {
    val ppHomeDir = File("/PayPredict")
    val dataDir = ppHomeDir.resolve("data")
    val sourceDir = ppHomeDir.resolve("paypredict-R")
    RConnection(
        env = mapOf(
            "PP_CPT_PORT" to "8000",
            "PP_CPT_ENV_FILE" to dataDir.resolve("env_cpt.Rdata").absolutePath
        ),
        sourceDir = sourceDir.resolve("ml/predict-cpt"),
        source = "plumber.run.R"
    ).also {
        onDestroy += { it.close() }
    }
}

private class Predictor(val onError: (Throwable) -> Unit) {
    private val logger: Logger = Logger.getLogger(javaClass.name)

    private fun call(path: String, builder: Request.Builder.() -> Unit = {}): Call =
        rConnection.call(path, builder)

    private fun asyncCall(call: () -> Unit) {
        predictorExecutor.submit {
            try {
                call()
            } catch (e: Throwable) {
                logger.log(Level.WARNING, "http call error", e)
                onError(e)
            }
        }
    }

    fun asyncGetCptList(onReady: (List<CPT>) -> Unit) {
        asyncCall {
            onReady(call("cpts", ::logRB).execute().use { response ->
                response.toJsonArray().map { value: JsonValue ->
                    val json = value as JsonObject
                    CPT(
                        json.getString("cpt", "?"),
                        json.getString("dsc", "?")
                    )
                }
            })
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun logRB(builder: Request.Builder) {
        logger.info(builder.build().url().uri().toASCIIString())
    }

    fun asyncGetPayerList(vararg items: Item?, onReady: (List<Payer>) -> Unit) {
        asyncCall {
            onReady(call(items.toPath("payers"), ::logRB).execute().use { response ->
                response.toJsonArray().map { value: JsonValue ->
                    val json = value as JsonObject
                    Payer(
                        json.getString("prid", "?"),
                        json.getString("dsc", "?")
                    )
                }
            })
        }
    }

    fun asyncGetPlanList(vararg items: Item?, onReady: (List<Plan>) -> Unit) {
        asyncCall {
            onReady(call(items.toPath("plans"), ::logRB).execute().use { response ->
                response.toJsonArray().map { value: JsonValue ->
                    val json = value as JsonObject
                    Plan(
                        json.getString("fCode", "?"),
                        json.getString("dsc", "?")
                    )
                }
            })
        }
    }

    fun asyncGetDxList(vararg items: Item?, onReady: (List<DX>) -> Unit) {
        asyncCall {
            onReady(call(items.toPath("dxs"), ::logRB).execute().use { response ->
                response.toJsonArray().map { value: JsonValue ->
                    val json = value as JsonObject
                    DX(
                        json.getString("Dx1", "?"),
                        json.getString("dsc", "?")
                    )
                }
            })
        }
    }

    fun asyncPredict(vararg items: Item?, onReady: (Risk) -> Unit) {
        asyncCall {
            onReady(call(items.toPath("predict"), ::logRB).execute().use { response ->
                val json = response.toJsonObject()
                val risk = json.getJsonArray("risk").firstOrNull() as JsonObject
                Risk(
                    RiskLevel.valueOf(risk.getString("Level", "?")),
                    risk.getString("Reason", null)
                )
            })
        }
    }

    private fun Array<out Item?>.toPath(base: String) =
        filterNotNull().joinToString(separator = "&", prefix = "$base?") {
            val name = when (it) {
                is CPT -> "cpt"
                is Payer -> "prid"
                is Plan -> "fCode"
                is DX -> "dx"
            }
            "$name=${it.id}"
        }

    private fun json(builder: JsonObjectBuilder.() -> Unit): JsonObject = Json
        .createObjectBuilder()
        .apply(builder)
        .build()

    private fun array(builder: JsonArrayBuilder.() -> Unit): JsonArray = Json
        .createArrayBuilder()
        .apply(builder)
        .build()

    private fun String.toJsonObject(): JsonObject = Json.createReader(reader()).readObject()
    private fun String.toJsonArray(): JsonArray = Json.createReader(reader()).readArray()

    private fun Response.toJsonObject(): JsonObject = body()?.string()?.toJsonObject() ?: json { }
    private fun Response.toJsonArray(): JsonArray = body()?.string()?.toJsonArray() ?: array { }
}
