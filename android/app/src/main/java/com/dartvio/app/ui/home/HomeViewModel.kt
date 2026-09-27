package com.dartvio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.dao.MatchWithPlayers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 首页数据源：只取最近几场对局用于摘要展示。 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MatchRepository = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.matchRepository
        else -> MatchRepository(DartVioDatabase.get(ctx).matchRecordDao())
    }

    /** 最近 3 场对局（按结束时间倒序）。 */
    val recentMatches: StateFlow<List<MatchWithPlayers>> = repository.observeMatches()
        .map { it.take(3) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
