package com.posetracker.ui.connection

import android.os.Bundle
import androidx.navigation.NavDirections
import com.posetracker.R
import kotlin.Int
import kotlin.String

public class ConnectionFragmentDirections private constructor() {
  private data class ActionConnectionFragmentToPoseFragment(
    public val address: String,
    public val port: Int,
    public val armSide: String,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_connectionFragment_to_poseFragment

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putString("address", this.address)
        result.putInt("port", this.port)
        result.putString("armSide", this.armSide)
        return result
      }
  }

  public companion object {
    public fun actionConnectionFragmentToPoseFragment(
      address: String,
      port: Int,
      armSide: String,
    ): NavDirections = ActionConnectionFragmentToPoseFragment(address, port, armSide)
  }
}
