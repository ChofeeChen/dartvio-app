package com.dartvio.app.data

import com.dartvio.app.data.local.dao.DartHitDao
import com.dartvio.app.data.local.entity.DartHitEntity

/**
 * 逐镖落点的唯一读写入口。
 *
 * 写入时机与 `match_records` 一致（对局结束时才落库）：
 * `matchId` 是在那一刻才生成的，点位必须等它出现才能挂上去，
 * 因此对局中途退出的场次**不产生**点位行 —— 与整份比赛记录的行为保持一致。
 */
class DartHitRepository(private val dao: DartHitDao) {

    suspend fun saveHits(hits: List<DartHitEntity>) = dao.insertAll(hits)

    suspend fun listForProfile(profileId: String): List<DartHitEntity> = dao.listByProfile(profileId)

    suspend fun count(): Int = dao.count()

    suspend fun clearAll() = dao.clearAll()
}
