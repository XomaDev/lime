package com.example.hellolime

import com.google.appinventor.components.annotations.DesignerComponent
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleObject
import com.google.appinventor.components.common.ComponentCategory
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.Form

@DesignerComponent(
  version = 1,
  category = ComponentCategory.EXTENSION,
  nonVisible = true
)
@SimpleObject(external = true)
class HelloLime(form: Form?) : AndroidNonvisibleComponent(form) {

  @SimpleFunction
  fun HelloWorld() {
    println("Hello, World!")
  }

  @SimpleEvent
  fun Meowd() {
    EventDispatcher.dispatchEvent(this, "Meowd")
  }
}