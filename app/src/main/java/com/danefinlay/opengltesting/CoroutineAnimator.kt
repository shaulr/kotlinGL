package com.danefinlay.opengltesting

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch

/**
 * Created by shaulr on 13/09/2017.
 */
class CoroutineAnimator(fps: Int) {
    val period = 1000 / fps
    fun startAnimating( animatable: IAnimatable) {
        val startTime = System.currentTimeMillis()
        val job = launch(CommonPool) {
            var nextFrameTime = startTime
            var i = 0
            while (isActive) { // cancellable computation loop
                // print a message twice a second
                if (System.currentTimeMillis() >= nextFrameTime) {
                    animatable.doFrame()
                    nextFrameTime += period
                }
            }
        }
    }
}