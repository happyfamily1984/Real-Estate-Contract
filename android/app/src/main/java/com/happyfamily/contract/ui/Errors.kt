package com.happyfamily.contract.ui

import com.google.firebase.firestore.FirebaseFirestoreException

/** 저장·불러오기 오류를 사용자가 할 일이 보이게 바꾼다. */
fun friendlyError(e: Throwable): String {
    val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    return if (denied) {
        "저장이 거부됐습니다. Firebase 보안 규칙이 최신이 아닙니다. Firebase 콘솔 → Firestore → 규칙 탭에 " +
            "GitHub 의 최신 firestore.rules 를 붙여 넣고 '게시'를 눌러 주세요."
    } else {
        e.message ?: e.toString()
    }
}
