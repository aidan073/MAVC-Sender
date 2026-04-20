package com.posetracker.ui.pose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.posetracker.databinding.FragmentPoseBinding
import com.posetracker.utils.PoseLandmarkerHelper
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    private var _binding: FragmentPoseBinding? = null
    private val binding get() = _binding!!

    private val args: PoseFragmentArgs by navArgs()
    private val viewModel: PoseViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private var imageAnalyzer: ImageAnalysis? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPoseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize ViewModel with connection info
        viewModel.initialize(args.address, args.port)

        binding.tvConnectionStatus.text = "Connected to ${args.address}:${args.port}"

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
            findNavController().popBackStack()
        }

        // Observe pose data being sent
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sendStatus.collect { status ->
                    binding.tvSendStatus.text = status
                }
            }
        }

        // Check / request camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = requireContext(),
            listener = this
        )

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        poseLandmarkerHelper.detectLiveStream(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ── PoseLandmarkerHelper.LandmarkerListener ──────────────────────────────

    override fun onResults(result: PoseLandmarkerHelper.PoseResult) {
        // Draw skeleton overlay
        binding.overlayView.setResults(
            result.landmarks,
            result.imageWidth,
            result.imageHeight
        )
        // Send over network
        viewModel.sendPoseData(result)
    }

    override fun onError(error: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Pose error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::poseLandmarkerHelper.isInitialized) poseLandmarkerHelper.close()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "PoseFragment"
    }
}
