package de.inovex.recognizecarswithtflite

import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel, where the recognition results will be stored and updated with each new image
 * analyzed by the Tensorflow Lite Model.
 */
class RecognitionListViewModel : ViewModel() {

    val recognitionList = MutableLiveData<List<Recognition>>()
    var color: Int? = R.color.blue
    var image: ImageProxy? = null

    fun updateData(recognitions: List<Recognition>, image: ImageProxy, color: Int?) {
        recognitionList.postValue(recognitions)
        this.image = image
        this.color = color
    }
}