package com.happyfamily.contract.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.happyfamily.contract.ui.theme.ContractTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 디자인 확인용 화면 스크린샷 (build/outputs/roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(file: String, content: @Composable () -> Unit) {
        compose.setContent { ContractTheme { content() } }
        compose.onRoot().captureRoboImage(file)
    }

    @Test
    fun login() = shot("login.png") { LoginScreen() }

    @Test
    fun homeEmpty() = shot("home_empty.png") { Home(SampleData.homeState().copy(isEmpty = true)) }

    @Test
    fun homeFolders() = shot("home_folders.png") { Home(SampleData.homeState()) }

    @Test
    fun homeMerge() = shot("home_merge.png") { Home(SampleData.homeState(), selecting = setOf("f1", "f2")) }

    @Test
    fun folder() = shot("folder.png") {
        FolderContent(
            folder = SampleData.folders[0],
            contracts = SampleData.gwacheon,
            stakeholders = SampleData.stakeholders,
            newestFirst = false,
            upload = null,
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onScan = {}, onPickFiles = {}, onToggleSort = {}, onRename = {}, onSetStatus = {},
            onSetStakeholder = {}, onSaveSummary = {}, onApply = {}, onCopy = {}, onExport = {}, onDelete = {},
            onOpenContract = {},
        )
    }

    @Composable
    private fun Home(state: HomeState, selecting: Set<String>? = null) {
        HomeContent(
            state = state,
            query = "",
            onQueryChange = {},
            stakeholderFilter = null,
            onStakeholderFilter = {},
            upload = null,
            loadError = null,
            snackbar = remember { SnackbarHostState() },
            onPickFiles = {}, onScan = {}, onStakeholders = {}, onSignOut = {}, onOpenFolder = {}, onOpenContract = {},
            selecting = selecting,
        )
    }
}
