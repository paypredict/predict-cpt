package net.paypredict.predict.cpt.web.ui

import com.vaadin.annotations.Push
import com.vaadin.annotations.Title
import com.vaadin.annotations.VaadinServletConfiguration
import com.vaadin.icons.VaadinIcons
import com.vaadin.server.Page
import com.vaadin.server.VaadinRequest
import com.vaadin.server.VaadinServlet
import com.vaadin.ui.*
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
    private val cpt = comboBox<CPT>(placeholder = "Select CPT Code", width = "100%") {
        addValueChangeListener {
            doGetPayerList(value)
        }
    }

    private val payer = comboBox<Payer>(placeholder = "Select Insurance Payer", width = "100%") {
        addValueChangeListener {
            doGetPlanList(cpt.value, value)
        }
    }

    private val plan = comboBox<Plan>(placeholder = "Select Insurance Plan", width = "100%") {
        addValueChangeListener {
            doGetDxList(cpt.value, payer.value, value)
        }
    }

    private val dx = comboBox<DX>(placeholder = "Select Patient Diagnosis", width = "100%") {
        addValueChangeListener {
            hidePredictionResult()
        }
    }

    private val button = button("Predict Denial Risk") {
        addClickListener {
            doPredict(cpt.value, payer.value, plan.value, dx.value)
        }
    }

    private val predictionResult = verticalLayout(width = "100%", margin = false)

    private val predictor = Predictor {
        access {
            Notification.show(
                "Predictor error ${it.javaClass.simpleName}",
                it.message,
                Notification.Type.WARNING_MESSAGE
            )
        }
    }

    private fun showPredictionResult(vararg components: Component) {
        predictionResult.removeAllComponents()
        predictionResult.addComponents(*components)
    }

    private fun hidePredictionResult() {
        predictionResult.removeAllComponents()
    }

    private val enableOnFirstResponse = listOf<Component>(cpt, payer, plan, dx, button).also {
        it.forEach { it.isEnabled = false }
    }

    override fun init(request: VaadinRequest) {
        content = verticalLayout(width = "100%") {
            val body = this add verticalLayout(width = "100%", margin = false) {
                this add cpt
                this add payer
                this add plan
                this add dx
                this add horizontalLayout(width = "100%") {
                    this add button
                    setComponentAlignment(button, Alignment.MIDDLE_RIGHT)
                }
            }

            hidePredictionResult()
            this add predictionResult

            body.setSizeFull()
            setExpandRatio(body, 1f)
        }
        doGetCptList()
    }

    private fun doGetCptList() = predictor.asyncGetCptList {
        access {
            cpt.setItems(it)
            enableOnFirstResponse.forEach { it.isEnabled = true }
            cpt.focus()
        }
    }

    private fun doGetPayerList(vararg items: Item?) {
        if (items.contains(null))
            payer.updateItems(emptyList())
        else
            predictor.asyncGetPayerList(*items) { access { payer.updateItems(it) } }
    }

    private fun doGetPlanList(vararg items: Item?) {
        if (items.contains(null))
            plan.updateItems(emptyList())
        else
            predictor.asyncGetPlanList(*items) { access { plan.updateItems(it) } }
    }

    private fun doGetDxList(vararg items: Item?) {
        if (items.contains(null))
            dx.updateItems(emptyList())
        else
            predictor.asyncGetDxList(*items) { access { dx.updateItems(it) } }
    }

    private inline fun <reified T> ComboBox<T>.updateItems(items: List<T>) {
        value = null
        setItems(items)
    }

    private fun doPredict(vararg items: Item?) {
        fun Double.dollars(): String = "$%.2f".format(this)

        fun PredictionResult.Risk.toMedicareRateLabel(): Label? =//language=HTML
            medicareRate
                ?.let { labelHtml("Medicare Rate: <span style='font-weight: bold;'>${it.dollars()}</span>") }

        fun PredictionResult.Risk.toPrivateInsRateLabel(): Label? =//language=HTML
            privateInsRate
                ?.let { labelHtml("Private Ins Rate: <span style='font-weight: bold;'>${it.dollars()}</span>") }

        fun PredictionResult.Risk.toDenialRiskLabel(): Label = labelHtml(//language=HTML
            "Denial Risk: ${when (riskLabel) {
                "High" -> "<span style='color: white; font-weight: bold; background-color: red; border-radius: 4px'>"
                "Elevated" -> "<span style='color: white; font-weight: bold; background-color: orange; border-radius: 4px'>"
                "Low" -> "<span style='color: white; font-weight: bold; background-color: green; border-radius: 4px'>"
                else -> "<span style='color: white; font-weight: bold; background-color: blue; border-radius: 4px'>"
            }}&nbsp;$riskLabel&nbsp;(${(level * 100).toInt()}%)&nbsp;</span>"
        )

        fun PredictionResult.Risk.toDenialReasonLabel(): Label? =//language=HTML
            reasonName
                ?.let {
                    labelHtml(
                        "Denial Reason: <span style='font-weight: bold;'>$reason - $it</span>",
                        width = "100%"
                    )
                }

        fun buttonWithProductionNotification(caption: String, need: Boolean): Button =
            button(
                caption,
                icon = if (need) VaadinIcons.CHECK_SQUARE else VaadinIcons.THIN_SQUARE
            ) {
                addStyleName(ValoTheme.BUTTON_LINK)
                isCaptionAsHtml = true
                addClickListener {
                    Notification("Form available in production version")
                        .apply {
                            delayMsec = 3000
                            show(Page.getCurrent())
                        }
                }
            }

        fun PredictionResult.ABN.toComponent(): Component? =//language=HTML
            if (need) buttonWithProductionNotification("Need ABN" +
                    (amount?.let { " <span style='font-weight: bold;'>(${it.dollars()})</span>" } ?: ""), need)
            else null


        fun PredictionResult.Precertification.toComponent(): Component? =//language=HTML
            if (need) buttonWithProductionNotification("Need Precertification", need)
            else null

        hidePredictionResult()
        if (items.contains(null))
            Notification.show("Invalid Parameters", Notification.Type.WARNING_MESSAGE)
        else
            predictor.asyncPredict(*items) { result ->
                access {
                    showPredictionResult(
                        panel(width = "100%", margin = false) {
                            setMargin(true)

                            this add horizontalLayout(margin = false) {
                                addStyleName(ValoTheme.LAYOUT_HORIZONTAL_WRAPPING)

                                result.risk.toMedicareRateLabel() addTo this
                                result.risk.toPrivateInsRateLabel() addTo this
                                result.risk.toDenialRiskLabel() addTo this
                            }
                            result.risk.toDenialReasonLabel() addTo this
                        },
                        horizontalLayout(margin = false) {
                            addStyleName(ValoTheme.LAYOUT_HORIZONTAL_WRAPPING)

                            result.abn.toComponent() addTo this
                            result.precertification.toComponent() addTo this
                        })
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
private class Payer(id: String, name: String) : Item(id, name) {
    override fun toString(): String = name
}

private class Plan(id: String, name: String) : Item(id, name)
private class DX(id: String, name: String) : Item(id, name)


private data class PredictionResult(val risk: Risk, val abn: ABN, val precertification: Precertification) {
    data class Risk(
        val level: Double,
        val riskLabel: String?,
        var reason: String?,
        var reasonName: String?,
        var medicareRate: Double?,
        var privateInsRate: Double?
    )

    data class ABN(val need: Boolean, val amount: Double?)
    data class Precertification(val need: Boolean)
}

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
                        json.getString("NAME", "?")
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

    fun asyncPredict(vararg items: Item?, onReady: (PredictionResult) -> Unit) {
        asyncCall {
            onReady(call(items.toPath("predict"), ::logRB).execute().use { response ->
                val json = response.toJsonObject()
                val risk = json.getJsonArray("risk").firstOrNull() as JsonObject
                val needABN = json.getJsonArray("needABN").firstOrNull()
                val abnAmt = json.getJsonArray("abnAmt").firstOrNull() as? JsonNumber
                val needPrecert = json.getJsonArray("needPrecert").firstOrNull()
                PredictionResult(
                    risk = PredictionResult.Risk(
                        level = risk.getJsonNumber("Level")?.doubleValue() ?: 1.0,
                        riskLabel = risk.getString("RiskLabel", null),
                        reason = risk.getString("Reason", null),
                        reasonName = risk.getString("ReasonName", null),
                        medicareRate = risk.getJsonNumber("MC")?.doubleValue(),
                        privateInsRate = risk.getJsonNumber("Comm")?.doubleValue()
                    ),
                    abn = PredictionResult.ABN(
                        need = needABN == JsonValue.TRUE,
                        amount = abnAmt?.doubleValue()
                    ),
                    precertification = PredictionResult.Precertification(
                        need = needPrecert == JsonValue.TRUE
                    )
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
