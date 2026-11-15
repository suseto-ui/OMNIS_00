package com.example.ui.user

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class UserProgressViewModel : ViewModel() {
    val badges: StateFlow<List<UserBadge>> = BadgeTriggerManager.currentBadges
    val dialecticalReputationScore: StateFlow<Int> = BadgeTriggerManager.reputationScore

    fun earnBadge(badgeId: String) {
        BadgeTriggerManager.triggerBadge(badgeId, triggerVector = "MANUAL_OVERRIDE_EVENT")
    }
}
