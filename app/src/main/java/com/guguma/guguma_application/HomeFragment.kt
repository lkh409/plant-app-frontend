package com.guguma.guguma_application

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.guguma.guguma_application.databinding.FragmentHomeBinding
import okhttp3.*
import org.json.JSONArray
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.guguma.guguma_application.dto.PlantDto
import com.guguma.guguma_application.viewmodel.PlantViewModel

//import com.guguma.guguma_application.viewmodel.PlantViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
     private val plantViewModel: PlantViewModel by activityViewModels()

    companion object {
        const val REQUEST_ADD_PLANT = 1001 // AddPlantActivity의 요청 코드
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // RecyclerView 초기화: 빈 어댑터로 설정
        binding.plantListView.layoutManager = LinearLayoutManager(requireContext())
        binding.plantListView.adapter = PlantAdapter(requireContext(), mutableListOf()) { plantId ->
            plantViewModel.deletePlant(plantId)
        }

        // LiveData를 observe하여 UI 업데이트
        plantViewModel.plantList.observe(viewLifecycleOwner) { updatedPlantList ->
            updateUI(updatedPlantList)
        }

        // 식물 추가 버튼 클릭 리스너
       // binding.pBtn.setOnClickListener {
            //val intent = Intent(activity, CreatePlantStartActivity::class.java)
        binding.pBtn.setOnClickListener {
            val intent = Intent(activity, CreatePlantStartActivity::class.java)
            startActivityForResult(intent, REQUEST_ADD_PLANT)
        }

        return binding.root

    }

    override fun onResume() {
        super.onResume()
        // Fragment가 다시 활성화될 때 데이터 새로고침
        plantViewModel.fetchPlantsFromServer() // 서버에서 최신 데이터를 가져옴
    }


    // AddPlantActivity에서 돌아왔을 때 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_PLANT && resultCode == Activity.RESULT_OK) {
            // AddPlantActivity에서 전달된 데이터
            val plantId = data?.getLongExtra("plantId", -1L) ?: -1L
            val newPlantName = data?.getStringExtra("newPlantName")
            val newPlantNickname = data?.getStringExtra("newPlantNickname")
            val newPlantImageUrl = data?.getStringExtra("newPlantImageUrl")

            if (plantId != -1L && !newPlantName.isNullOrEmpty() && !newPlantNickname.isNullOrEmpty() && !newPlantImageUrl.isNullOrEmpty()) {
                val newPlant = PlantDto(plantId, newPlantName, newPlantNickname, newPlantImageUrl)
                //val adapter = binding.plantListView.adapter as? PlantAdapter
                plantViewModel.addPlant(newPlant) // ViewModel에 데이터 추가
                //adapter?.addItem(newPlant) // RecyclerView에 새 데이터 추가
            }
        }
    }

    // UI 업데이트 메서드
    private fun updateUI(plantList: MutableList<PlantDto>) {
        if (binding.plantListView.adapter == null) {
            binding.plantListView.layoutManager = LinearLayoutManager(requireContext())
            binding.plantListView.adapter = PlantAdapter(requireContext(), plantList) { plantId ->
                plantViewModel.deletePlant(plantId)
            }
        } else {
            (binding.plantListView.adapter as PlantAdapter).updateData(plantList) // 어댑터 갱신
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 메모리 누수 방지
    }

}