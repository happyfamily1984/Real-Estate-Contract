package com.happyfamily.contract.data

import com.happyfamily.contract.ui.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestFolderTest {
    private val folders = SampleData.folders // f1 과천 미소가(주소: 경기도 과천시 별양동 미소가 101동 1203호), f2 목동 건영

    @Test
    fun `AI 가 고른 폴더가 있으면 그 폴더`() {
        val c = SampleData.contract("n1", SampleData.analysis("월세", "2025-01-01", "홍길동", "새세입"), folderId = null)
        assertEquals("f2", FolderRepository.suggestFolder(c, "f2", folders))
    }

    @Test
    fun `주소가 같으면 기존 폴더 (띄어쓰기 달라도)`() {
        val a = SampleData.analysis("월세", "2025-01-01", "홍길동", "새세입").copy(propertyAddress = "경기도 과천시 별양동 미소가 101동1203호")
        val c = SampleData.contract("n2", a, folderId = null)
        assertEquals("f1", FolderRepository.suggestFolder(c, null, folders))
    }

    @Test
    fun `처음 보는 부동산이면 새 폴더 제안 (기존 폴더 없음)`() {
        val a = SampleData.analysis("월세", "2025-01-01", "홍길동", "새세입").copy(propertyAddress = "경기도 과천시 부림동 35-5 온은빌 201호", folderName = "과천 온은빌 201호")
        val c = SampleData.contract("n3", a, folderId = null)
        assertNull(FolderRepository.suggestFolder(c, null, folders))
    }
}
