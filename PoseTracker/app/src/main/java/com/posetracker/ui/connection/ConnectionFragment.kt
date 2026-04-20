package com.posetracker.ui.connection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.posetracker.R
import com.posetracker.databinding.FragmentConnectionBinding
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConnectionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore last-used values if available
        viewModel.lastAddress?.let { binding.etAddress.setText(it) }
        viewModel.lastPort?.let { binding.etPort.setText(it) }

        binding.btnConnect.setOnClickListener {
            val address = binding.etAddress.text.toString().trim()
            val portStr = binding.etPort.text.toString().trim()

            if (address.isEmpty()) {
                binding.tilAddress.error = "Address is required"
                return@setOnClickListener
            } else {
                binding.tilAddress.error = null
            }

            if (portStr.isEmpty()) {
                binding.tilPort.error = "Port is required"
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull()
            if (port == null || port !in 1..65535) {
                binding.tilPort.error = "Enter a valid port (1–65535)"
                return@setOnClickListener
            } else {
                binding.tilPort.error = null
            }

            viewModel.connect(address, port)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ConnectionUiState.Idle -> {
                            binding.progressBar.isVisible = false
                            binding.btnConnect.isEnabled = true
                        }
                        is ConnectionUiState.Connecting -> {
                            binding.progressBar.isVisible = true
                            binding.btnConnect.isEnabled = false
                        }
                        is ConnectionUiState.Connected -> {
                            binding.progressBar.isVisible = false
                            binding.btnConnect.isEnabled = true
                            // Navigate to pose tracking screen, passing connection info
                            val action = ConnectionFragmentDirections
                                .actionConnectionFragmentToPoseFragment(
                                    address = state.address,
                                    port = state.port
                                )
                            findNavController().navigate(action)
                            viewModel.resetState()
                        }
                        is ConnectionUiState.Error -> {
                            binding.progressBar.isVisible = false
                            binding.btnConnect.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
