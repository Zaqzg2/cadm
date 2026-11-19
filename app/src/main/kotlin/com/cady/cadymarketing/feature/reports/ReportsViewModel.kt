package com.cady.cadymarketing.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.report.OrdersRefreshReport
import com.cady.cadymarketing.data.report.ReportException
import com.cady.cadymarketing.data.report.ReportRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.domain.engine.CampaignRules
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.ReportEngine
import com.cady.cadymarketing.domain.engine.ReportSources
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ManualResult
import com.cady.cadymarketing.domain.model.ReportSummary
import com.cady.cadymarketing.domain.model.ResultMetric
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The period a report covers, counted back from today. */
enum class ReportPeriod(val label: String, val days: Int?) {
    WEEK("٧ أيام", 7),
    MONTH("٣٠ يومًا", 30),
    ALL("الكل", null),
}

data class ReportsState(
    val loaded: Boolean = false,
    val period: ReportPeriod = ReportPeriod.MONTH,
    val summary: ReportSummary? = null,
    /** Every result written by hand (the campaign cards show the ones of their campaign). */
    val results: List<ManualResult> = emptyList(),
    val refreshing: Boolean = false,
    val lastRefresh: Long? = null,
    val report: OrdersRefreshReport? = null,
    val sources: ReportSources = ReportSources(),
    val isPreview: Boolean = false,
    val expandedId: String? = null,
)

private class Computed(val summary: ReportSummary, val results: List<ManualResult>, val period: ReportPeriod)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reports: ReportRepository,
    campaigns: CampaignRepository,
    content: ContentRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.MONTH)

    private val _state = MutableStateFlow(ReportsState(isPreview = reports.isPreview))
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                campaigns.observeAll(),
                content.observeAll(),
                reports.observeOrders(),
                reports.observeResults(),
                period,
            ) { campaignList, posts, orders, results, selected ->
                val range = selected.days?.let { ReportEngine.lastDays(DateUtil.today(), it) }
                Computed(
                    summary = ReportEngine.compute(campaignList, posts, orders, results, range?.first, range?.second),
                    results = results,
                    period = selected,
                )
            }.flowOn(Dispatchers.Default).collect { computed ->
                _state.update { it.copy(loaded = true, summary = computed.summary, results = computed.results, period = computed.period) }
            }
        }
        viewModelScope.launch {
            reports.observeLastRefresh().collect { at -> _state.update { it.copy(lastRefresh = at) } }
        }
        viewModelScope.launch {
            settings.reportSources.collect { sources -> _state.update { it.copy(sources = sources) } }
        }
        viewModelScope.launch { reports.refreshIfStale(STALE_MS) }
    }

    fun setPeriod(value: ReportPeriod) {
        period.value = value
    }

    fun toggle(campaignId: String) {
        _state.update { it.copy(expandedId = if (it.expandedId == campaignId) null else campaignId) }
    }

    /** Reads the orders again. The answer says what was read from each source, and what went wrong with any that failed. */
    fun refresh() {
        if (_state.value.refreshing) return
        if (reports.isPreview) {
            say("وضع المعاينة: لا تُقرأ طلبات لأن Firebase غير مربوط في هذا البناء")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val report = reports.refreshOrders()
                _state.update { it.copy(report = report) }
                val read = report.sources.sumOf { it.read }
                say("قُرئ $read طلبًا برمز حملة" + if (report.failures.isEmpty()) "" else " (تعذّر مصدر: افتح التشخيص)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReportException) {
                say(e.message ?: "تعذّرت قراءة الطلبات")
            } catch (e: Exception) {
                say(CloudErrors.message(e))
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun addResult(campaignId: String, channel: Channel?, metric: ResultMetric, value: Long, date: String, note: String) {
        if (value <= 0L) return say("اكتب رقمًا أكبر من صفر")
        if (!CampaignRules.isDate(date)) return say("التاريخ غير صالح")
        viewModelScope.launch {
            reports.addResult(campaignId, channel, metric, value, date, note)
            say("سُجّلت النتيجة")
        }
    }

    fun deleteResult(id: String) {
        viewModelScope.launch {
            reports.deleteResult(id)
            say("حُذفت النتيجة")
        }
    }

    fun saveSources(value: ReportSources) {
        val names = listOf(value.siteCollection, value.siteField) +
            if (value.kadyEnabled) listOf(value.kadyCollection, value.kadyField) else emptyList()
        if (names.any { !ReportSources.isValidName(it) }) return say("اسم المجموعة أو الحقل فارغ أو يحوي مسافة أو شرطة مائلة")
        viewModelScope.launch {
            settings.setReportSources(value)
            say("حُفظت إعدادات المصادر. اضغط ⟳ لقراءة الطلبات")
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    private companion object {
        const val STALE_MS = 10L * 60L * 1000L
    }
}
