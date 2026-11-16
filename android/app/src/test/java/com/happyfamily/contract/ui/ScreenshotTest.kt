package com.happyfamily.contract.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.happyfamily.contract.data.Agent
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractAnalysis
import com.happyfamily.contract.data.ContractStatus
import com.happyfamily.contract.data.Party
import com.happyfamily.contract.data.Payment
import com.happyfamily.contract.ui.theme.ContractTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

/** 디자인 시안과 비교하기 위한 화면 스크린샷 (build/outputs/roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun login() {
        compose.setContent { ContractTheme { LoginScreen() } }
        compose.onRoot().captureRoboImage("login.png")
    }

    @Test
    fun listEmpty() = list("list_empty.png", emptyList())

    @Test
    fun listFilled() = list("list_filled.png", sampleContracts())

    private fun list(file: String, contracts: List<Contract>) {
        compose.setContent {
            ContractTheme {
                ContractListContent(
                    contracts = contracts,
                    query = "",
                    onQueryChange = {},
                    upload = null,
                    loadError = null,
                    snackbar = remember { SnackbarHostState() },
                    onGallery = {},
                    onScan = {},
                    onSignOut = {},
                    onOpen = {},
                )
            }
        }
        compose.onRoot().captureRoboImage(file)
    }

    private fun sampleContracts(): List<Contract> {
        val empty = Party("", "", "")
        fun analysis(type: String, address: String, deposit: Long, rent: Long, sale: Long) = ContractAnalysis(
            contractType = type, propertyType = "아파트", propertyAddress = address, area = "",
            contractDate = "2026-03-02", leaseStart = "", leaseEnd = "", salePrice = sale,
            deposit = deposit, monthlyRent = rent, managementFee = 0,
            downPayment = Payment(0, ""), middlePayments = emptyList(), balancePayment = Payment(0, ""),
            lessor = empty, lessee = empty, agent = Agent("", "", "", "", ""),
            specialTerms = emptyList(), summary = "", cautions = emptyList(),
        )
        fun contract(id: String, status: ContractStatus, title: String, type: String, a: ContractAnalysis?) =
            Contract(id, status, title, type, 3, null, "", null, Date(), a)
        return listOf(
            contract("1", ContractStatus.PROCESSING, "분석 중인 계약서", "", null),
            contract("2", ContractStatus.DONE, "서울특별시 마포구 월드컵북로 400, 101동 1203호", "전세",
                analysis("전세", "", 350_000_000, 0, 0)),
            contract("3", ContractStatus.DONE, "경기도 성남시 분당구 정자일로 95", "월세",
                analysis("월세", "", 20_000_000, 850_000, 0)),
            contract("4", ContractStatus.ERROR, "분석 중인 계약서", "", null),
        )
    }
}
