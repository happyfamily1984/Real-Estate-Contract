package com.happyfamily.contract.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.happyfamily.contract.data.AnalyzeWorker
import com.happyfamily.contract.data.UpgradeWorker
import com.happyfamily.contract.data.ReminderWorker
import com.happyfamily.contract.data.Contract
import com.happyfamily.contract.data.ContractRepository
import com.happyfamily.contract.data.Folder
import com.happyfamily.contract.data.FolderRepository
import com.happyfamily.contract.data.FolderStatus
import com.happyfamily.contract.data.Stakeholder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 홈 화면에 보여 줄 내용 */
data class HomeState(
    /** 아직 폴더가 정해지지 않은(분석 중·실패) 계약서 */
    val unfiled: List<Contract>,
    val ongoing: List<Folder>,
    val done: List<Folder>,
    val contractCounts: Map<String, Int>,
    val stakeholders: List<Stakeholder>,
    val isEmpty: Boolean,
)

/** 홈 화면 보기 방식 */
enum class ViewMode(val value: String) { CARD("card"), LIST("list"), GRID("grid") }

/** 폴더 정렬 기준 */
enum class SortMode(val value: String, val label: String, val defaultDesc: Boolean) {
    NAME("name", "이름", false),
    ACQUIRED("acquired", "취득일", false),
    RECENT("recent", "최근계약", true),
}

/** 폴더 정렬에 쓰는 날짜들 */
object FolderSort {
    /** 취득날짜: 매매·분양·경매 계약의 취득일(없으면 계약일) 중 가장 빠른 것 */
    fun acquired(contracts: List<Contract>): String? = contracts.mapNotNull { c ->
        c.analysis?.takeIf { it.isSale }?.let { a -> a.acquisitionDate.ifBlank { a.contractDate }.ifBlank { null } }
    }.minOrNull()

    /** 최근계약: 가장 최근 계약일 */
    fun recent(contracts: List<Contract>): String? = contracts.mapNotNull { c ->
        c.sortDate.ifBlank { c.analysis?.representativeDate.orEmpty() }.ifBlank { null }
    }.maxOrNull()

    private val collator = java.text.Collator.getInstance(java.util.Locale.KOREAN)

    /** 정렬. 날짜를 모르는 폴더는 방향과 관계없이 맨 뒤 */
    fun sort(folders: List<Folder>, contracts: List<Contract>, mode: SortMode, desc: Boolean): List<Folder> {
        val byFolder = contracts.groupBy { it.folderId }
        if (mode == SortMode.NAME) {
            val sorted = folders.sortedWith { a, b -> collator.compare(a.name, b.name) }
            return if (desc) sorted.reversed() else sorted
        }
        val keyed = folders.map { f ->
            val list = byFolder[f.id].orEmpty()
            f to (if (mode == SortMode.ACQUIRED) acquired(list) else recent(list))
        }
        val known = keyed.filter { it.second != null }.sortedBy { it.second }.let { if (desc) it.reversed() else it }
        val unknown = keyed.filter { it.second == null }.sortedWith { a, b -> collator.compare(a.first.name, b.first.name) }
        return (known + unknown).map { it.first }
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val query = MutableStateFlow("")

    private val prefs = application.getSharedPreferences("home", android.content.Context.MODE_PRIVATE)

    /** 보기 방식: 사진 포함 / 목록 / 바둑판 */
    val viewMode = MutableStateFlow(
        ViewMode.entries.firstOrNull { it.value == prefs.getString("viewMode", null) }
            ?: if (prefs.getBoolean("compact", false)) ViewMode.LIST else ViewMode.CARD,
    )

    fun setViewMode(mode: ViewMode) {
        viewMode.value = mode
        prefs.edit().putString("viewMode", mode.value).apply()
    }

    val sortMode = MutableStateFlow(SortMode.entries.firstOrNull { it.value == prefs.getString("sort", null) } ?: SortMode.NAME)
    val sortDesc = MutableStateFlow(prefs.getBoolean("sortDesc", sortMode.value.defaultDesc))

    /** 기준을 바꾸면 그 기준의 기본 방향으로, 같은 기준을 다시 누르면 방향만 뒤집는다. */
    fun setSort(mode: SortMode) {
        val desc = if (mode == sortMode.value) !sortDesc.value else mode.defaultDesc
        sortMode.value = mode
        sortDesc.value = desc
        prefs.edit().putString("sort", mode.value).putBoolean("sortDesc", desc).apply()
    }

    fun toggleSortDirection() = setSort(sortMode.value)

    /** null 이면 전체, 아니면 그 이해관계자의 폴더만 */
    val stakeholderFilter = MutableStateFlow<String?>(null)

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private data class Filters(val query: String, val who: String?, val sort: SortMode, val desc: Boolean)

    private val filters = combine(query, stakeholderFilter, sortMode, sortDesc) { q, s, m, d -> Filters(q, s, m, d) }

    /** null 이면 아직 불러오는 중 */
    val state: StateFlow<HomeState?> = combine(
        FolderRepository.foldersFlow().catch { _loadError.value = friendlyLoadError(it) },
        ContractRepository.contractsFlow().catch { _loadError.value = friendlyLoadError(it) },
        FolderRepository.stakeholdersFlow().catch { emit(emptyList()) },
        filters,
    ) { folders, contracts, stakeholders, f ->
        val q = f.query
        val who = f.who
        val counts = contracts.mapNotNull { it.folderId }.groupingBy { it }.eachCount()
        val filtered = folders
            .filter { who == null || who in it.stakeholderNames }
            .filter { fo -> fo.matches(q) || contracts.any { it.folderId == fo.id && it.matches(q) } }
        val shown = FolderSort.sort(filtered, contracts, f.sort, f.desc)
        HomeState(
            unfiled = contracts.filter { it.folderId == null && it.matches(q) },
            ongoing = shown.filter { it.status == FolderStatus.ONGOING },
            done = shown.filter { it.status == FolderStatus.DONE },
            contractCounts = counts,
            stakeholders = stakeholders,
            isEmpty = folders.isEmpty() && contracts.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "분석 다시 시작" — 멈춘 것 같을 때 대기 중인 계약서를 모두 처음부터 다시 줄 세운다. */
    fun restartAnalysis() {
        viewModelScope.launch { runCatching { AnalyzeWorker.recoverStuck(getApplication(), force = true) } }
    }

    /** 같은 부동산 폴더 합치기. 끝나면 안내 문구를 [onDone] 으로 알려 준다. */
    fun merge(targetId: String, folderIds: List<String>, name: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val edited = FolderRepository.mergeFolders(getApplication(), targetId, folderIds, name)
                onDone(
                    if (edited) {
                        "폴더를 합쳤어요. 직접 고친 정리는 그대로 두었으니, 폴더에서 '계약서 내용적용'을 누르면 합친 내용으로 다시 정리합니다."
                    } else {
                        "폴더 ${folderIds.size}개를 합쳤어요. 계약내용정리를 다시 만들고 있어요."
                    },
                )
            } catch (e: Exception) {
                onDone("합치지 못했습니다: ${friendlyError(e)}")
            }
        }
    }

    init {
        // 폴더 기능 이전에 저장된 계약서를 폴더로 옮긴다.
        viewModelScope.launch { runCatching { FolderRepository.fileUnfiledContracts(getApplication()) } }
        // 앱이 꺼지거나 예전 버전에서 멈춘 분석을 다시 대기열에 넣는다.
        viewModelScope.launch { runCatching { AnalyzeWorker.recoverStuck(getApplication()) } }
        // AI 를 못 써서 기본 분석으로 끝난 계약서를 AI 를 쓸 수 있게 되면 다시 분석한다.
        UpgradeWorker.schedule(getApplication())
        // 계약 만기 등 일정 알림 (하루 한 번 확인)
        ReminderWorker.schedule(getApplication())
        viewModelScope.launch { runCatching { UpgradeWorker.upgradeNow(getApplication()) } }
    }
}

/** 목록을 못 불러왔을 때 사용자가 할 일을 알려 준다. */
fun friendlyLoadError(e: Throwable): String {
    val denied = e is com.google.firebase.firestore.FirebaseFirestoreException &&
        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
    return if (denied) {
        "Firebase 보안 규칙이 최신이 아닙니다.\n\nFirebase 콘솔 → Firestore Database → 규칙 탭에 " +
            "GitHub 저장소의 firestore.rules 내용을 통째로 붙여 넣고 '게시'를 누른 뒤 앱을 다시 여세요."
    } else {
        e.message ?: e.toString()
    }
}
