package org.autojs.autojs.core.ui.attribute

import android.view.View
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.JsQuickContactBadge
import org.autojs.autojs.runtime.ScriptRuntime

class JsQuickContactBadgeAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : QuickContactBadgeAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as JsQuickContactBadge

}