package com.example.on_safe.data.fake

import com.example.on_safe.data.repository.AccidentHistoryRepository
import com.example.on_safe.ui.history.HistoryListItem
import com.example.on_safe.ui.history.HistoryType

// TODO: GET /accident/history API 연동 후 교체
// 스크롤 및 '하루 여러 건' 케이스 테스트용 더미 데이터.
// WARNING 타입은 어댑터에서 필터링되어 화면에 표시되지 않음(연동 검증용).
class FakeAccidentHistoryRepository : AccidentHistoryRepository {

    override fun getHistoryEntries(): MutableList<HistoryListItem.HistoryEntry> = mutableListOf(
        // 2026.07.21 — 하루 2건
        HistoryListItem.HistoryEntry("1",  HistoryType.FALL,    "23:12", "2026.07.21"),
        HistoryListItem.HistoryEntry("2",  HistoryType.FALL,    "18:47", "2026.07.21"),
        // 2026.07.20 — 1건 (+ 필터링되는 WARNING 1건)
        HistoryListItem.HistoryEntry("3",  HistoryType.FALL,    "14:32", "2026.07.20"),
        HistoryListItem.HistoryEntry("4",  HistoryType.WARNING, "09:17", "2026.07.20"),
        // 2026.07.18 — 하루 3건
        HistoryListItem.HistoryEntry("5",  HistoryType.FALL,    "21:05", "2026.07.18"),
        HistoryListItem.HistoryEntry("6",  HistoryType.FALL,    "15:33", "2026.07.18"),
        HistoryListItem.HistoryEntry("7",  HistoryType.FALL,    "08:20", "2026.07.18"),
        // 2026.07.15 — 1건
        HistoryListItem.HistoryEntry("8",  HistoryType.FALL,    "11:44", "2026.07.15"),
        // 2026.07.11 — 하루 2건 (+ 필터링되는 WARNING 1건)
        HistoryListItem.HistoryEntry("9",  HistoryType.FALL,    "19:50", "2026.07.11"),
        HistoryListItem.HistoryEntry("10", HistoryType.WARNING, "12:03", "2026.07.11"),
        HistoryListItem.HistoryEntry("11", HistoryType.FALL,    "07:30", "2026.07.11"),
        // 2026.07.05 — 1건
        HistoryListItem.HistoryEntry("12", HistoryType.FALL,    "16:10", "2026.07.05"),
    )
}
