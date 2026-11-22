package com.cady.cadymarketing.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.calendar.OccasionRepository
import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.inventory.InventoryRepository
import com.cady.cadymarketing.domain.engine.Arrival
import com.cady.cadymarketing.domain.engine.CalendarCell
import com.cady.cadymarketing.domain.engine.CalendarEntries
import com.cady.cadymarketing.domain.engine.CalendarMath
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.DayEntries
import com.cady.cadymarketing.domain.engine.InventoryInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The month on show, the day picked in it, and what happens on every day of the grid. */
data class CalendarState(
    val loaded: Boolean = false,
    val year: Int,
    val month: Int,
    val today: String,
    val selected: String,
    val grid: List<CalendarCell>,
    val days: Map<String, DayEntries>,
    /** The deliveries the inventory snapshot expects, by day of the grid (empty without a snapshot). */
    val arrivals: Map<String, List<Arrival>> = emptyMap(),
) {
    val selectedEntries: DayEntries? get() = days[selected]

    val selectedArrivals: List<Arrival> get() = arrivals[selected].orEmpty()

    companion object {
        fun initial(today: String): CalendarState {
            val year = CalendarMath.yearOf(today) ?: 2026
            val month = CalendarMath.monthOf(today) ?: 1
            return CalendarState(false, year, month, today, today, CalendarMath.monthGrid(year, month), emptyMap())
        }
    }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    content: ContentRepository,
    campaigns: CampaignRepository,
    private val occasions: OccasionRepository,
    inventory: InventoryRepository,
) : ViewModel() {

    private val start = DateUtil.today()
    private val month = MutableStateFlow((CalendarMath.yearOf(start) ?: 2026) to (CalendarMath.monthOf(start) ?: 1))
    private val selected = MutableStateFlow(start)

    private val base: Flow<CalendarState> = combine(
        month,
        selected,
        content.observeAll(),
        campaigns.observeAll(),
        occasions.observeAll(),
    ) { shown, picked, posts, campaignList, own ->
        val today = DateUtil.today()
        val grid = CalendarMath.monthGrid(shown.first, shown.second)
        CalendarState(
            loaded = true,
            year = shown.first,
            month = shown.second,
            today = today,
            selected = picked,
            grid = grid,
            days = CalendarEntries.build(grid.first().date, grid.last().date, posts, campaignList, own, today),
        )
    }

    val state: StateFlow<CalendarState> = combine(base, inventory.observeData()) { calendar, stock ->
        calendar.copy(
            arrivals = InventoryInsights.arrivals(stock.snapshot, stock.links, stock.products, calendar.grid.first().date, calendar.grid.last().date),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState.initial(start))

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Moves the grid by whole months; the picked day becomes today (when it is in that month) or the first of the month. */
    fun showMonth(delta: Int) {
        val (year, monthNumber) = CalendarMath.addMonths(month.value.first, month.value.second, delta)
        month.value = year to monthNumber
        val today = DateUtil.today()
        selected.value = if (CalendarMath.yearOf(today) == year && CalendarMath.monthOf(today) == monthNumber) {
            today
        } else {
            CalendarMath.date(year, monthNumber, 1)
        }
    }

    fun goToday() {
        val today = DateUtil.today()
        month.value = (CalendarMath.yearOf(today) ?: month.value.first) to (CalendarMath.monthOf(today) ?: month.value.second)
        selected.value = today
    }

    /** Picks a day; a day of a neighbouring month (the grey ones) also brings that month on screen. */
    fun select(date: String) {
        selected.value = date
        val year = CalendarMath.yearOf(date) ?: return
        val monthNumber = CalendarMath.monthOf(date) ?: return
        if (year != month.value.first || monthNumber != month.value.second) month.value = year to monthNumber
    }

    fun addOccasion(title: String, date: String, yearly: Boolean) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            occasions.add(clean, date, yearly)
            _messages.tryEmit("أُضيفت المناسبة «$clean»")
        }
    }

    fun deleteOccasion(id: String) {
        viewModelScope.launch {
            occasions.delete(id)
            _messages.tryEmit("حُذفت المناسبة")
        }
    }
}
