package net.paypredict.predict.cpt.web

import com.vaadin.server.Resource
import com.vaadin.server.VaadinServlet
import com.vaadin.shared.ui.ContentMode
import com.vaadin.shared.ui.MarginInfo
import com.vaadin.ui.*
import javax.servlet.annotation.WebServlet

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 2/3/2018.
 */
@WebServlet(urlPatterns = ["/VAADIN/*"], name = "K1-Servlet", asyncSupported = true)
class K1Servlet : VaadinServlet()

internal inline infix fun <reified T : Component> ComponentContainer.add(component: T): T =
    component.also { addComponent(it) }

internal infix fun ComponentContainer.rem(component: Component) =
    removeComponent(component)

internal inline infix fun <reified T : Component> ComponentContainer.replace(component: T): T =
    component.also {
        removeAllComponents()
        addComponent(it)
    }

internal fun panel(
    caption: String? = null,
    width: String? = null,
    height: String? = null,
    sizeUndefined: Boolean? = null,
    margin: Boolean = true,
    marginInfo: MarginInfo? = null,
    build: VerticalLayout.() -> Unit = {}
) = Panel(caption).apply {
    if (sizeUndefined == true) setSizeUndefined()
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    content = verticalLayout(width = width, height = height, margin = margin, marginInfo = marginInfo)
        .apply { build(this) }
}

internal fun verticalLayout(
    width: String? = null,
    height: String? = null,
    sizeUndefined: Boolean? = null,
    margin: Boolean? = null,
    marginInfo: MarginInfo? = null,
    spacing: Boolean? = true,
    build: VerticalLayout.() -> Unit = {}
) = VerticalLayout().apply {
    if (sizeUndefined == true) setSizeUndefined()
    if (margin != null) setMargin(margin)
    if (marginInfo != null) setMargin(marginInfo)
    if (spacing != null) isSpacing = spacing
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    build(this)
}

internal fun horizontalLayout(
    width: String? = null,
    height: String? = null,
    sizeUndefined: Boolean? = null,
    margin: Boolean? = null,
    marginInfo: MarginInfo? = null,
    spacing: Boolean? = true,
    build: HorizontalLayout.() -> Unit = {}
) = HorizontalLayout().apply {
    if (sizeUndefined == true) setSizeUndefined()
    if (margin != null) setMargin(margin)
    if (marginInfo != null) setMargin(marginInfo)
    if (spacing != null) isSpacing = spacing
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    build(this)
}

internal fun textField(
    caption: String? = null,
    readOnly: Boolean? = null,
    width: String? = null,
    height: String? = null,
    build: TextField.() -> Unit = {}
) = TextField(caption).apply {
    if (readOnly != null) isReadOnly = readOnly
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    build(this)
}

internal fun textArea(
    caption: String? = null,
    readOnly: Boolean? = null,
    width: String? = null,
    height: String? = null,
    rows: Int? = null,
    build: TextArea.() -> Unit = {}
) = TextArea(caption).apply {
    if (readOnly != null) isReadOnly = readOnly
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    if (rows != null) this.rows = rows
    build(this)
}

internal fun label(
    text: String? = null,
    width: String? = null,
    height: String? = null,
    mode: ContentMode = ContentMode.TEXT,
    build: Label.() -> Unit = {}
) = Label(text).apply {
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    contentMode = mode
    build(this)
}

internal fun button(
    caption: String? = null,
    icon: Resource? = null,
    width: String? = null,
    height: String? = null,
    build: Button.() -> Unit = {}
) = Button(caption, icon).apply {
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    build(this)
}


internal inline fun <reified T> comboBox(
    caption: String? = null,
    readOnly: Boolean? = null,
    width: String? = null,
    height: String? = null,
    placeholder: String? = null,
    build: ComboBox<T>.() -> Unit = {}
) = ComboBox<T>(caption).apply {
    if (readOnly != null) isReadOnly = readOnly
    if (width != null) setWidth(width)
    if (height != null) setHeight(height)
    if (placeholder != null) this.placeholder = placeholder
    build(this)
}
