package com.example.cameraxplayground

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cameraxplayground.utils.CameraActionEnum

class PhotoViewModel : ViewModel() {

    private val _cameraActionLiveData = MutableLiveData<CameraActionEnum>()
    val cameraActionLiveData: LiveData<CameraActionEnum> = _cameraActionLiveData

    private val _cameraActionText = MutableLiveData<String>()
    val cameraActionText: LiveData<String> = _cameraActionText

    private val _progressValue = MutableLiveData<Int>(0)
    val progressValue:LiveData<Int> = _progressValue

    var cameraActionEnum: CameraActionEnum? = null


    private fun updateValue(cameraActionEnum: CameraActionEnum) {
        _cameraActionLiveData.value = cameraActionEnum
        this.cameraActionEnum = cameraActionEnum
        _cameraActionText.value = cameraActionEnum.title
    }


    private val originalList = listOf(CameraActionEnum.NODE_LEFT,CameraActionEnum.NODE_RIGHT,CameraActionEnum.EYE_BLINK,CameraActionEnum.SMILE)
    var shuffledList = mutableListOf<CameraActionEnum>()
    var counterValue = 0

    fun shuffleAndResetList(){
        shuffledList.clear()
        shuffledList = originalList.shuffled().toMutableList()
        counterValue = 0
        this.updateValue(shuffledList[0])
        _progressValue.value = 0
    }

    fun successExecution(){
        if (shuffledList.isNotEmpty()){
            counterValue +=1
            _progressValue.value = progressValue.value?.plus(25)
            shuffledList.removeAt(0)
            if (shuffledList.size !=0){
                this.updateValue(shuffledList[0])
            }
        }
    }

    fun resetAndShuffleList(){
        shuffledList.clear()
        shuffledList = originalList.shuffled().toMutableList()
        counterValue = 0
    }

}