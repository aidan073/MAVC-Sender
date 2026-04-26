package com.posetracker.ui.pose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.posetracker.databinding.FragmentPoseBinding
import com.posetracker.utils.HandLandmarkerHelper
import com.posetracker.utils.PoseLandmarkerHelper
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseFragment : Fragment(),
    PoseLandmarkerHelper.LandmarkerListener,
    HandLandmarkerHelper.HandListener {

    private var _binding: FragmentPoseBinding? = null
    private val binding get() = _binding!!

    private val args: PoseFragmentArgs by navArgs()
    private val viewModel: PoseViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var handLandmarkerHelper: HandLandmarkerHelper? = null

    private var useFrontCamera = true

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

        val armSide = ArmSide.valueOf(args.armSide)
        viewModel.armSide = armSide
        viewModel.initialize(args.address, args.port)

        binding.tvConnectionStatus.text =
            "Connected to ${args.address}:${args.port} — ${armSide.name.lowercase()} arm"

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
            findNavController().popBackStack()
        }

        binding.btnFlipCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            // Recreate MediaPipe helpers so mirror flip logic resets correctly
            cameraExecutor.execute {
                poseLandmarkerHelper?.close()
                poseLandmarkerHelper = null
                handLandmarkerHelper?.close()
                handLandmarkerHelper = null
            }
            startCamera()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sendStatus.collect { binding.tvSendStatus.text = it }
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val appContext = requireContext().applicationContext
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val cameraSelector = if (useFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (poseLandmarkerHelper == null) {
                            poseLandmarkerHelper = PoseLandmarkerHelper(
                                context  = appContext,
                                isFrontCamera = useFrontCamera,
                                listener = this
                            )
                        }
                        if (handLandmarkerHelper == null) {
                            handLandmarkerHelper = HandLandmarkerHelper(
                                context        = appContext,
                                targetHandSide = viewModel.armSide.name,
                                listener       = this
                            )
                        }
                        val bitmap = poseLandmarkerHelper?.detectLiveStream(imageProxy)
                            ?: run { imageProxy.close(); return@setAnalyzer }
                        handLandmarkerHelper?.detectFromBitmap(bitmap, SystemClock.uptimeMillis())
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun onResults(result: PoseLandmarkerHelper.PoseResult) {
        binding.overlayView.setResults(result.landmarks, result.imageWidth, result.imageHeight)
        viewModel.sendPoseData(result)
    }

    override fun onError(error: String) {
        Log.e(TAG, "Pose error: $error")
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onHandResult(isClosed: Boolean) {
        viewModel.updateHandState(isClosed)
    }

    override fun onHandError(error: String) {
        Log.e(TAG, "Hand error: $error")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.execute {
            poseLandmarkerHelper?.close()
            poseLandmarkerHelper = null
            handLandmarkerHelper?.close()
            handLandmarkerHelper = null
        }
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "PoseFragment"
    }
}
