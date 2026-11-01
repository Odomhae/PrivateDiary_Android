package com.odom.privatediary.lock

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface AuthResult {
    data object Success : AuthResult
    /** 사용자 취소·시도 횟수 초과 등. [message]는 시스템이 준 문구. */
    data class Error(val message: String) : AuthResult
}

object BiometricAuth {
    /**
     * 지문·얼굴 또는 기기 PIN으로 인증한다. DEVICE_CREDENTIAL을 허용하므로 취소 버튼은 시스템이 그린다.
     * 인식 실패(onAuthenticationFailed)는 프롬프트가 계속 떠 있으므로 결과로 보내지 않는다.
     */
    fun authenticate(activity: FragmentActivity, title: String, onResult: (AuthResult) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(AuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(AuthResult.Error(errString.toString()))
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
