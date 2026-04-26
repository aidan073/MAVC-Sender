package com.posetracker.ui.pose

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Int
import kotlin.String
import kotlin.jvm.JvmStatic

public data class PoseFragmentArgs(
  public val address: String,
  public val port: Int,
  public val armSide: String,
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putString("address", this.address)
    result.putInt("port", this.port)
    result.putString("armSide", this.armSide)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("address", this.address)
    result.set("port", this.port)
    result.set("armSide", this.armSide)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): PoseFragmentArgs {
      bundle.setClassLoader(PoseFragmentArgs::class.java.classLoader)
      val __address : String?
      if (bundle.containsKey("address")) {
        __address = bundle.getString("address")
        if (__address == null) {
          throw IllegalArgumentException("Argument \"address\" is marked as non-null but was passed a null value.")
        }
      } else {
        throw IllegalArgumentException("Required argument \"address\" is missing and does not have an android:defaultValue")
      }
      val __port : Int
      if (bundle.containsKey("port")) {
        __port = bundle.getInt("port")
      } else {
        throw IllegalArgumentException("Required argument \"port\" is missing and does not have an android:defaultValue")
      }
      val __armSide : String?
      if (bundle.containsKey("armSide")) {
        __armSide = bundle.getString("armSide")
        if (__armSide == null) {
          throw IllegalArgumentException("Argument \"armSide\" is marked as non-null but was passed a null value.")
        }
      } else {
        throw IllegalArgumentException("Required argument \"armSide\" is missing and does not have an android:defaultValue")
      }
      return PoseFragmentArgs(__address, __port, __armSide)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): PoseFragmentArgs {
      val __address : String?
      if (savedStateHandle.contains("address")) {
        __address = savedStateHandle["address"]
        if (__address == null) {
          throw IllegalArgumentException("Argument \"address\" is marked as non-null but was passed a null value")
        }
      } else {
        throw IllegalArgumentException("Required argument \"address\" is missing and does not have an android:defaultValue")
      }
      val __port : Int?
      if (savedStateHandle.contains("port")) {
        __port = savedStateHandle["port"]
        if (__port == null) {
          throw IllegalArgumentException("Argument \"port\" of type integer does not support null values")
        }
      } else {
        throw IllegalArgumentException("Required argument \"port\" is missing and does not have an android:defaultValue")
      }
      val __armSide : String?
      if (savedStateHandle.contains("armSide")) {
        __armSide = savedStateHandle["armSide"]
        if (__armSide == null) {
          throw IllegalArgumentException("Argument \"armSide\" is marked as non-null but was passed a null value")
        }
      } else {
        throw IllegalArgumentException("Required argument \"armSide\" is missing and does not have an android:defaultValue")
      }
      return PoseFragmentArgs(__address, __port, __armSide)
    }
  }
}
