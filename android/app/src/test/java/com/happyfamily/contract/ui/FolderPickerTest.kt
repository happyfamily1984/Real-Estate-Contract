package com.happyfamily.contract.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FolderPickerTest {
    @get:Rule val compose = createComposeRule()

    private val picks = mutableListOf<Pair<String?, String>>()
    private var dismissed = 0

    private fun show(current: String?, newName: String) = compose.setContent {
        FolderPickerDialog("어느 폴더로 옮길까요?", SampleData.folders, current, newName, { dismissed++ }, { id, n -> picks += id to n })
    }

    @Test
    fun `폴더를 누르기만 해서는 안 옮겨지고 옮기기를 눌러야 옮겨진다`() {
        show("f1", "새 이름")
        compose.onNodeWithText("목동 건영").performClick()
        assertEquals(emptyList<Pair<String?, String>>(), picks)
        compose.onNodeWithText("옮기기").performClick()
        assertEquals(listOf<Pair<String?, String>>("f2" to ""), picks)
    }

    @Test
    fun `새 폴더를 고르고 옮기기를 누르면 새 폴더 이름으로`() {
        show("f1", "과천 온은빌 201호")
        compose.onNodeWithText("새 폴더 만들어 옮기기").performClick()
        compose.onNodeWithText("옮기기").performClick()
        assertEquals(listOf<Pair<String?, String>>(null to "과천 온은빌 201호"), picks)
    }

    @Test
    fun `새 폴더 이름이 이미 있는 폴더와 같으면 그 폴더로`() {
        show(null, "목동 건영")
        compose.onNodeWithText("옮기기").performClick()
        assertEquals(listOf<Pair<String?, String>>("f2" to ""), picks)
    }

    @Test
    fun `닫기는 옮기지 않는다`() {
        show("f1", "새 이름")
        compose.onNodeWithText("닫기").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, picks.size)
    }
}
